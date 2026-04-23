// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.foojay;

import com.google.gson.*;
import com.juanmuscaria.relauncher.jvm.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Talks to the Foojay Disco API.
 */
@SuppressWarnings({"SizeReplaceableByIsEmpty", "CharsetObjectCanBeUsed", "StringOperationCanBeSimplified"})
final class FoojayClient {

    static final String BASE_URL = "https://api.foojay.io/disco/v3.0/packages";

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final String USER_AGENT = "Relauncher/dev (+https://github.com/juanmuscaria/relauncher)";

    private FoojayClient() {
    }

    /**
     * Simple GET that returns the body as a UTF-8 string; throws on non-2xx.
     */
    static String httpGet(String urlStr) throws IOException {
        var conn = openConnection(urlStr);
        conn.setRequestMethod("GET");
        try {
            var code = conn.getResponseCode();
            if (code / 100 != 2) {
                throw new IOException("HTTP " + code + " from " + urlStr);
            }
            try (var in = conn.getInputStream();
                 var buf = new ByteArrayOutputStream()) {
                var chunk = new byte[8192];
                int n;
                while ((n = in.read(chunk)) > 0) buf.write(chunk, 0, n);
                return new String(buf.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Stream the package's archive to {@code target}, invoking {@code progress} periodically.
     */
    static void download(RemotePackage pkg, Path target, ProgressListener progress) throws IOException {
        var conn = openConnection(pkg.downloadUrl());
        conn.setRequestMethod("GET");
        try {
            var code = conn.getResponseCode();
            if (code / 100 != 2) {
                throw new IOException("HTTP " + code + " downloading " + pkg.downloadUrl());
            }
            var total = pkg.sizeBytes() > 0 ? pkg.sizeBytes() : conn.getContentLengthLong();

            try (var in = conn.getInputStream();
                 var out = new BufferedOutputStream(
                     Files.newOutputStream(target))) {
                var buf = new byte[64 * 1024];
                long done = 0;
                long lastReportMillis = 0;
                long lastReportBytes = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new IOException("Download interrupted");
                    }
                    out.write(buf, 0, n);
                    done += n;
                    var now = System.currentTimeMillis();
                    if (done - lastReportBytes >= 1_048_576 || now - lastReportMillis >= 1000) {
                        progress.onProgress(done, total);
                        lastReportBytes = done;
                        lastReportMillis = now;
                    }
                }
                progress.onProgress(done, total);
            }
        } finally {
            conn.disconnect();
        }
    }

    private static HttpURLConnection openConnection(String urlStr) throws IOException {
        var url = new URL(urlStr);
        var conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept", "application/json,*/*");
        return conn;
    }

    /**
     * Build the search URL for a requirement + arch + OS.
     */
    static String buildSearchUrl(JavaRequirement req, String arch, String os) {
        var sb = new StringBuilder(BASE_URL).append('?');
        var first = true;

        for (var major : req.acceptableMajors()) {
            appendParam(sb, first, "version", String.valueOf(major));
            first = false;
        }
        for (var v : req.acceptableVendors()) {
            var name = VendorMapping.toFoojay(v);
            if (name != null) {
                appendParam(sb, first, "distribution", name);
                first = false;
            }
        }

        appendParam(sb, first, "architecture", arch);
        first = false;
        appendParam(sb, first, "operating_system", os);
        // JRE minimum means "JDK or JRE both satisfy" (per JavaRequirement.imageTypeSatisfies).
        // Foojay rejects multi-value package_type, so omit the param entirely to get both.
        // Vendors without a JRE (e.g. GraalVM) would otherwise return zero results.
        if (req.minImageType() == JvmImageType.JDK) {
            appendParam(sb, first, "package_type", "jdk");
        }
        appendParam(sb, first, "archive_type", "tar.gz,zip");
        appendParam(sb, first, "directly_downloadable", "true");
        appendParam(sb, first, "latest", "available");
        appendParam(sb, first, "release_status", "ga");
        appendParam(sb, first, "javafx_bundled", "false");

        return sb.toString();
    }

    /**
     * Parse the JSON response from {@code GET /disco/v3.0/packages?...} into RemotePackages.
     */
    static List<RemotePackage> parseSearchResponse(String json) {
        var root = JsonParser.parseString(json).getAsJsonObject();
        var result = root.has("result") ? root.getAsJsonArray("result") : new JsonArray();

        List<RemotePackage> out = new ArrayList<>();
        var gson = new Gson();
        for (var el : result) {
            var fp = gson.fromJson(el, FoojayPackage.class);
            if (fp == null) continue;
            if (!fp.directly_downloadable) continue;

            var vendor = VendorMapping.fromFoojay(fp.distribution);
            if (vendor == JvmVendor.UNKNOWN) continue;

            JvmImageType imageType;
            if ("jdk".equalsIgnoreCase(fp.package_type)) imageType = JvmImageType.JDK;
            else if ("jre".equalsIgnoreCase(fp.package_type)) imageType = JvmImageType.JRE;
            else continue;

            if (fp.links == null || fp.links.pkg_download_redirect == null) continue;

            var pkg = new RemotePackage(
                vendor,
                new JvmVersion(fp.java_version),
                imageType,
                fp.architecture == null ? null : fp.architecture.toLowerCase(Locale.ROOT),
                fp.operating_system == null ? null : fp.operating_system.toLowerCase(Locale.ROOT),
                fp.size == null ? -1L : fp.size,
                fp.archive_type == null ? null : fp.archive_type.toLowerCase(Locale.ROOT),
                fp.links.pkg_download_redirect,
                /*sha256*/ null, // fetched separately via parseChecksumResponse
                "foojay:" + fp.id);
            out.add(pkg);
        }
        return out;
    }

    /**
     * Parse the JSON response from {@code GET /disco/v3.0/ids/<id>}, returning
     * the lowercase inline SHA-256 or null. Some vendors (Oracle GraalVM,
     * GraalVM CE) return an empty inline {@code checksum} and publish the hash
     * at {@link #parseChecksumUriResponse}.
     */
    static String parseChecksumResponse(String json) {
        var first = firstResult(json);
        if (first == null) return null;
        if (!isSha256Type(first)) return null;

        var cs = first.get("checksum");
        if (cs == null || cs.isJsonNull()) return null;
        var value = cs.getAsString().trim();
        if (value.isEmpty()) return null;
        return value.toLowerCase(Locale.ROOT);
    }

    /**
     * Parse the {@code checksum_uri} from the same per-id response, returning
     * the URL of the sidecar {@code .sha256} file or null.
     */
    static String parseChecksumUriResponse(String json) {
        var first = firstResult(json);
        if (first == null) return null;
        if (!isSha256Type(first)) return null;

        var uri = first.get("checksum_uri");
        if (uri == null || uri.isJsonNull()) return null;
        var value = uri.getAsString().trim();
        return value.isEmpty() ? null : value;
    }

    /**
     * Parse the body of a sidecar {@code .sha256} file. Accepts the common
     * {@code sha256sum(1)} layout {@code "<hash>  <filename>"} as well as
     * Oracle/GraalVM's bare {@code "<hash>"}, returning the lowercase hash or
     * null when no 64-hex-character token is present.
     */
    static String parseSidecarChecksum(String body) {
        if (body == null) return null;
        for (var line : body.split("\\R", -1)) {
            var trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            // First whitespace-separated token, in case "<hash>  <filename>".
            var firstToken = trimmed.split("\\s+", 2)[0];
            if (firstToken.length() == 64 && isHex(firstToken)) {
                return firstToken.toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }

    private static JsonObject firstResult(String json) {
        var root = JsonParser.parseString(json).getAsJsonObject();
        var result = root.has("result") ? root.getAsJsonArray("result") : new JsonArray();
        if (result.size() == 0) return null;
        return result.get(0).getAsJsonObject();
    }

    private static boolean isSha256Type(JsonObject first) {
        var csType = first.get("checksum_type");
        if (csType == null || csType.isJsonNull()) return false;
        return "sha256".equalsIgnoreCase(csType.getAsString());
    }

    private static boolean isHex(String s) {
        for (var i = 0; i < s.length(); i++) {
            var c = s.charAt(i);
            var hex = (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'f')
                || (c >= 'A' && c <= 'F');
            if (!hex) return false;
        }
        return true;
    }

    private static void appendParam(StringBuilder sb, boolean first, String key, String value) {
        if (!first) sb.append('&');
        sb.append(key).append('=').append(encode(value));
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError("UTF-8 always supported", e);
        }
    }
}
