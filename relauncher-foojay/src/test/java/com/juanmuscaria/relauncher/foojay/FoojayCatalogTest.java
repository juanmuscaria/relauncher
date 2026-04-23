// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.foojay;

import com.juanmuscaria.relauncher.jvm.JavaRequirement;
import com.juanmuscaria.relauncher.jvm.JvmImageType;
import com.juanmuscaria.relauncher.jvm.JvmVendor;
import com.juanmuscaria.relauncher.jvm.JvmVersion;
import com.juanmuscaria.relauncher.jvm.RemotePackage;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FoojayCatalogTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    @Test
    void searchHitsSearchEndpointAndAttachesChecksum() throws IOException {
        var body = "{\"result\":[{"
            + "\"id\":\"abc\",\"distribution\":\"temurin\",\"java_version\":\"21.0.4\","
            + "\"package_type\":\"jdk\",\"architecture\":\"x86_64\",\"operating_system\":\"linux\","
            + "\"size\":10,\"archive_type\":\"tar.gz\",\"directly_downloadable\":true,"
            + "\"links\":{\"pkg_download_redirect\":\"http://127.0.0.1:" + port + "/dl\","
            + "\"pkg_info_uri\":\"http://127.0.0.1:" + port + "/info/abc\"}"
            + "}]}";
        server.createContext("/packages", ex -> {
            var b = body.getBytes();
            ex.sendResponseHeaders(200, b.length);
            try (var o = ex.getResponseBody()) {
                o.write(b);
            }
        });
        server.createContext("/info/abc", ex -> {
            var info = "{\"result\":[{\"checksum\":\"DEADBEEF\",\"checksum_type\":\"sha256\"}]}";
            var b = info.getBytes();
            ex.sendResponseHeaders(200, b.length);
            try (var o = ex.getResponseBody()) {
                o.write(b);
            }
        });

        var catalog = new FoojayCatalog(
            "http://127.0.0.1:" + port + "/packages",
            "http://127.0.0.1:" + port + "/info/"); // id appended by catalog
        var pkgs = catalog.search(JavaRequirement.majors(21), "x86_64");
        assertEquals(1, pkgs.size());
        assertEquals("deadbeef", pkgs.get(0).sha256());
    }

    @Test
    void searchFetchesSidecarWhenInlineChecksumEmpty() throws IOException {
        // Search response: standard package metadata.
        var body = "{\"result\":[{"
            + "\"id\":\"abc\",\"distribution\":\"graalvm_community\",\"java_version\":\"25.0.2\","
            + "\"package_type\":\"jdk\",\"architecture\":\"x86_64\",\"operating_system\":\"linux\","
            + "\"size\":10,\"archive_type\":\"tar.gz\",\"directly_downloadable\":true,"
            + "\"links\":{\"pkg_download_redirect\":\"http://127.0.0.1:" + port + "/dl\","
            + "\"pkg_info_uri\":\"http://127.0.0.1:" + port + "/info/abc\"}"
            + "}]}";
        server.createContext("/packages", ex -> {
            var b = body.getBytes();
            ex.sendResponseHeaders(200, b.length);
            try (var o = ex.getResponseBody()) {
                o.write(b);
            }
        });
        // Per-id info: empty inline checksum, sidecar URL points back at this server.
        server.createContext("/info/abc", ex -> {
            var info = "{\"result\":[{\"checksum\":\"\",\"checksum_type\":\"sha256\","
                + "\"checksum_uri\":\"http://127.0.0.1:" + port + "/sidecar/abc.sha256\"}]}";
            var b = info.getBytes();
            ex.sendResponseHeaders(200, b.length);
            try (var o = ex.getResponseBody()) {
                o.write(b);
            }
        });
        var sidecarHash =
            "e0be791c8fda4d03b6b0a0cb824fef3149736170057b3a515252b44419606af0";
        server.createContext("/sidecar/abc.sha256", ex -> {
            var b = (sidecarHash + "\n").getBytes();
            ex.sendResponseHeaders(200, b.length);
            try (var o = ex.getResponseBody()) {
                o.write(b);
            }
        });

        var catalog = new FoojayCatalog(
            "http://127.0.0.1:" + port + "/packages",
            "http://127.0.0.1:" + port + "/info/");
        var pkgs = catalog.search(JavaRequirement.majors(25), "x86_64");
        assertEquals(1, pkgs.size());
        assertEquals(sidecarHash, pkgs.get(0).sha256());
    }

    @Test
    void searchTolerates404FromSidecarUrl() throws IOException {
        var body = "{\"result\":[{"
            + "\"id\":\"abc\",\"distribution\":\"temurin\",\"java_version\":\"21.0.4\","
            + "\"package_type\":\"jdk\",\"architecture\":\"x86_64\",\"operating_system\":\"linux\","
            + "\"size\":10,\"archive_type\":\"tar.gz\",\"directly_downloadable\":true,"
            + "\"links\":{\"pkg_download_redirect\":\"http://127.0.0.1:" + port + "/dl\","
            + "\"pkg_info_uri\":\"http://127.0.0.1:" + port + "/info/abc\"}"
            + "}]}";
        server.createContext("/packages", ex -> {
            var b = body.getBytes();
            ex.sendResponseHeaders(200, b.length);
            try (var o = ex.getResponseBody()) {
                o.write(b);
            }
        });
        server.createContext("/info/abc", ex -> {
            var info = "{\"result\":[{\"checksum\":\"\",\"checksum_type\":\"sha256\","
                + "\"checksum_uri\":\"http://127.0.0.1:" + port + "/sidecar/missing.sha256\"}]}";
            var b = info.getBytes();
            ex.sendResponseHeaders(200, b.length);
            try (var o = ex.getResponseBody()) {
                o.write(b);
            }
        });
        server.createContext("/sidecar/missing.sha256", ex -> {
            ex.sendResponseHeaders(404, -1);
            ex.close();
        });

        var catalog = new FoojayCatalog(
            "http://127.0.0.1:" + port + "/packages",
            "http://127.0.0.1:" + port + "/info/");
        var pkgs = catalog.search(JavaRequirement.majors(21), "x86_64");
        assertEquals(1, pkgs.size());
        // Sidecar fetch failed: package surfaces with null sha so verifier warns
        // and proceeds rather than throwing a misleading mismatch.
        assertNull(pkgs.get(0).sha256());
    }

    @Test
    void downloadDelegates(@TempDir Path tmp) throws IOException {
        var payload = "jdk-bytes".getBytes();
        server.createContext("/dl", ex -> {
            ex.sendResponseHeaders(200, payload.length);
            try (var o = ex.getResponseBody()) {
                o.write(payload);
            }
        });

        var pkg = new RemotePackage(
            JvmVendor.TEMURIN,
            new JvmVersion("21"),
            JvmImageType.JDK,
            "x86_64", "linux", payload.length, "tar.gz",
            "http://127.0.0.1:" + port + "/dl", null, "test");

        var catalog = new FoojayCatalog(
            "http://127.0.0.1:" + port + "/packages",
            "http://127.0.0.1:" + port + "/info/");
        var target = tmp.resolve("out.bin");
        catalog.download(pkg, target, (a, b) -> {
        });
        assertArrayEquals(payload, Files.readAllBytes(target));
    }
}
