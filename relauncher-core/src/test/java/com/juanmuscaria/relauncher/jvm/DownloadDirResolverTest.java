// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.jvm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class DownloadDirResolverTest {

    @AfterEach
    void clearSysProp() {
        System.clearProperty("relauncher.java.downloadDir");
    }

    @Test
    void systemPropertyWins() {
        System.setProperty("relauncher.java.downloadDir", "/tmp/rl-test-cache");
        var p = DownloadDirResolver.resolve();
        assertEquals(Paths.get("/tmp/rl-test-cache"), p);
    }

    @Test
    void systemPropertyIgnoredWhenBlank() {
        System.setProperty("relauncher.java.downloadDir", "   ");
        var p = DownloadDirResolver.resolve();
        assertNotEquals(Paths.get("   "), p);
    }

    @Test
    void platformDefaultEndsInRelauncherJdks() {
        var p = DownloadDirResolver.resolve();
        assertTrue(p.endsWith(Paths.get("relauncher", "jdks")),
            () -> "expected ...relauncher/jdks suffix, got " + p);
    }
}
