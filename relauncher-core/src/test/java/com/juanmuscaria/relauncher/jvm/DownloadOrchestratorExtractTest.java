// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.jvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class DownloadOrchestratorExtractTest {

    /**
     * Build a minimal USTAR header for a regular file.
     */
    private static byte[] makeUstarHeader(String name, long size, int mode) {
        var h = new byte[512];
        var nb = name.getBytes();
        System.arraycopy(nb, 0, h, 0, Math.min(nb.length, 100));
        writeOctal(h, 100, 8, mode);
        writeOctal(h, 108, 8, 0);    // uid
        writeOctal(h, 116, 8, 0);    // gid
        writeOctal(h, 124, 12, size);
        writeOctal(h, 136, 12, 0);   // mtime
        for (var i = 148; i < 156; i++) h[i] = ' ';  // checksum placeholder
        h[156] = '0';                 // typeflag: regular file
        var magic = "ustar".getBytes();
        System.arraycopy(magic, 0, h, 257, magic.length);
        h[263] = '0';
        h[264] = '0';   // version "00"

        var checksum = 0;
        for (var b : h) checksum += (b & 0xFF);
        writeOctal(h, 148, 7, checksum);
        h[155] = ' ';
        return h;
    }

    private static void writeOctal(byte[] h, int off, int len, long value) {
        var s = Long.toOctalString(value);
        var pad = len - 1 - s.length();
        for (var i = 0; i < pad; i++) h[off + i] = '0';
        var sb = s.getBytes();
        System.arraycopy(sb, 0, h, off + pad, sb.length);
        h[off + len - 1] = 0;
    }

    @Test
    void extractsZipAndStripsTopLevelDir(@TempDir Path tmp) throws IOException {
        var zip = tmp.resolve("fake.zip");
        try (var zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("jdk-21.0.4/bin/java"));
            zos.write("binary".getBytes());
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("jdk-21.0.4/release"));
            zos.write("JAVA_VERSION=\"21.0.4\"\n".getBytes());
            zos.closeEntry();
        }

        var dest = tmp.resolve("extracted");
        DownloadOrchestrator.extract(zip, "zip", dest);

        assertTrue(Files.isRegularFile(dest.resolve("bin").resolve("java")));
        assertTrue(Files.isRegularFile(dest.resolve("release")));
        assertEquals("binary", new String(Files.readAllBytes(dest.resolve("bin").resolve("java"))));
    }

    @Test
    void rejectsZipSlipAttempt(@TempDir Path tmp) throws IOException {
        var zip = tmp.resolve("evil.zip");
        try (var zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("../evil.txt"));
            zos.write("pwn".getBytes());
            zos.closeEntry();
        }

        var dest = tmp.resolve("extracted");
        var ex = assertThrows(IOException.class,
            () -> DownloadOrchestrator.extract(zip, "zip", dest));
        assertTrue(ex.getMessage().toLowerCase().contains("unsafe")
            || ex.getMessage().toLowerCase().contains("traversal"));
    }

    @Test
    void rejectsAbsolutePathInZip(@TempDir Path tmp) throws IOException {
        var zip = tmp.resolve("evil.zip");
        try (var zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("/etc/passwd"));
            zos.write("pwn".getBytes());
            zos.closeEntry();
        }

        var dest = tmp.resolve("extracted");
        assertThrows(IOException.class,
            () -> DownloadOrchestrator.extract(zip, "zip", dest));
    }

    @Test
    void extractsTarGzMinimal(@TempDir Path tmp) throws IOException {
        // Build a tiny USTAR tar.gz in memory.
        // Entry: "jdk-21.0.4/bin/java" with body "hello"
        var body = "hello".getBytes();
        var header = makeUstarHeader("jdk-21.0.4/bin/java", body.length, 0100755);
        var raw = new ByteArrayOutputStream();
        raw.write(header);
        raw.write(body);
        // Pad body to 512
        raw.write(new byte[512 - body.length]);
        // Two 512-byte blocks of zeros = end of archive
        raw.write(new byte[1024]);

        var uncompressed = raw.toByteArray();
        var tgz = tmp.resolve("fake.tar.gz");
        try (var gz =
                 new GZIPOutputStream(Files.newOutputStream(tgz))) {
            gz.write(uncompressed);
        }

        var dest = tmp.resolve("extracted");
        DownloadOrchestrator.extract(tgz, "tar.gz", dest);

        assertTrue(Files.isRegularFile(dest.resolve("bin").resolve("java")));
        assertEquals("hello", new String(Files.readAllBytes(dest.resolve("bin").resolve("java"))));
    }
}
