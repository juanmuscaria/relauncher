// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.jvm;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Finds Java installations on the current system.
 * <p>
 * Scans standard OS-specific roots plus the current {@link #currentJavaHome()}
 * and probes each candidate via {@link ReleaseFileProbe} (fast) with
 * {@link ProcessProbe} as a fallback.
 */
public final class JavaInstallationDetector {

    private JavaInstallationDetector() {
    }

    /**
     * Detect all installations reachable from the standard scan roots.
     */
    public static List<JavaInstallation> detect() {
        List<Path> roots = new ArrayList<>(defaultScanRoots());
        List<JavaInstallation> installs = new ArrayList<>(scanRoots(roots));

        var current = probeHome(currentJavaHome());
        if (current != null) {
            var alreadyPresent = false;
            for (var i : installs) {
                if (i.home().equals(current.home())) {
                    alreadyPresent = true;
                    break;
                }
            }
            if (!alreadyPresent) installs.add(current);
        }

        return installs;
    }

    /**
     * The current JVM's home directory, or null if the property is missing.
     */
    public static Path currentJavaHome() {
        var home = System.getProperty("java.home");
        return home != null ? Paths.get(home) : null;
    }

    /**
     * Shallow-scan a list of roots, collecting successfully probed installs.
     */
    public static List<JavaInstallation> scanRoots(List<Path> roots) {
        Set<Path> seenHomes = new HashSet<>();
        List<JavaInstallation> result = new ArrayList<>();

        for (var root : roots) {
            if (root == null || !Files.isDirectory(root)) continue;
            collectCandidates(root, seenHomes, result);
        }
        return result;
    }

    private static void collectCandidates(Path root, Set<Path> seen, List<JavaInstallation> out) {
        try (var stream = Files.newDirectoryStream(root)) {
            for (var child : stream) {
                if (!Files.isDirectory(child)) continue;

                var directHome = child;
                var macHome = child.resolve("Contents").resolve("Home");

                tryProbe(directHome, seen, out);
                if (Files.isDirectory(macHome)) {
                    tryProbe(macHome, seen, out);
                }
            }
        } catch (IOException ignored) {
            // skip unreadable roots
        }
    }

    private static void tryProbe(Path home, Set<Path> seen, List<JavaInstallation> out) {
        Path resolved;
        try {
            resolved = home.toRealPath();
        } catch (IOException e) {
            resolved = home.toAbsolutePath();
        }
        if (!seen.add(resolved)) return;

        var install = probeHome(home);
        if (install != null) out.add(install);
    }

    /**
     * Probe a specific java home directory. Returns null if there's no
     * {@code bin/java} or all probe strategies fail.
     */
    public static JavaInstallation probeHome(Path home) {
        if (home == null) return null;
        var windows = isWindows();
        var executable = home.resolve("bin").resolve(windows ? "java.exe" : "java");
        if (!Files.isRegularFile(executable)) return null;

        var rel = ReleaseFileProbe.load(home);
        var rawVersion = rel.javaVersion;
        var rawImplementor = rel.implementor;
        var rawArch = rel.osArch;
        var rawImageType = rel.imageType;

        if (rawVersion == null || rawImplementor == null) {
            var proc = ProcessProbe.probe(executable);
            if (rawVersion == null) rawVersion = proc.javaVersion;
            if (rawImplementor == null) rawImplementor = proc.implementor;
            if (rawArch == null) rawArch = proc.osArch;
        }

        if (rawVersion == null) return null;

        var version = new JvmVersion(rawVersion);
        var vendor = JvmVendor.guessFromVendor(rawImplementor);
        var imageType = detectImageType(home, rawImageType, windows);

        return new JavaInstallation(home, executable, version, vendor, imageType, rawArch);
    }

    private static JvmImageType detectImageType(Path home, String releaseImageType, boolean windows) {
        if (releaseImageType != null) {
            if (releaseImageType.equalsIgnoreCase("JDK")) return JvmImageType.JDK;
            if (releaseImageType.equalsIgnoreCase("JRE")) return JvmImageType.JRE;
        }
        var bin = home.resolve("bin");
        if (!Files.isDirectory(bin)) return JvmImageType.UNKNOWN;
        var javac = bin.resolve(windows ? "javac.exe" : "javac");
        return Files.isRegularFile(javac) ? JvmImageType.JDK : JvmImageType.JRE;
    }

    /**
     * Platform-appropriate default scan roots.
     */
    public static List<Path> defaultScanRoots() {
        var os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        var userHome = System.getProperty("user.home", "");
        Set<Path> roots = new LinkedHashSet<>();

        if (os.contains("win")) {
            var pf = env("ProgramFiles", "C:\\Program Files");
            var lad = env("LOCALAPPDATA", userHome + "\\AppData\\Local");
            roots.add(Paths.get(pf, "Java"));
            roots.add(Paths.get(pf, "Eclipse Adoptium"));
            roots.add(Paths.get(pf, "Microsoft"));
            roots.add(Paths.get(pf, "Zulu"));
            roots.add(Paths.get(pf, "BellSoft"));
            roots.add(Paths.get(pf, "GraalVM"));
            roots.add(Paths.get(lad, "Programs", "Eclipse Adoptium"));
            roots.add(Paths.get(userHome, ".gradle", "jdks"));
            roots.add(Paths.get(userHome, ".jdks"));
        } else if (os.contains("mac")) {
            roots.add(Paths.get("/Library/Java/JavaVirtualMachines"));
            roots.add(Paths.get(userHome, "Library", "Java", "JavaVirtualMachines"));
            roots.add(Paths.get(userHome, ".sdkman", "candidates", "java"));
            roots.add(Paths.get(userHome, ".gradle", "jdks"));
        } else {
            roots.addAll(Arrays.asList(
                Paths.get("/usr/lib/jvm"),
                Paths.get("/usr/lib32/jvm"),
                Paths.get("/usr/java"),
                Paths.get("/opt/jdk"),
                Paths.get("/opt/jdks"),
                Paths.get(userHome, ".sdkman", "candidates", "java"),
                Paths.get(userHome, ".jdks"),
                Paths.get(userHome, ".gradle", "jdks")));
        }

        roots.add(DownloadDirResolver.resolve());

        return new ArrayList<>(roots);
    }

    private static String env(String key, String fallback) {
        var v = System.getenv(key);
        return v == null || v.isEmpty() ? fallback : v;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
