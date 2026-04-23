// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.jvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.*;

class DownloadOrchestratorLockTest {

    @Test
    void acquiresWhenUncontended(@TempDir Path tmp) throws Exception {
        try (var h = DownloadOrchestrator.acquireLock(tmp, 1000)) {
            assertNotNull(h);
        }
    }

    @Test
    void timesOutWhenLockHeldElsewhere(@TempDir Path tmp) throws Exception {
        Files.createDirectories(tmp);
        var lockFile = tmp.resolve(".lock");
        try (var ch = FileChannel.open(lockFile,
            StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var ignored = ch.lock()) {
            var thrown = assertThrows(IOException.class,
                () -> DownloadOrchestrator.acquireLock(tmp, 800));
            assertTrue(thrown.getMessage().toLowerCase().contains("lock"),
                () -> "expected lock-related message, got " + thrown.getMessage());
        }
    }

    @Test
    void createsLockDirIfMissing(@TempDir Path tmp) throws Exception {
        var nested = tmp.resolve("does").resolve("not").resolve("exist");
        try (var h = DownloadOrchestrator.acquireLock(nested, 1000)) {
            assertTrue(Files.isDirectory(nested));
            assertTrue(Files.isRegularFile(nested.resolve(".lock")));
        }
    }
}
