// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher;

import com.juanmuscaria.relauncher.jvm.*;
import com.juanmuscaria.relauncher.launch.Platform;
import com.juanmuscaria.relauncher.launch.Side;
import com.juanmuscaria.relauncher.logger.LoggerAdapter;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Relaunches the JVM with extra args. Call early, before any real game state.
 * On success the current process is replaced or halted, control never returns.
 */
public final class Relauncher {
    public static final String RELAUNCH_PROPERTY = "relauncher.didRelaunch";
    private static final String DEPTH_PROPERTY = "relauncher.depth";
    private static final int MAX_RELAUNCH_DEPTH = 10;

    private Relauncher() {
    }

    /** True if running inside a relaunched JVM */
    public static boolean isRelaunched() {
        return Boolean.getBoolean(RELAUNCH_PROPERTY);
    }

    /** 0 for the original process, 1 after the first relaunch, and so on */
    public static int getDepth() {
        return Integer.getInteger(DEPTH_PROPERTY, 0);
    }

    /**
     * Sorts providers by priority, collects their args and relaunches.
     * Never returns on success. A returned {@link RelaunchResult} means
     * the relaunch failed or was skipped.
     */
    public static RelaunchResult relaunch(Collection<CommandLineProvider> providers) {
        return relaunch(providers, Collections.<JavaRuntimeProvider>emptyList());
    }

    /**
     * Same as {@link #relaunch(Collection)} but also resolves a java install
     * matching the combined java requirements. Returns {@link RelaunchResult#skipped(String)}
     * if there are no extra args and the current jvm already satisfies the
     * combined java requirement.
     */
    public static RelaunchResult relaunch(
        Collection<CommandLineProvider> argProviders,
        Collection<JavaRuntimeProvider> javaProviders) {

        var extraArgs = argProviders.stream()
            .sorted(Comparator.comparingInt(CommandLineProvider::priority))
            .flatMap(p -> p.extraJvmArguments().stream())
            .collect(Collectors.toCollection(ArrayList::new));

        var requirements = javaProviders.stream()
            .sorted(Comparator.comparingInt(JavaRuntimeProvider::priority))
            .flatMap(p -> p.javaRequirements().stream())
            .collect(Collectors.toCollection(ArrayList::new));

        String overrideExecutable = null;
        if (!requirements.isEmpty()) {
            JavaRequirement combined;
            try {
                combined = JavaRequirement.combine(requirements);
            } catch (JavaRequirement.UnsatisfiableException e) {
                failFatal("Conflicting Java runtime requirements", e.getMessage());
                return RelaunchResult.failed(e.getMessage()); // failFatal exits; satisfy compiler
            }

            overrideExecutable = resolveJavaExecutable(combined);
        }

        if (extraArgs.isEmpty() && overrideExecutable == null) {
            return RelaunchResult.skipped("nothing to change");
        }

        extraArgs.add("-D" + RELAUNCH_PROPERTY + "=true");
        extraArgs.add("-D" + DEPTH_PROPERTY + "=" + (getDepth() + 1));

        return relaunchInternal(extraArgs, overrideExecutable);
    }

    // returns the executable to use, null when the current jvm satisfies it,
    // doesn't return on unsatisfiable
    private static String resolveJavaExecutable(JavaRequirement combined) {
        var currentArch = System.getProperty("os.arch");

        var current = JavaInstallationDetector.probeHome(JavaInstallationDetector.currentJavaHome());
        if (current != null && combined.matches(current, currentArch)) {
            return null;
        }

        var matches = JavaInstallationDetector.detect().stream()
            .filter(i -> combined.matches(i, currentArch))
            .sorted((a, b) -> {
                var byVersion = b.version().compareTo(a.version()); // descending
                if (byVersion != 0) return byVersion;
                return a.home().toString().length() - b.home().toString().length();
            })
            .collect(Collectors.toList());

        if (matches.isEmpty()) {
            // Fallback to downloader SPI if present.
            var downloadDir = resolveDownloadDir();
            var configAutoDownload = readConfigAutoDownload();
            var fallback = tryDownloadFallback(combined, currentArch, configAutoDownload, downloadDir);
            if (fallback.status() == DownloadOrchestrator.Result.Status.SUCCESS) {
                return fallback.installation().executable().toString();
            }

            failFatal("No matching Java installation",
                "Required: " + combined + ". None of the installations on this system satisfy it, "
                    + downloadFailureDetail(fallback.status())
                    + " Install a matching Java runtime and try again.");
            return null; // unreachable
        }

        return matches.get(0).executable().toString();
    }

    private static String downloadFailureDetail(DownloadOrchestrator.Result.Status status) {
        switch (status) {
            case NO_SPI:
                return "and no Java downloader is available on the classpath.";
            case CONSENT_DENIED:
                return "and download consent was denied (set java.autoDownload = true in the "
                    + "Relauncher config or pass -Drelauncher.java.autoDownload=true to allow it).";
            case NO_MATCHING_PACKAGE:
                return "and no downloader has a package matching this requirement (e.g. GraalVM "
                    + "Community only ships as JDK; set java.imageType = jdk if you need it).";
            default:
                return "and the download fallback could not satisfy it.";
        }
    }

    // throws IOException via failFatal on recoverable download failures
    private static DownloadOrchestrator.Result tryDownloadFallback(JavaRequirement combined, String currentArch,
                                                                   boolean configAutoDownload, Path downloadDir) {
        var downloaders = loadDownloaderSpi();
        if (downloaders.isEmpty()) return DownloadOrchestrator.Result.noSpi();

        var clientWithTinyFd = detectClientWithTinyFd();

        try {
            return DownloadOrchestrator.orchestrate(
                combined, currentArch,
                configAutoDownload, clientWithTinyFd,
                () -> promptTinyFdYesNo(combined),
                downloadDir,
                downloaders);
        } catch (IOException e) {
            failFatal("Java download failed", e.getMessage() == null ? e.toString() : e.getMessage());
            return DownloadOrchestrator.Result.noMatchingPackage(); // unreachable
        }
    }

    // Reuses RelauncherConfig.discoverSpi so downloaders dropped into the mods
    // folder are picked up the same way CommandLineProvider/JavaRuntimeProvider
    // are. Platform may not be registered when called from the programmatic API,
    // in which case discoverSpi falls back to classloader-only scanning.
    private static List<JavaRuntimeDownloader> loadDownloaderSpi() {
        Platform platform = null;
        LoggerAdapter logger = null;
        try {
            platform = Platform.current();
            logger = platform.logger();
        } catch (Throwable ignored) {
            // No platform registered; classloader-only path will run.
        }
        return RelauncherConfig.discoverSpi(JavaRuntimeDownloader.class, platform, logger);
    }

    private static boolean detectClientWithTinyFd() {
        try {
            if (Platform.current().side() != Side.CLIENT) return false;
        } catch (Throwable ignored) {
            return false;
        }
        try {
            Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean promptTinyFdYesNo(JavaRequirement req) {
        var title = "Relauncher";
        var msg = "Relauncher needs to download Java " + req.acceptableMajors()
            + " to launch this game. Downloading may take 30-90 seconds; the game will launch when complete.\n\nProceed?";
        try {
            var tfd = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
            var result = tfd.getMethod("tinyfd_messageBox",
                    CharSequence.class, CharSequence.class,
                    CharSequence.class, CharSequence.class, boolean.class)
                .invoke(null, title, msg, "yesno", "question", true);
            if (Boolean.getBoolean("relauncher.debug")) {
                System.out.println("[Relauncher Debug] TinyFD consent result: " + result
                    + " (type " + (result == null ? "null" : result.getClass().getName()) + ")");
            }
            // LWJGL 3.3.0 returned int (1=yes, 0=no); 3.3.3+ returns boolean for
            // the boolean-defaultButton overload. Accept both.
            if (result instanceof Boolean) return (Boolean) result;
            return result instanceof Number && ((Number) result).intValue() == 1;
        } catch (Throwable t) {
            System.err.println("[Relauncher] TinyFD consent prompt failed: " + t);
            return false;
        }
    }

    private static Path resolveDownloadDir() {
        try {
            var cfg = RelauncherConfig.load(Platform.current().configDirectory());
            if (cfg.getJavaDownloadDir() != null) return cfg.getJavaDownloadDir();
        } catch (Throwable ignored) {
            // Platform not registered or config unreadable; fall through to default.
        }
        return DownloadDirResolver.resolve();
    }

    private static boolean readConfigAutoDownload() {
        try {
            var cfg = RelauncherConfig.load(Platform.current().configDirectory());
            return cfg.isJavaAutoDownload();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void failFatal(String title, String message) {
        LoggerAdapter logger = null;
        try {
            logger = Platform.current().logger();
        } catch (Throwable ignored) {
            // No platform registered; dialog still works
        }
        RelaunchErrorDialog.showAndExit(title, message, logger);
    }

    /**
     * Relaunches the JVM with {@code extraJvmArgs} prepended to the original
     * command line. Same semantics as {@link #relaunch(Collection)}.
     */
    public static RelaunchResult relaunch(List<String> extraJvmArgs) {
        return relaunchInternal(extraJvmArgs, null);
    }

    private static RelaunchResult relaunchInternal(List<String> extraJvmArgs, String overrideExecutable) {
        var depth = getDepth();
        if (depth >= MAX_RELAUNCH_DEPTH) {
            return RelaunchResult.failed("Relaunch loop detected, bailing out at depth " + depth +
                ". Some mod is probably asking for a relaunch on every startup.");
        }

        var executable = overrideExecutable != null
            ? overrideExecutable
            : CommandLineExtractor.extractExecutable();
        var originalArgs = CommandLineExtractor.extractArguments();

        var debug = Boolean.getBoolean("relauncher.debug");
        if (debug) {
            System.out.println("[Relauncher Debug] Executable: " + executable);
            var phArgs = CommandLineExtractor.invokeProcessHandle("arguments");
            var processHandleSource = (phArgs != null && phArgs.isPresent()) ? "ProcessHandle" : "fallback";
            System.out.println("[Relauncher Debug] ProcessHandle source: " + processHandleSource);
            System.out.println("[Relauncher Debug] Original args (" + originalArgs.size() + "):");
            for (var i = 0; i < originalArgs.size(); i++) {
                System.out.println("[Relauncher Debug]   [" + i + "] " + originalArgs.get(i));
            }
            System.out.println("[Relauncher Debug] Extra args (" + extraJvmArgs.size() + "):");
            for (var i = 0; i < extraJvmArgs.size(); i++) {
                System.out.println("[Relauncher Debug]   [" + i + "] " + extraJvmArgs.get(i));
            }
        }

        // Filter out existing relaunch/depth properties to avoid stacking
        var filteredOriginal = new ArrayList<String>();
        for (var arg : originalArgs) {
            if (arg.startsWith("-D" + RELAUNCH_PROPERTY + "=") ||
                arg.startsWith("-D" + DEPTH_PROPERTY + "=")) {
                continue;
            }
            filteredOriginal.add(arg);
        }

        var command = new ArrayList<String>(1 + extraJvmArgs.size() + filteredOriginal.size());
        command.add(executable);
        command.addAll(extraJvmArgs);
        command.addAll(filteredOriginal);

        applyLauncherWorkarounds(command);

        var unsupportedReason = getUnsupportedLauncherReason(command);
        if (unsupportedReason != null) {
            return RelaunchResult.skipped(unsupportedReason);
        }

        System.out.println("[Relauncher] Relaunching JVM with " + extraJvmArgs.size() + " extra argument(s)");

        if (debug) {
            System.out.println("[Relauncher Debug] Final command (" + command.size() + " args):");
            for (var i = 0; i < command.size(); i++) {
                System.out.println("[Relauncher Debug]   [" + i + "] " + command.get(i));
            }
        }

        // Instrumentation only, no-op when relauncher.test.markerDir is unset
        writeInstrumentationMarker("pre-dispatch", executable, extraJvmArgs, command);

        var testForceFallback = TestHooks.forceFallback();
        var testDisableExec = TestHooks.disableNativeExec();

        // Try Unix exec(). Replaces the current process entirely.
        if (!testForceFallback && !testDisableExec && NativeRelaunch.isExecAvailable()) {
            System.out.println("[Relauncher] Attempting Unix exec...");
            writeInstrumentationMarker("posix-exec", executable, extraJvmArgs, command);
            NativeRelaunch.exec(command);
            // If we get here, exec() failed.
        }

        // Try Windows DLL. Arms DllMain(DLL_PROCESS_DETACH) to create
        // the child process after the JVM has fully shut down.
        var workingDir = System.getProperty("user.dir", ".");
        if (!testForceFallback && NativeRelaunch.isWinDllAvailable() && NativeRelaunch.arm(command, workingDir)) {
            System.out.println("[Relauncher] Exec emulation armed, halting parent JVM. " +
                "Child will be created in DLL_PROCESS_DETACH.");
            writeInstrumentationMarker("windows-dll", executable, extraJvmArgs, command);
            Runtime.getRuntime().exit(0);
        }

        // Fallback. Start child via ProcessBuilder, block on waitFor().
        writeInstrumentationMarker("java-fallback", executable, extraJvmArgs, command);
        try {
            var process = new ProcessBuilder(command)
                .inheritIO()
                .start();

            int exitCode;
            try {
                exitCode = process.waitFor();
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
                exitCode = 1;
            }
            // halt() bypasses shutdown hooks
            Runtime.getRuntime().halt(exitCode);
        } catch (IOException e) {
            return RelaunchResult.failed("Failed to start child process", e);
        }

        return RelaunchResult.failed("All relaunch strategies exhausted");
    }

    // Some launchers feed auth tokens + game args to their entry point via
    // stdin, which is gone after a relaunch. Detect known launchers and swap
    // their entry point for the real main class + game args.
    private static void applyLauncherWorkarounds(List<String> command) {
        applyPrismLauncherWorkaround(command);
        applyModrinthWorkaround(command);
    }

    // PrismLauncher reads stdin for auth and game args via org.prismlauncher.EntryPoint,
    // but also stashes the resolved values in system properties, so we grab
    // the real main class and game args from there
    private static void applyPrismLauncherWorkaround(List<String> command) {
        var prismIdx = command.indexOf("org.prismlauncher.EntryPoint");
        if (prismIdx < 0) return;

        var realMainClass = System.getProperty("org.prismlauncher.launch.mainclass");
        var gameArgs = System.getProperty("org.prismlauncher.launch.gameargs");

        if (realMainClass == null || realMainClass.trim().isEmpty()) {
            System.out.println("[Relauncher] WARNING: PrismLauncher detected but " +
                "org.prismlauncher.launch.mainclass not set. Bailing out");
            return;
        }
        command.set(prismIdx, realMainClass);
        while (command.size() > prismIdx + 1) {
            command.remove(command.size() - 1);
        }

        if (gameArgs != null && !gameArgs.isEmpty()) {
            for (var arg : gameArgs.split("\u001F")) {
                if (!arg.isEmpty()) {
                    command.add(arg);
                }
            }
        }

        System.out.println("[Relauncher] PrismLauncher workaround: replaced EntryPoint with " + realMainClass);
    }

    // Modrinth's theseus wraps the real main class behind com.modrinth.theseus.MinecraftLaunch
    // and attaches a javaagent for quickplay. The IPC connection is dead after
    // a relaunch, swap in the real main class and strip the theseus args.
    //TODO: figure out what the RPC system does and if we end up dropping system properties that way
    private static void applyModrinthWorkaround(List<String> command) {
        var modrinthIdx = command.indexOf("com.modrinth.theseus.MinecraftLaunch");
        if (modrinthIdx < 0) return;

        // The real entry point is the next argument after MinecraftLaunch
        if (modrinthIdx + 1 >= command.size()) {
            System.out.println("[Relauncher] WARNING: Modrinth detected but no real main class argument found.");
            return;
        }

        var realMainClass = command.get(modrinthIdx + 1);
        command.set(modrinthIdx, realMainClass);
        command.remove(modrinthIdx + 1);

        // Remove the theseus javaagent and IPC properties, it breaks on relaunch
        var it = command.iterator();
        while (it.hasNext()) {
            var arg = it.next();
            if (arg.startsWith("-javaagent:") && arg.contains("theseus")) {
                it.remove();
            } else if (arg.startsWith("-Dmodrinth.internal.")) {
                it.remove();
            }
        }

        System.out.println("[Relauncher] Modrinth workaround: replaced theseus wrapper with " + realMainClass);
    }

    // returns a reason string for a known-unsupported launcher, null otherwise
    private static String getUnsupportedLauncherReason(List<String> command) {
        // MultiMC: uses org.multimc.EntryPoint which reads stdin for main class,
        // game args, launching, etc. Unlike PrismLauncher, it does not store the
        // resolved values in system properties, and they live in private fields of a
        // local OneSixLauncher object. No way to reconstruct the command.
        // TODO: Maybe JNI can solve this by combing through the heap....
        if (command.contains("org.multimc.EntryPoint")) {
            return "MultiMC detected. Relaunch is not supported under MultiMC because " +
                "it uses a stdin protocol that cannot be replayed.";
        }
        return null;
    }

    private static void writeInstrumentationMarker(String strategy, String executable,
                                                   List<String> extraJvmArgs,
                                                   List<String> finalCommand) {
        var dir = TestHooks.markerDir();
        if (dir == null) return;
        try {
            var base = Paths.get(dir, "core-instrumentation");
            Files.createDirectories(base);
            var pid = pidBestEffort();
            var file = base.resolve("relaunch-" + pid + "-depth" + getDepth() + ".json");

            var sb = new StringBuilder(512);
            sb.append("{\n");
            sb.append("  \"strategy\": \"").append(jsonEscape(strategy)).append("\",\n");
            sb.append("  \"executable\": \"").append(jsonEscape(executable)).append("\",\n");
            sb.append("  \"depth\": ").append(getDepth()).append(",\n");
            sb.append("  \"pid\": ").append(pid).append(",\n");
            sb.append("  \"extraArgs\": ").append(jsonArray(extraJvmArgs)).append(",\n");
            sb.append("  \"finalCommand\": ").append(jsonArray(finalCommand)).append("\n");
            sb.append("}\n");
            Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
            // Instrumentation failing must never break a real relaunch.
        }
    }

    private static long pidBestEffort() {
        try {
            return Long.parseLong(ManagementFactory.getRuntimeMXBean()
                .getName().split("@")[0]);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static String jsonEscape(String s) {
        var sb = new StringBuilder(s.length());
        for (var i = 0; i < s.length(); i++) {
            var c = s.charAt(i);
            switch (c) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String jsonArray(List<String> list) {
        var sb = new StringBuilder();
        sb.append('[');
        for (var i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append('"').append(jsonEscape(list.get(i))).append('"');
        }
        sb.append(']');
        return sb.toString();
    }
}
