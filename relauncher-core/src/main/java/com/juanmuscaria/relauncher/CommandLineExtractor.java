package com.juanmuscaria.relauncher;

import java.lang.management.ManagementFactory;
import java.nio.file.Paths;
import java.util.*;


/**
 * Figures out what command line was used to start this JVM so we can
 * reproduce it for the relaunch.
 * <p>
 * Tries, in order: {@code ProcessHandle} (Java 9+), the OS-level command
 * line via {@link NativeRelaunch#getOriginalCommandLine()}, and finally a
 * best-effort reconstruction from system properties (which can be inaccurate
 * if the launcher or ForgeWrapper mutated things like {@code java.class.path}).
 */
// We are aiming to stay compatible with java 8 still
@SuppressWarnings({"Java9CollectionFactory", "SizeReplaceableByIsEmpty", "StringRepeatCanBeUsed"})
public final class CommandLineExtractor {

    public static final boolean DEBUG = Boolean.getBoolean("relauncher.debug");

    private CommandLineExtractor() {
    }

    /**
     * Finds the path to the {@code java} binary that started this JVM.
     */
    public static String extractExecutable() {
        // ProcessHandle requires Java 9+ so we use reflection to stay Java 8 compatible
        @SuppressWarnings("unchecked")
        var result = (Optional<String>) invokeProcessHandle("command");
        if (result != null && result.isPresent()) {
            return result.get();
        }
        return findJavaExecutable();
    }

    /**
     * Recovers the full argument list (JVM flags + application args) in their
     * original order, with correct argument boundaries.
     */
    public static List<String> extractArguments() {
        // Round 1, try ProcessHandle.arguments()
        @SuppressWarnings("unchecked")
        var processHandleArgs = (Optional<String[]>) invokeProcessHandle("arguments");
        if (processHandleArgs != null && processHandleArgs.isPresent()) {
            return Collections.unmodifiableList(Arrays.asList(processHandleArgs.get()));
        }

        // Round 2, OS-level command line (GetCommandLineW / /proc/self/cmdline)
        var nativeCmdLine = tryNativeCommandLine();
        if (nativeCmdLine != null) {
            return nativeCmdLine;
        }

        // We are out of luck here, try our best reconstruct from system properties (may be inaccurate)
        return reconstructArguments();
    }

    /**
     * Attempts to pull arguments from the OS-level command line.
     * Returns everything after the executable, or null if we couldn't get it.
     */
    private static List<String> tryNativeCommandLine() {
        var rawCmdLine = NativeRelaunch.getOriginalCommandLine();
        if (rawCmdLine == null || rawCmdLine.trim().isEmpty()) {
            return null;
        }

        var allArgs = NativeRelaunch.parseCommandLine(rawCmdLine);
        if (allArgs.size() <= 1) {
            return null;
        }

        // Skip argv[0] (the executable) as caller adds it separately via extractExecutable()
        var args = allArgs.subList(1, allArgs.size());

        if (DEBUG) {
            System.out.println("[Relauncher Debug] Native command line used");
            System.out.println("[Relauncher Debug]   Raw length: " + rawCmdLine.length());
            System.out.println("[Relauncher Debug]   Parsed " + allArgs.size() + " args (skipping argv[0])");
        }

        return Collections.unmodifiableList(new ArrayList<>(args));
    }

    /**
     * Splits a Windows command line string into arguments following the
     * {@code CommandLineToArgvW} quoting/escaping rules.
     *
     * @see <a href="https://learn.microsoft.com/en-us/cpp/c-language/parsing-c-command-line-arguments">
     * Parsing C Command-Line Arguments</a>
     */
    static List<String> parseWindowsCommandLine(String cmdLine) {
        var args = new ArrayList<String>();
        var current = new StringBuilder();
        boolean inQuotes = false;
        int i = 0;

        while (i < cmdLine.length()) {
            char c = cmdLine.charAt(i);

            if (c == '\\') {
                int numBackslashes = 0;
                while (i < cmdLine.length() && cmdLine.charAt(i) == '\\') {
                    numBackslashes++;
                    i++;
                }
                if (i < cmdLine.length() && cmdLine.charAt(i) == '"') {
                    appendBackslashes(current, numBackslashes / 2);
                    if (numBackslashes % 2 == 1) {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = !inQuotes;
                        i++;
                    }
                } else {
                    appendBackslashes(current, numBackslashes);
                }
            } else if (c == '"') {
                inQuotes = !inQuotes;
                i++;
            } else if ((c == ' ' || c == '\t') && !inQuotes) {
                if (current.length() > 0) {
                    args.add(current.toString());
                    current.setLength(0);
                }
                i++;
            } else {
                current.append(c);
                i++;
            }
        }

        if (current.length() > 0) {
            args.add(current.toString());
        }

        return args;
    }

    private static List<String> reconstructArguments() {
        var args = new ArrayList<>(ManagementFactory.getRuntimeMXBean().getInputArguments());

        ensureArgFromProperty(args, "java.class.path", "-cp", Arrays.asList("-cp", "-classpath", "--class-path"));
        ensureArgFromProperty(args, "jdk.module.path", "-p", Arrays.asList("-p", "--module-path"));

        var sunCommand = System.getProperty("sun.java.command");
        if (sunCommand != null && !sunCommand.trim().isEmpty()) {
            Collections.addAll(args, sunCommand.split(" "));
        }

        if (Boolean.getBoolean("relauncher.debug")) {
            System.out.println("[Relauncher Debug] Fallback reconstruction used");
            System.out.println("[Relauncher Debug]   sun.java.command = " + sunCommand);
            System.out.println("[Relauncher Debug]   java.class.path length = " +
                String.valueOf(System.getProperty("java.class.path")).length());
        }

        return Collections.unmodifiableList(args);
    }

    private static void ensureArgFromProperty(List<String> args, String property, String flag, List<String> aliases) {
        for (var alias : aliases) {
            if (args.contains(alias)) {
                return;
            }
        }
        var value = System.getProperty(property);
        if (value != null && !value.trim().isEmpty()) {
            args.add(flag);
            args.add(value);
        }
    }

    private static String findJavaExecutable() {
        var javaHome = System.getProperty("java.home");
        var windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        String binary;
        if (windows) {
            binary = System.console() == null ? "javaw.exe" : "java.exe";
        } else {
            binary = "java";
        }
        return Paths.get(javaHome, "bin", binary).toString();
    }

    static void appendBackslashes(StringBuilder sb, int count) {
        for (int i = 0; i < count; i++) {
            sb.append('\\');
        }
    }

    @SuppressWarnings("OptionalAssignedToNull")
    static Optional<?> invokeProcessHandle(String infoMethod) {
        try {
            var phClass = Class.forName("java.lang.ProcessHandle");
            var current = phClass.getMethod("current").invoke(null);
            var info = current.getClass().getMethod("info").invoke(current);
            return (Optional<?>) info.getClass().getMethod(infoMethod).invoke(info);
        } catch (Exception e) {
            return null;
        }
    }
}
