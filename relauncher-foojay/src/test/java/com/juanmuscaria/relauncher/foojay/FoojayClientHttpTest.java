// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.foojay;

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

import static org.junit.jupiter.api.Assertions.*;

class FoojayClientHttpTest {

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
    void downloadStreamsBytesAndReportsProgress(@TempDir Path tmp) throws IOException {
        var payload = new byte[100_000];
        for (var i = 0; i < payload.length; i++) payload[i] = (byte) (i & 0xFF);
        server.createContext("/dl", ex -> {
            ex.getResponseHeaders().add("Content-Length", String.valueOf(payload.length));
            ex.sendResponseHeaders(200, payload.length);
            try (var out = ex.getResponseBody()) {
                out.write(payload);
            }
        });

        var pkg = new RemotePackage(
            JvmVendor.TEMURIN, new JvmVersion("21.0.4"), JvmImageType.JDK,
            "x86_64", "linux", payload.length, "zip",
            "http://127.0.0.1:" + port + "/dl", null, "foojay:test");

        var target = tmp.resolve("archive.zip");
        long[] lastReported = {-1};
        FoojayClient.download(pkg, target, (done, total) -> lastReported[0] = done);

        assertEquals(payload.length, Files.size(target));
        assertArrayEquals(payload, Files.readAllBytes(target));
        assertTrue(lastReported[0] > 0, "progress listener never fired");
    }

    @Test
    void searchHitsServerAndReturnsPackages() throws IOException {
        server.createContext("/disco/v3.0/packages", ex -> {
            var body = "{\"result\":[{"
                + "\"id\":\"abc\",\"distribution\":\"temurin\",\"java_version\":\"21.0.4\","
                + "\"package_type\":\"jdk\",\"architecture\":\"x86_64\",\"operating_system\":\"linux\","
                + "\"size\":10,\"archive_type\":\"tar.gz\",\"directly_downloadable\":true,"
                + "\"links\":{\"pkg_download_redirect\":\"http://x/dl\",\"pkg_info_uri\":\"http://x/info\"}"
                + "}]}";
            var b = body.getBytes();
            ex.sendResponseHeaders(200, b.length);
            try (var out = ex.getResponseBody()) {
                out.write(b);
            }
        });

        var url = "http://127.0.0.1:" + port + "/disco/v3.0/packages?version=21";
        var json = FoojayClient.httpGet(url);
        assertTrue(json.contains("\"temurin\""));
    }

    @Test
    void httpGet404ThrowsIoException() {
        server.createContext("/notfound", ex -> {
            ex.sendResponseHeaders(404, -1);
            ex.close();
        });
        var ex = assertThrows(IOException.class,
            () -> FoojayClient.httpGet("http://127.0.0.1:" + port + "/notfound"));
        assertTrue(ex.getMessage().contains("404"));
    }
}
