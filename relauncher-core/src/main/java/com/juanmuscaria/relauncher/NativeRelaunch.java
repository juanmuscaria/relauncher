package com.juanmuscaria.relauncher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * JNI bridge to the Rust native library that handles the platform-specific
 * parts of relaunching.
 * <p>
 * On POSIX systems, {@link #exec(List)} calls {@code execvp()} to replace the
 * process in-place. On Windows, {@link #arm(List, String)} sets up
 * {@code DllMain(DLL_PROCESS_DETACH)} to spawn a child after the JVM exits.
 * <p>
 * Also exposes {@link #getOriginalCommandLine()}, which reads the command line
 * straight from the OS ({@code GetCommandLineW()} on Windows,
 * {@code /proc/self/cmdline} on Linux) while bypassing any mutations Java's
 * bootstrap code may have made to system properties like {@code java.class.path}.
 * <p>
 * Methods that don't apply to the current platform are safe no-ops.
 */
public final class NativeRelaunch {
    private static final boolean available;
    private static final boolean isWindows;

    static {
        isWindows = "windows".equals(NativeLibs.osName());
        available = NativeLibs.ensureLoaded();
    }

    private NativeRelaunch() {
    }

    /**
     * Whether the native library loaded successfully.
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Whether we can use POSIX {@code execvp()} (i.e. native lib loaded and not on Windows).
     */
    public static boolean isExecAvailable() {
        return available && !isWindows;
    }

    /**
     * Whether we can use the Windows DLL detach strategy.
     */
    public static boolean isWinDllAvailable() {
        return available && isWindows;
    }


    /**
     * Replaces the current process via {@code execvp()}.
     * If it works, this <b>never returns</b>. If it fails (or we're not on
     * Linux/macOS), returns normally and you should try another strategy.
     *
     * @param command the full command line (executable + arguments)
     */
    public static void exec(List<String> command) {
        if (!isExecAvailable()) return;
        nativeExec(command.toArray(new String[0]));
    }

    /**
     * Prepares the Windows DLL to spawn a child process when the JVM exits.
     * <p>
     * Once armed, the next {@link Runtime#exit(int)} causes the DLL's
     * {@code DLL_PROCESS_DETACH} handler to create the child and wait for it.
     * stdin/stdout/stderr are duplicated beforehand so they survive the JVM
     * shutting down.
     *
     * @param command    the full command line (executable + arguments)
     * @param workingDir working directory for the child process
     * @return true if armed successfully, false if not on Windows or native lib missing
     */
    public static boolean arm(List<String> command, String workingDir) {
        if (!isWinDllAvailable()) return false;

        var commandLine = buildWindowsCommandLine(command);
        if (Boolean.getBoolean("relauncher.debug")) {
            System.out.println("[Relauncher Debug] Windows command line length: " + commandLine.length() + " chars");
            System.out.println("[Relauncher Debug] Working dir: " + workingDir);
            if (commandLine.length() <= 2000) {
                System.out.println("[Relauncher Debug] Command line: " + commandLine);
            } else {
                System.out.println("[Relauncher Debug] Command line (first 2000 chars): " +
                    commandLine.substring(0, 2000) + "...");
                System.out.println("[Relauncher Debug] Command line (last 500 chars): ..." +
                    commandLine.substring(commandLine.length() - 500));
            }
        }
        return nativeSetCommand(commandLine, workingDir);
    }

    /**
     * Grabs the raw command line straight from the OS - {@code GetCommandLineW()}
     * on Windows, {@code /proc/self/cmdline} on Linux/macOS.
     * <p>
     * This is the real thing, untouched by whatever Java's bootstrap may have
     * done to system properties along the way.
     *
     * @return the raw command line string, or null if we can't get it
     */
    public static String getOriginalCommandLine() {
        if (isWindows && available) {
            return nativeGetCommandLine();
        }

        // Linux/macOS: read /proc/self/cmdline
        return readProcCmdline();
    }

    private static String readProcCmdline() {
        try {
            var path = Paths.get("/proc/self/cmdline");
            if (!Files.exists(path)) return null;

            byte[] bytes = Files.readAllBytes(path);
            if (bytes.length == 0) return null;

            // /proc/self/cmdline is null-byte separated, UTF-8 encoded.
            // Strip trailing null byte, preserve internal nulls as argument separators.
            int len = bytes.length;
            if (bytes[len - 1] == 0) {
                len--;
            }
            return new String(bytes, 0, len, UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Splits a raw command line (from {@link #getOriginalCommandLine()}) into
     * individual arguments. Uses null-byte splitting for POSIX and
     * {@code CommandLineToArgvW} rules for Windows.
     *
     * @param cmdLine the raw command line from {@link #getOriginalCommandLine()}
     * @return the parsed argument list, or an empty list if parsing fails
     */
    public static List<String> parseCommandLine(String cmdLine) {
        if (cmdLine == null || cmdLine.isEmpty()) return Collections.emptyList();

        if (cmdLine.indexOf('\0') >= 0) {
            // POSIX: null-byte separated
            return Collections.unmodifiableList(Arrays.asList(cmdLine.split("\0")));
        }

        // Windows: CommandLineToArgvW rules
        return CommandLineExtractor.parseWindowsCommandLine(cmdLine);
    }

    // https://learn.microsoft.com/en-us/cpp/c-language/parsing-c-command-line-arguments
    static String buildWindowsCommandLine(List<String> args) {
        var sb = new StringBuilder();
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) sb.append(' ');
            appendQuotedArg(sb, args.get(i));
        }
        return sb.toString();
    }

    private static void appendQuotedArg(StringBuilder sb, String arg) {
        if (arg.isEmpty()) {
            sb.append("\"\"");
            return;
        }

        boolean needsQuoting = false;
        for (int i = 0; i < arg.length(); i++) {
            char c = arg.charAt(i);
            if (c == ' ' || c == '\t' || c == '"') {
                needsQuoting = true;
                break;
            }
        }

        if (!needsQuoting) {
            sb.append(arg);
            return;
        }

        sb.append('"');
        int backslashes = 0;
        for (int i = 0; i < arg.length(); i++) {
            char c = arg.charAt(i);
            if (c == '\\') {
                backslashes++;
            } else if (c == '"') {
                CommandLineExtractor.appendBackslashes(sb, backslashes * 2);
                backslashes = 0;
                sb.append("\\\"");
            } else {
                CommandLineExtractor.appendBackslashes(sb, backslashes);
                backslashes = 0;
                sb.append(c);
            }
        }
        CommandLineExtractor.appendBackslashes(sb, backslashes * 2);
        sb.append('"');
    }

    private static native void nativeExec(String[] args);

    private static native boolean nativeSetCommand(String commandLine, String workingDir);

    private static native String nativeGetCommandLine();
}
