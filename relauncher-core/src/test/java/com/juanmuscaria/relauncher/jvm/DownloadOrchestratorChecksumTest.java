// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.jvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DownloadOrchestratorChecksumTest {

    // SHA-256 of the empty string
    private static final String EMPTY_SHA256 =
        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @Test
    void acceptsMatchingChecksum(@TempDir Path tmp) throws IOException {
        var file = tmp.resolve("empty.bin");
        Files.write(file, new byte[0]);
        assertDoesNotThrow(() -> DownloadOrchestrator.verifySha256(file, EMPTY_SHA256));
    }

    @Test
    void rejectsMismatchedChecksum(@TempDir Path tmp) throws IOException {
        var file = tmp.resolve("empty.bin");
        Files.write(file, new byte[0]);
        var ex = assertThrows(IOException.class,
            () -> DownloadOrchestrator.verifySha256(file, "0".repeat(64)));
        assertTrue(ex.getMessage().toLowerCase().contains("checksum"));
    }

    @Test
    void isCaseInsensitive(@TempDir Path tmp) throws IOException {
        var file = tmp.resolve("empty.bin");
        Files.write(file, new byte[0]);
        assertDoesNotThrow(() -> DownloadOrchestrator.verifySha256(file, EMPTY_SHA256.toUpperCase()));
    }

    @Test
    void skipsVerificationWhenExpectedIsNull(@TempDir Path tmp) throws IOException {
        var file = tmp.resolve("garbage.bin");
        Files.write(file, new byte[]{1, 2, 3});
        // Null expected = log warning and skip, no throw
        assertDoesNotThrow(() -> DownloadOrchestrator.verifySha256(file, null));
    }

    @Test
    void skipsVerificationWhenExpectedIsEmpty(@TempDir Path tmp) throws IOException {
        var file = tmp.resolve("garbage.bin");
        Files.write(file, new byte[]{1, 2, 3});
        // Empty string is treated like null: warn and skip, do not throw a useless
        // "expected , got <hash>" mismatch.
        assertDoesNotThrow(() -> DownloadOrchestrator.verifySha256(file, ""));
    }

    @Test
    void skipsVerificationWhenExpectedIsBlank(@TempDir Path tmp) throws IOException {
        var file = tmp.resolve("garbage.bin");
        Files.write(file, new byte[]{1, 2, 3});
        assertDoesNotThrow(() -> DownloadOrchestrator.verifySha256(file, "   \t\n"));
    }

    @Test
    void computesKnownHash(@TempDir Path tmp) throws IOException {
        var file = tmp.resolve("abc.bin");
        Files.write(file, "abc".getBytes());
        // SHA-256("abc") = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
        var computed = DownloadOrchestrator.computeSha256Hex(file);
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", computed);
    }
}
