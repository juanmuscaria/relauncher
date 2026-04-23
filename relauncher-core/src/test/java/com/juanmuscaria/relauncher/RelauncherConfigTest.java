// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher;

import com.juanmuscaria.relauncher.jvm.JvmImageType;
import com.juanmuscaria.relauncher.jvm.JvmVendor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RelauncherConfigTest {

    private static void writeConfig(Path tmp, String... lines) throws IOException {
        Files.write(tmp.resolve("config.cfg"), Arrays.asList(lines));
    }

    @Test
    void parsesJavaVersions(@TempDir Path tmp) throws IOException {
        writeConfig(tmp, "enabled = true",
            "java.versions = 17, 21");
        var loaded = RelauncherConfig.load(tmp);
        assertEquals(Arrays.asList(17, 21), loaded.getJavaVersions());
    }

    @Test
    void parsesJavaVendorsWithAliases(@TempDir Path tmp) throws IOException {
        writeConfig(tmp, "java.vendors = temurin, adoptium, graalvm");
        var loaded = RelauncherConfig.load(tmp);
        // "temurin" and "adoptium" both resolve to TEMURIN; expect deduplicated list
        assertEquals(Arrays.asList(JvmVendor.TEMURIN, JvmVendor.GRAALVM), loaded.getJavaVendors());
    }

    @Test
    void parsesImageType(@TempDir Path tmp) throws IOException {
        writeConfig(tmp, "java.imageType = jdk");
        var loaded = RelauncherConfig.load(tmp);
        assertEquals(JvmImageType.JDK, loaded.getJavaImageType());
    }

    @Test
    void defaultsImageTypeToJre(@TempDir Path tmp) throws IOException {
        writeConfig(tmp, "# no image type");
        var loaded = RelauncherConfig.load(tmp);
        assertEquals(JvmImageType.JRE, loaded.getJavaImageType());
    }

    @Test
    void dropsInvalidMajorsAndLogs(@TempDir Path tmp) throws IOException {
        writeConfig(tmp, "java.versions = 17, notanumber, 21");
        var loaded = RelauncherConfig.load(tmp);
        assertEquals(Arrays.asList(17, 21), loaded.getJavaVersions());
    }

    @Test
    void dropsInvalidVendors(@TempDir Path tmp) throws IOException {
        writeConfig(tmp, "java.vendors = temurin, nosuchbrand");
        var loaded = RelauncherConfig.load(tmp);
        assertEquals(List.of(JvmVendor.TEMURIN), loaded.getJavaVendors());
    }

    @Test
    void emptyJavaConfigProducesNoRequirement(@TempDir Path tmp) throws IOException {
        writeConfig(tmp, "enabled = true");
        var loaded = RelauncherConfig.load(tmp);
        assertTrue(loaded.getJavaVersions().isEmpty());
        assertTrue(loaded.getJavaVendors().isEmpty());
        assertFalse(loaded.hasJavaRequirement());
    }

    @Test
    void anyJavaKeyTriggersRequirement(@TempDir Path tmp) throws IOException {
        writeConfig(tmp, "java.versions = 17");
        var loaded = RelauncherConfig.load(tmp);
        assertTrue(loaded.hasJavaRequirement());
    }

    @Test
    void parsesJavaAutoDownload(@TempDir Path tmp) throws IOException {
        Files.write(tmp.resolve("config.cfg"), List.of("java.autoDownload = true"));
        var loaded = RelauncherConfig.load(tmp);
        assertTrue(loaded.isJavaAutoDownload());
    }

    @Test
    void javaAutoDownloadDefaultsFalse(@TempDir Path tmp) throws IOException {
        Files.write(tmp.resolve("config.cfg"), List.of("# no flag"));
        var loaded = RelauncherConfig.load(tmp);
        assertFalse(loaded.isJavaAutoDownload());
    }

    @Test
    void parsesJavaDownloadDir(@TempDir Path tmp) throws IOException {
        Files.write(tmp.resolve("config.cfg"),
            List.of("java.downloadDir = /tmp/custom-jdks"));
        var loaded = RelauncherConfig.load(tmp);
        assertEquals(Paths.get("/tmp/custom-jdks"), loaded.getJavaDownloadDir());
    }

    @Test
    void javaDownloadDirIsNullWhenUnset(@TempDir Path tmp) throws IOException {
        Files.write(tmp.resolve("config.cfg"), List.of("# nothing"));
        var loaded = RelauncherConfig.load(tmp);
        assertNull(loaded.getJavaDownloadDir());
    }
}
