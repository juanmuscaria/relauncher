// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.smoketest.harness;

import com.juanmuscaria.relauncher.launch.Platform;
import com.juanmuscaria.relauncher.launch.Side;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TestPlatformTest {

    // NB: these tests register a Platform, which is a singleton. They must
    // run in a fresh JVM. Gradle's default test task forks a new JVM per
    // test *class*, which is sufficient.

    @Test
    void installCreatesDirectoriesAndRegisters(@TempDir Path scratch) {
        var p = TestPlatform.install(scratch, Side.SERVER);
        assertTrue(Files.isDirectory(p.configDirectory()));
        assertTrue(Files.isDirectory(p.modsDirectory()));
        assertEquals(Side.SERVER, p.side());
        assertSame(p, Platform.current());
    }
}
