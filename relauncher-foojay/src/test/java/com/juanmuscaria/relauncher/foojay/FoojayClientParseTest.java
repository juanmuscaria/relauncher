// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.foojay;

import com.juanmuscaria.relauncher.jvm.JvmImageType;
import com.juanmuscaria.relauncher.jvm.JvmVendor;
import com.juanmuscaria.relauncher.jvm.RemotePackage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FoojayClientParseTest {

    @Test
    void parsesTypicalSearchResponse() {
        var json = "{"
            + "\"result\":[{"
            + "\"id\":\"abc123\","
            + "\"distribution\":\"temurin\","
            + "\"java_version\":\"21.0.4\","
            + "\"package_type\":\"jdk\","
            + "\"architecture\":\"x86_64\","
            + "\"operating_system\":\"linux\","
            + "\"size\":183456789,"
            + "\"archive_type\":\"tar.gz\","
            + "\"directly_downloadable\":true,"
            + "\"links\":{"
            + "\"pkg_download_redirect\":\"https://api.foojay.io/disco/v3.0/ids/abc123/redirect\","
            + "\"pkg_info_uri\":\"https://api.foojay.io/disco/v3.0/ids/abc123\""
            + "}}]}";

        var pkgs = FoojayClient.parseSearchResponse(json);
        assertEquals(1, pkgs.size());
        var p = pkgs.get(0);
        assertEquals(JvmVendor.TEMURIN, p.vendor());
        assertEquals(21, p.version().getMajor());
        assertEquals(JvmImageType.JDK, p.imageType());
        assertEquals("x86_64", p.arch());
        assertEquals("linux", p.osName());
        assertEquals(183456789L, p.sizeBytes());
        assertEquals("tar.gz", p.archiveType());
        assertTrue(p.downloadUrl().contains("abc123"));
        assertEquals("foojay:abc123", p.sourceId());
    }

    @Test
    void skipsUnknownDistributions() {
        var json = "{\"result\":[{"
            + "\"id\":\"x\",\"distribution\":\"nosuchvendor\",\"java_version\":\"21\","
            + "\"package_type\":\"jdk\",\"architecture\":\"x86_64\",\"operating_system\":\"linux\","
            + "\"size\":1,\"archive_type\":\"zip\",\"directly_downloadable\":true,"
            + "\"links\":{\"pkg_download_redirect\":\"x\",\"pkg_info_uri\":\"x\"}}]}";
        var pkgs = FoojayClient.parseSearchResponse(json);
        assertTrue(pkgs.isEmpty(), () -> "expected unknown vendor dropped, got " + pkgs);
    }

    @Test
    void skipsNonDirectlyDownloadable() {
        var json = "{\"result\":[{"
            + "\"id\":\"x\",\"distribution\":\"temurin\",\"java_version\":\"21\","
            + "\"package_type\":\"jdk\",\"architecture\":\"x86_64\",\"operating_system\":\"linux\","
            + "\"size\":1,\"archive_type\":\"zip\",\"directly_downloadable\":false,"
            + "\"links\":{\"pkg_download_redirect\":\"x\",\"pkg_info_uri\":\"x\"}}]}";
        var pkgs = FoojayClient.parseSearchResponse(json);
        assertTrue(pkgs.isEmpty());
    }

    @Test
    void emptyResultReturnsEmptyList() {
        var pkgs = FoojayClient.parseSearchResponse("{\"result\":[]}");
        assertTrue(pkgs.isEmpty());
    }

    @Test
    void parsesChecksumSha256() {
        var json = "{\"result\":[{"
            + "\"checksum\":\"abcDEF123\",\"checksum_type\":\"sha256\""
            + "}]}";
        var hash = FoojayClient.parseChecksumResponse(json);
        assertEquals("abcdef123", hash); // lowercased
    }

    @Test
    void parsesChecksumNonSha256ReturnsNull() {
        var json = "{\"result\":[{"
            + "\"checksum\":\"abc\",\"checksum_type\":\"md5\""
            + "}]}";
        assertNull(FoojayClient.parseChecksumResponse(json));
    }

    @Test
    void parsesChecksumMissingFieldsReturnsNull() {
        assertNull(FoojayClient.parseChecksumResponse("{\"result\":[]}"));
        assertNull(FoojayClient.parseChecksumResponse("{\"result\":[{}]}"));
    }

    @Test
    void parsesChecksumEmptyInlineReturnsNull() {
        // Foojay emits this for vendors that publish the checksum at a
        // separate URL (e.g. Oracle GraalVM, GraalVM CE).
        var json = "{\"result\":[{\"checksum\":\"\",\"checksum_type\":\"sha256\"}]}";
        assertNull(FoojayClient.parseChecksumResponse(json));
    }

    @Test
    void parsesChecksumWhitespaceInlineReturnsNull() {
        var json = "{\"result\":[{\"checksum\":\"  \\t\",\"checksum_type\":\"sha256\"}]}";
        assertNull(FoojayClient.parseChecksumResponse(json));
    }

    @Test
    void parsesChecksumJsonNullInlineReturnsNull() {
        var json = "{\"result\":[{\"checksum\":null,\"checksum_type\":\"sha256\"}]}";
        assertNull(FoojayClient.parseChecksumResponse(json));
    }

    @Test
    void parsesChecksumUriPresent() {
        var json = "{\"result\":[{\"checksum\":\"\",\"checksum_type\":\"sha256\","
            + "\"checksum_uri\":\"https://example.com/file.tar.gz.sha256\"}]}";
        assertEquals("https://example.com/file.tar.gz.sha256",
            FoojayClient.parseChecksumUriResponse(json));
    }

    @Test
    void parsesChecksumUriAbsentReturnsNull() {
        var json = "{\"result\":[{\"checksum\":\"abc\",\"checksum_type\":\"sha256\"}]}";
        assertNull(FoojayClient.parseChecksumUriResponse(json));
    }

    @Test
    void parsesChecksumUriEmptyReturnsNull() {
        var json = "{\"result\":[{\"checksum\":\"\",\"checksum_type\":\"sha256\","
            + "\"checksum_uri\":\"\"}]}";
        assertNull(FoojayClient.parseChecksumUriResponse(json));
    }

    @Test
    void parsesChecksumUriWrongTypeReturnsNull() {
        var json = "{\"result\":[{\"checksum\":\"\",\"checksum_type\":\"md5\","
            + "\"checksum_uri\":\"https://example.com/file.md5\"}]}";
        assertNull(FoojayClient.parseChecksumUriResponse(json));
    }

    @Test
    void parsesSidecarBareHash() {
        // GraalVM/Oracle .sha256 sidecars are just the hash, often with a trailing newline.
        var hash = "e0be791c8fda4d03b6b0a0cb824fef3149736170057b3a515252b44419606af0";
        assertEquals(hash, FoojayClient.parseSidecarChecksum(hash + "\n"));
    }

    @Test
    void parsesSidecarHashWithFilename() {
        // Standard sha256sum(1) format: "<hash>  <filename>"
        var hash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        var body = hash + "  some-file.tar.gz\n";
        assertEquals(hash, FoojayClient.parseSidecarChecksum(body));
    }

    @Test
    void parsesSidecarUppercaseLowercased() {
        var hash = "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD";
        assertEquals(hash.toLowerCase(),
            FoojayClient.parseSidecarChecksum(hash + "\n"));
    }

    @Test
    void parsesSidecarBlankReturnsNull() {
        assertNull(FoojayClient.parseSidecarChecksum(""));
        assertNull(FoojayClient.parseSidecarChecksum("\n\n"));
        assertNull(FoojayClient.parseSidecarChecksum("   "));
    }

    @Test
    void parsesSidecarRejectsNonHex() {
        // Not 64 hex chars = not a SHA-256 = treat as missing rather than corrupt.
        assertNull(FoojayClient.parseSidecarChecksum("not-a-hash\n"));
        assertNull(FoojayClient.parseSidecarChecksum("abcd\n"));
    }
}
