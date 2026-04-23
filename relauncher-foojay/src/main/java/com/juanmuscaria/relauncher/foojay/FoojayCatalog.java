// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.foojay;

import com.juanmuscaria.relauncher.jvm.JavaRequirement;
import com.juanmuscaria.relauncher.jvm.JavaRuntimeDownloader;
import com.juanmuscaria.relauncher.jvm.ProgressListener;
import com.juanmuscaria.relauncher.jvm.RemotePackage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Foojay Disco API implementation of {@link JavaRuntimeDownloader}.
 * <p>
 * Two endpoints: {@code /disco/v3.0/packages} for search, and the per-package
 * info URL (advertised in the search response) for checksums. Download is a
 * plain GET on {@code pkg_download_redirect}.
 */
public final class FoojayCatalog implements JavaRuntimeDownloader {

    private final String searchBaseUrl;
    private final String infoUrlPrefix;

    /**
     * Public no-arg constructor used by {@link java.util.ServiceLoader}.
     */
    public FoojayCatalog() {
        this(FoojayClient.BASE_URL, "https://api.foojay.io/disco/v3.0/ids/");
    }

    // Package-private for tests.
    FoojayCatalog(String searchBaseUrl, String infoUrlPrefix) {
        this.searchBaseUrl = searchBaseUrl;
        this.infoUrlPrefix = infoUrlPrefix;
    }

    /**
     * Variant of {@link FoojayClient#buildSearchUrl} that uses a custom base URL
     * (so tests can hit the local HttpServer).
     */
    private static String buildSearchUrlOnBase(String base, JavaRequirement req, String arch, String os) {
        var defaultUrl = FoojayClient.buildSearchUrl(req, arch, os);
        return base + defaultUrl.substring(FoojayClient.BASE_URL.length());
    }

    private static String currentOsName() {
        var os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return "windows";
        if (os.contains("mac")) return "macos";
        return "linux";
    }

    /**
     * Extract the Foojay ID from a sourceId of the form "foojay:<id>".
     */
    private static String extractFoojayId(String sourceId) {
        if (sourceId == null) return "";
        var colon = sourceId.indexOf(':');
        return colon >= 0 ? sourceId.substring(colon + 1) : sourceId;
    }

    @Override
    public List<RemotePackage> search(JavaRequirement req, String arch) throws IOException {
        var os = currentOsName();
        var url = buildSearchUrlOnBase(searchBaseUrl, req, arch, os);
        var json = FoojayClient.httpGet(url);
        var packages = FoojayClient.parseSearchResponse(json);

        // Attach SHA-256 checksum to each package via a second request.
        List<RemotePackage> withChecksum = new ArrayList<>(packages.size());
        for (var p : packages) {
            withChecksum.add(new RemotePackage(p.vendor(), p.version(), p.imageType(),
                p.arch(), p.osName(), p.sizeBytes(), p.archiveType(),
                p.downloadUrl(), fetchChecksum(p.sourceId()), p.sourceId()));
        }
        return withChecksum;
    }

    // Per-id info first, sidecar URL fallback for vendors (Oracle GraalVM,
    // GraalVM CE) that publish the hash separately. Network errors degrade to
    // null so verify warns and proceeds rather than failing the whole download.
    private String fetchChecksum(String sourceId) {
        String infoJson;
        try {
            infoJson = FoojayClient.httpGet(infoUrlPrefix + extractFoojayId(sourceId));
        } catch (IOException e) {
            System.err.println("[Relauncher] Failed to fetch checksum for " + sourceId
                + ": " + e.getMessage());
            return null;
        }
        var inline = FoojayClient.parseChecksumResponse(infoJson);
        if (inline != null) return inline;

        var uri = FoojayClient.parseChecksumUriResponse(infoJson);
        if (uri == null) return null;
        try {
            var sidecar = FoojayClient.httpGet(uri);
            return FoojayClient.parseSidecarChecksum(sidecar);
        } catch (IOException e) {
            System.err.println("[Relauncher] Failed to fetch checksum sidecar " + uri
                + " for " + sourceId + ": " + e.getMessage());
            return null;
        }
    }

    @Override
    public void download(RemotePackage pkg, Path targetFile, ProgressListener progress) throws IOException {
        FoojayClient.download(pkg, targetFile, progress);
    }

}
