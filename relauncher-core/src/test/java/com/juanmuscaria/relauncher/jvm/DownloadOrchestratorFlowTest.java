// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.jvm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class DownloadOrchestratorFlowTest {

    /**
     * Build a "fake JDK" zip at {@code out} with bin/java + release file.
     */
    private static void makeFakeJdkZip(Path out) throws IOException {
        try (var zos = new ZipOutputStream(Files.newOutputStream(out))) {
            zos.putNextEntry(new ZipEntry("jdk-21.0.4/bin/java"));
            zos.write("fake binary".getBytes());
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("jdk-21.0.4/release"));
            zos.write(("""
                JAVA_VERSION="21.0.4"
                IMPLEMENTOR="Eclipse Adoptium"
                OS_ARCH="x86_64"
                IMAGE_TYPE="JDK"
                """).getBytes());
            zos.closeEntry();
        }
    }

    @AfterEach
    void clearSysProp() {
        System.clearProperty("relauncher.java.autoDownload");
    }

    @Test
    void happyPathDownloadsExtractsAndProbes(@TempDir Path tmp) throws Exception {
        System.setProperty("relauncher.java.autoDownload", "true"); // pre-consent

        var fakeArchive = tmp.resolve("upstream.zip");
        makeFakeJdkZip(fakeArchive);
        var sha = DownloadOrchestrator.computeSha256Hex(fakeArchive);

        var pkg = new RemotePackage(
            JvmVendor.TEMURIN, new JvmVersion("21.0.4"), JvmImageType.JDK,
            "x86_64", "linux", Files.size(fakeArchive), "zip",
            fakeArchive.toUri().toString(), sha, "test");

        var stub = new JavaRuntimeDownloader() {
            @Override
            public List<RemotePackage> search(JavaRequirement req, String arch) {
                return Collections.singletonList(pkg);
            }

            @Override
            public void download(RemotePackage p, Path target, ProgressListener cb) throws IOException {
                Files.copy(Path.of(URI.create(p.downloadUrl())), target,
                    StandardCopyOption.REPLACE_EXISTING);
                cb.onProgress(Files.size(target), Files.size(target));
            }
        };

        var downloadDir = tmp.resolve("dl");
        var result = DownloadOrchestrator.orchestrate(
            JavaRequirement.majors(21),
            "x86_64",
            /*configAutoDownload*/ false,
            /*clientWithTinyFd*/ false,
            () -> false,
            downloadDir,
            Collections.singletonList(stub));

        assertEquals(DownloadOrchestrator.Result.Status.SUCCESS, result.status());
        var install = result.installation();
        assertNotNull(install, "expected a JavaInstallation after download");
        assertEquals(21, install.version().getMajor());
        assertTrue(Files.isRegularFile(install.executable()),
            () -> "executable not found: " + install.executable());
    }

    @Test
    void deniedConsentReturnsConsentDenied(@TempDir Path tmp) throws Exception {
        var stub = new JavaRuntimeDownloader() {
            @Override
            public List<RemotePackage> search(JavaRequirement req, String arch) {
                return Collections.emptyList();
            }

            @Override
            public void download(RemotePackage p, Path target, ProgressListener cb) {
                fail("download should not be called on denied consent");
            }
        };

        var result = DownloadOrchestrator.orchestrate(
            JavaRequirement.majors(21), "x86_64",
            false, false, () -> false,
            tmp.resolve("dl"),
            Collections.singletonList(stub));
        assertEquals(DownloadOrchestrator.Result.Status.CONSENT_DENIED, result.status());
        assertNull(result.installation());
    }

    @Test
    void noSearchMatchesReturnsNoMatchingPackage(@TempDir Path tmp) throws Exception {
        System.setProperty("relauncher.java.autoDownload", "true");

        var stub = new JavaRuntimeDownloader() {
            @Override
            public List<RemotePackage> search(JavaRequirement req, String arch) {
                return Collections.emptyList();
            }

            @Override
            public void download(RemotePackage p, Path target, ProgressListener cb) {
                fail("download should not be called when no matches");
            }
        };

        var result = DownloadOrchestrator.orchestrate(
            JavaRequirement.majors(21), "x86_64",
            false, false, () -> false,
            tmp.resolve("dl"),
            Collections.singletonList(stub));
        assertEquals(DownloadOrchestrator.Result.Status.NO_MATCHING_PACKAGE, result.status());
        assertNull(result.installation());
    }

    @Test
    void emptyDownloadersReturnsNoSpi(@TempDir Path tmp) throws Exception {
        var result = DownloadOrchestrator.orchestrate(
            JavaRequirement.majors(21), "x86_64",
            true, false, () -> true,
            tmp.resolve("dl"),
            Collections.emptyList());
        assertEquals(DownloadOrchestrator.Result.Status.NO_SPI, result.status());
        assertNull(result.installation());
    }

    @Test
    void searchThrowIsTreatedAsSkipAndNextSpiIsTried(@TempDir Path tmp) throws Exception {
        System.setProperty("relauncher.java.autoDownload", "true");

        var fakeArchive = tmp.resolve("upstream.zip");
        makeFakeJdkZip(fakeArchive);
        var sha = DownloadOrchestrator.computeSha256Hex(fakeArchive);
        var pkg = new RemotePackage(
            JvmVendor.TEMURIN, new JvmVersion("21.0.4"), JvmImageType.JDK,
            "x86_64", "linux", Files.size(fakeArchive), "zip",
            fakeArchive.toUri().toString(), sha, "test");

        var broken = new JavaRuntimeDownloader() {
            @Override
            public List<RemotePackage> search(JavaRequirement req, String arch) throws IOException {
                throw new IOException("simulated transport failure");
            }

            @Override
            public void download(RemotePackage p, Path target, ProgressListener cb) {
                fail("broken downloader should not be used");
            }

        };
        var working = new JavaRuntimeDownloader() {
            @Override
            public List<RemotePackage> search(JavaRequirement req, String arch) {
                return Collections.singletonList(pkg);
            }

            @Override
            public void download(RemotePackage p, Path target, ProgressListener cb) throws IOException {
                Files.copy(Path.of(URI.create(p.downloadUrl())), target,
                    StandardCopyOption.REPLACE_EXISTING);
                cb.onProgress(Files.size(target), Files.size(target));
            }

            @Override
            public int priority() {
                return 1;
            }
        };

        var result = DownloadOrchestrator.orchestrate(
            JavaRequirement.majors(21), "x86_64",
            false, false, () -> false,
            tmp.resolve("dl"),
            Arrays.asList(broken, working));
        assertEquals(DownloadOrchestrator.Result.Status.SUCCESS, result.status());
        assertNotNull(result.installation());
    }
}
