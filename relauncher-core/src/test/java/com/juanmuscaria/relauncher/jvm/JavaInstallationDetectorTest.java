// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.jvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JavaInstallationDetectorTest {

    /**
     * Build a fake JDK layout at {@code home}.
     *
     * @param imageType value for IMAGE_TYPE (null to omit the key)
     * @param withJavac whether to create bin/javac (JDK marker)
     */
    private static Path makeFakeJdk(Path home, String version, String vendor, String arch,
                                    String imageType, boolean withJavac) throws IOException {
        Files.createDirectories(home.resolve("bin"));

        var java = home.resolve("bin").resolve(isWindows() ? "java.exe" : "java");
        Files.write(java, new byte[]{});
        if (!isWindows()) {
            java.toFile().setExecutable(true);
        }

        if (withJavac) {
            var javac = home.resolve("bin").resolve(isWindows() ? "javac.exe" : "javac");
            Files.write(javac, new byte[]{});
        }

        var release = new StringBuilder();
        release.append("JAVA_VERSION=\"").append(version).append("\"\n");
        release.append("IMPLEMENTOR=\"").append(vendor).append("\"\n");
        release.append("OS_ARCH=\"").append(arch).append("\"\n");
        if (imageType != null) {
            release.append("IMAGE_TYPE=\"").append(imageType).append("\"\n");
        }
        Files.write(home.resolve("release"), release.toString().getBytes());

        return home;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    @Test
    void probesReleaseFileAndDetectsJdk(@TempDir Path tmp) throws IOException {
        var home = makeFakeJdk(tmp, "17.0.8", "Eclipse Adoptium", "x86_64", "JDK", true);
        var install = JavaInstallationDetector.probeHome(home);
        assertNotNull(install);
        assertEquals(17, install.version().getMajor());
        assertEquals(JvmVendor.TEMURIN, install.vendor());
        assertEquals(JvmImageType.JDK, install.imageType());
        assertEquals("x86_64", install.arch());
    }

    @Test
    void imageTypeFromJavacWhenReleaseLacksIt(@TempDir Path tmp) throws IOException {
        // release missing IMAGE_TYPE, javac present → JDK
        var home = makeFakeJdk(tmp, "1.8.0_291", "Oracle Corporation", "amd64", null, true);
        var install = JavaInstallationDetector.probeHome(home);
        assertNotNull(install);
        assertEquals(JvmImageType.JDK, install.imageType());
    }

    @Test
    void imageTypeJreWhenJavacAbsent(@TempDir Path tmp) throws IOException {
        var home = makeFakeJdk(tmp, "1.8.0_291", "Oracle Corporation", "amd64", null, false);
        var install = JavaInstallationDetector.probeHome(home);
        assertNotNull(install);
        assertEquals(JvmImageType.JRE, install.imageType());
    }

    @Test
    void returnsNullWhenNoJavaExecutable(@TempDir Path tmp) throws IOException {
        Files.createDirectories(tmp.resolve("empty"));
        var install = JavaInstallationDetector.probeHome(tmp.resolve("empty"));
        assertNull(install);
    }

    @Test
    void scanRootsFindsInstallationsInSubdirs(@TempDir Path tmp) throws IOException {
        var root = Files.createDirectories(tmp.resolve("jvms"));
        makeFakeJdk(root.resolve("jdk-17"), "17.0.8", "Eclipse Adoptium", "x86_64", "JDK", true);
        makeFakeJdk(root.resolve("jdk-21"), "21.0.2", "Eclipse Adoptium", "x86_64", "JDK", true);

        var found = JavaInstallationDetector.scanRoots(List.of(root));
        assertEquals(2, found.size());
    }

    @Test
    void scanRootsSkipsMissingDirs(@TempDir Path tmp) {
        var found = JavaInstallationDetector.scanRoots(
            List.of(tmp.resolve("does-not-exist")));
        assertTrue(found.isEmpty());
    }

    @Test
    void defaultScanRootsIncludesDownloadDir() {
        try {
            System.setProperty("relauncher.java.downloadDir", "/tmp/rl-test-roots");
            var roots = JavaInstallationDetector.defaultScanRoots();
            assertTrue(roots.contains(Paths.get("/tmp/rl-test-roots")),
                () -> "download dir not in scan roots; got " + roots);
        } finally {
            System.clearProperty("relauncher.java.downloadDir");
        }
    }
}
