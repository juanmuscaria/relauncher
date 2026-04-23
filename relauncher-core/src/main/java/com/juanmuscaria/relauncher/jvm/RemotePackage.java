// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.jvm;

/**
 * A single package advertised by a {@link JavaRuntimeDownloader}.
 * <p>
 * All fields are populated by the downloader; the {@code DownloadOrchestrator}
 * reads them when choosing the best candidate.
 */
public final class RemotePackage {
    private final JvmVendor vendor;
    private final JvmVersion version;
    private final JvmImageType imageType;
    private final String arch;
    private final String osName;
    private final long sizeBytes;
    private final String archiveType;
    private final String downloadUrl;
    private final String sha256;
    private final String sourceId;

    public RemotePackage(JvmVendor vendor, JvmVersion version, JvmImageType imageType,
                         String arch, String osName, long sizeBytes, String archiveType,
                         String downloadUrl, String sha256, String sourceId) {
        this.vendor = vendor;
        this.version = version;
        this.imageType = imageType;
        this.arch = arch;
        this.osName = osName;
        this.sizeBytes = sizeBytes;
        this.archiveType = archiveType;
        this.downloadUrl = downloadUrl;
        this.sha256 = sha256;
        this.sourceId = sourceId;
    }

    public JvmVendor vendor() {
        return vendor;
    }

    public JvmVersion version() {
        return version;
    }

    public JvmImageType imageType() {
        return imageType;
    }

    public String arch() {
        return arch;
    }

    public String osName() {
        return osName;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public String archiveType() {
        return archiveType;
    }

    public String downloadUrl() {
        return downloadUrl;
    }

    public String sha256() {
        return sha256;
    }

    public String sourceId() {
        return sourceId;
    }

    @Override
    public String toString() {
        return "RemotePackage{" + vendor + " " + version.getOriginalVersionString()
            + " " + imageType + " " + osName + "-" + arch
            + " " + (sizeBytes / (1024 * 1024)) + "MB"
            + " " + archiveType + " (" + sourceId + ")}";
    }
}
