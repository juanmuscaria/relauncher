package com.juanmuscaria.relauncher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Core entry point for relaunching the JVM with extra arguments.
 * <p>
 * Call this early, before significant game state is initialized, and
 * under normal circumstances the current process will be replaced or
 * halted, so control never returns to the caller.
 */
public final class Relauncher {
    public static final String RELAUNCH_PROPERTY = "relauncher.didRelaunch";
    private static final String DEPTH_PROPERTY = "relauncher.depth";
    private static final int MAX_RELAUNCH_DEPTH = 10;

    private Relauncher() {
    }

    /**
     * Checks whether we're running inside a relaunched JVM.
     */
    public static boolean isRelaunched() {
        return Boolean.getBoolean(RELAUNCH_PROPERTY);
    }

    /**
     * How many times this process has been relaunched: 0 for the original,
     * 1 after the first relaunch, and so on. Used internally to detect relaunch loops.
     */
    public static int getDepth() {
        return Integer.getInteger(DEPTH_PROPERTY, 0);
    }

    /**
     * Collects extra JVM arguments from the given providers (sorted by priority)
     * and relaunches the JVM.
     * <p>
     * On success this method <b>never returns</b> - the process is replaced or
     * halted. If you do get a {@link RelaunchResult} back, something went wrong
     * (or the relaunch was intentionally skipped), and the result tells you why.
     *
     * @param providers command line providers supplying extra JVM arguments
     * @return why the relaunch didn't happen - only returned on failure/skip
     */
    public static RelaunchResult relaunch(Collection<CommandLineProvider> providers) {
        var extraArgs = providers.stream()
            .sorted(Comparator.comparingInt(CommandLineProvider::priority))
            .flatMap(p -> p.extraJvmArguments().stream())
            .collect(Collectors.toCollection(ArrayList::new));

        extraArgs.add("-D" + RELAUNCH_PROPERTY + "=true");
        extraArgs.add("-D" + DEPTH_PROPERTY + "=" + (getDepth() + 1));

        return relaunch(extraArgs);
    }

    /**
     * Relaunches the JVM with the given arguments prepended to the original command line.
     * <p>
     * Same semantics as {@link #relaunch(Collection)}: on success this <b>never returns</b>.
     * If it does return, the {@link RelaunchResult} explains what happened.
     *
     * @param extraJvmArgs arguments to inject before the original arguments
     * @return why the relaunch didn't happen - only returned on failure/skip
     */
    public static RelaunchResult relaunch(List<String> extraJvmArgs) {
        int depth = getDepth();
        if (depth >= MAX_RELAUNCH_DEPTH) {
            return RelaunchResult.failed("Relaunch loop detected! Depth " + depth +
                " exceeds maximum of " + MAX_RELAUNCH_DEPTH +
                ". This usually means a mod is unconditionally requesting a relaunch" +
                " on every startup. Check your relauncher config and installed mods.");
        }

        var executable = CommandLineExtractor.extractExecutable();
        var originalArgs = CommandLineExtractor.extractArguments();

        boolean debug = Boolean.getBoolean("relauncher.debug");
        if (debug) {
            System.out.println("[Relauncher Debug] Executable: " + executable);
            var phArgs = CommandLineExtractor.invokeProcessHandle("arguments");
            String processHandleSource = (phArgs != null && phArgs.isPresent()) ? "ProcessHandle" : "fallback";
            System.out.println("[Relauncher Debug] ProcessHandle source: " + processHandleSource);
            System.out.println("[Relauncher Debug] Original args (" + originalArgs.size() + "):");
            for (int i = 0; i < originalArgs.size(); i++) {
                System.out.println("[Relauncher Debug]   [" + i + "] " + originalArgs.get(i));
            }
            System.out.println("[Relauncher Debug] Extra args (" + extraJvmArgs.size() + "):");
            for (int i = 0; i < extraJvmArgs.size(); i++) {
                System.out.println("[Relauncher Debug]   [" + i + "] " + extraJvmArgs.get(i));
            }
        }

        // Filter out existing relaunch/depth properties to avoid stacking
        var filteredOriginal = new ArrayList<String>();
        for (String arg : originalArgs) {
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
            for (int i = 0; i < command.size(); i++) {
                System.out.println("[Relauncher Debug]   [" + i + "] " + command.get(i));
            }
        }

        // Try Unix exec(). Replaces the current process entirely.
        if (NativeRelaunch.isExecAvailable()) {
            System.out.println("[Relauncher] Attempting Unix exec...");
            NativeRelaunch.exec(command);
            // If we get here, exec() failed.
        }

        // Try Windows DLL. Arms DllMain(DLL_PROCESS_DETACH) to create
        // the child process after the JVM has fully shut down.
        String workingDir = System.getProperty("user.dir", ".");
        if (NativeRelaunch.isWinDllAvailable() && NativeRelaunch.arm(command, workingDir)) {
            System.out.println("[Relauncher] Exec emulation armed, halting parent JVM. " +
                "Child will be created in DLL_PROCESS_DETACH.");
            Runtime.getRuntime().exit(0);
        }

        // Fallback. Start child via ProcessBuilder, block on waitFor().
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

    /**
     * Patches the command line for launchers that use stdin-based protocols.
     * <p>
     * Some third-party launchers feed auth tokens and game arguments to their
     * entry point via stdin, which is gone by the time we relaunch. These
     * workarounds detect known launchers and swap their entry point for the
     * real main class + game arguments, sidestepping stdin entirely.
     */
    private static void applyLauncherWorkarounds(List<String> command) {
        applyPrismLauncherWorkaround(command);
        applyModrinthWorkaround(command);
    }

    /**
     * PrismLauncher reads stdin for auth and game args via {@code org.prismlauncher.EntryPoint},
     * but it also stashes the resolved values in system properties - so we can grab
     * the real main class and game arguments from there instead.
     */
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
            for (String arg : gameArgs.split("\u001F")) {
                if (!arg.isEmpty()) {
                    command.add(arg);
                }
            }
        }

        System.out.println("[Relauncher] PrismLauncher workaround: replaced EntryPoint with " + realMainClass);
    }

    /**
     * Modrinth's theseus launcher wraps the real main class behind
     * {@code com.modrinth.theseus.MinecraftLaunch} and attaches a javaagent for quickplay.
     * The IPC connection is dead after a relaunch, so we swap in the
     * real main class and strip the theseus-specific arguments.
     */
    //TODO: figure out what the RPC system does and if we end up dropping system properties that way.
    private static void applyModrinthWorkaround(List<String> command) {
        int modrinthIdx = command.indexOf("com.modrinth.theseus.MinecraftLaunch");
        if (modrinthIdx < 0) return;

        // The real entry point is the next argument after MinecraftLaunch
        if (modrinthIdx + 1 >= command.size()) {
            System.out.println("[Relauncher] WARNING: Modrinth detected but no real main class argument found.");
            return;
        }

        String realMainClass = command.get(modrinthIdx + 1);
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

    /**
     * Checks for launchers we know we can't support. Returns a human-readable
     * reason if one is detected, or {@code null} if everything looks fine.
     */
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
}
