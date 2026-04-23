// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.jvm;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Download fallback when detection finds no matching java install:
 * consent → lock → SPI search → pick → download → verify → extract → probe.
 * Loaded from {@code Relauncher.resolveJavaExecutable} only when a
 * {@link JavaRuntimeDownloader} SPI is on the classpath. External callers
 * go through {@code Relauncher.relaunch}, public only so the relauncher
 * package can call {@link #orchestrate}.
 */
public final class DownloadOrchestrator {

    private DownloadOrchestrator() {
    }

    /**
     * Full download fallback flow. Returns a {@link Result} describing the outcome:
     * a successful install, consent denied, or no matching package across all
     * downloaders. Throws {@link IOException} for recoverable failures (lock
     * timeout, download error, checksum mismatch, extraction failure), callers
     * surface these as hard-fail.
     */
    public static Result orchestrate(JavaRequirement combined,
                                     String currentArch,
                                     boolean configAutoDownload,
                                     boolean clientWithTinyFd,
                                     ConsentPrompter prompter,
                                     Path downloadDir,
                                     Collection<JavaRuntimeDownloader> downloaders) throws IOException {
        if (downloaders == null || downloaders.isEmpty()) return Result.noSpi();

        var consent = checkConsent(clientWithTinyFd, configAutoDownload, prompter);
        if (consent == ConsentDecision.DENIED) {
            System.err.println("[Relauncher] Download consent denied; skipping downloader SPIs");
            return Result.consentDenied();
        }

        // Pick best package across downloaders in priority order.
        List<JavaRuntimeDownloader> ordered = new ArrayList<>(downloaders);
        ordered.sort(Comparator.comparingInt(JavaRuntimeDownloader::priority));

        JavaRuntimeDownloader chosenDownloader = null;
        RemotePackage chosenPackage = null;

        for (var d : ordered) {
            List<RemotePackage> remotes;
            try {
                remotes = d.search(combined, currentArch);
            } catch (IOException e) {
                System.err.println("[Relauncher] " + d.getClass().getSimpleName()
                    + ".search failed: " + e.getMessage() + ", trying next downloader");
                continue;
            }
            if (remotes == null || remotes.isEmpty()) continue;

            // Local filter as belt-and-suspenders against API drift.
            List<RemotePackage> filtered = new ArrayList<>();
            for (var p : remotes) {
                if (packageMatches(p, combined, currentArch)) filtered.add(p);
            }
            if (filtered.isEmpty()) continue;

            filtered.sort((a, b) -> {
                var byVersion = b.version().compareTo(a.version());
                if (byVersion != 0) return byVersion;
                var wantJdk = combined.minImageType() == JvmImageType.JDK;
                if (wantJdk && a.imageType() != b.imageType()) {
                    return a.imageType() == JvmImageType.JDK ? -1 : 1;
                }
                return Long.compare(a.sizeBytes(), b.sizeBytes());
            });
            chosenDownloader = d;
            chosenPackage = filtered.get(0);
            break;
        }

        if (chosenPackage == null) return Result.noMatchingPackage();

        // 3. Acquire lock, re-scan under the lock (another instance may have landed the install).
        Files.createDirectories(downloadDir);
        try (var ignored = acquireLock(downloadDir, 60_000L)) {
            var alreadyThere = rescanForMatch(downloadDir, combined, currentArch);
            if (alreadyThere != null) return Result.success(alreadyThere);

            // 4. Download.
            var staging = Files.createTempFile(downloadDir, ".staging-", "." + chosenPackage.archiveType());
            try {
                var totalExpected = chosenPackage.sizeBytes();
                System.out.println("[Relauncher] Downloading " + chosenPackage);
                chosenDownloader.download(chosenPackage, staging, (done, total) ->
                    System.out.println("[Relauncher] Progress: "
                        + (total > 0 ? (done * 100L / total) + "%" : done + " bytes")));

                // 5. Verify.
                verifySha256(staging, chosenPackage.sha256());

                // 6. Extract.
                var home = downloadDir.resolve(installDirName(chosenPackage));
                if (Files.exists(home)) {
                    deleteRecursively(home);
                }
                extract(staging, chosenPackage.archiveType(), home);

                // 7. Probe.
                var install = JavaInstallationDetector.probeHome(home);
                if (install == null) {
                    deleteRecursively(home);
                    throw new IOException("Downloaded archive extracted but probe failed at " + home);
                }

                System.out.println("[Relauncher] Java ready at " + home + "; relaunching");
                return Result.success(install);
            } finally {
                try {
                    Files.deleteIfExists(staging);
                } catch (IOException ignored2) {
                }
            }
        }
    }

    // same match rules core uses for local installs
    private static boolean packageMatches(RemotePackage p, JavaRequirement req, String currentArch) {
        // Build a synthetic JavaInstallation just to reuse JavaRequirement.matches.
        var synth = new JavaInstallation(
            Paths.get("/"), Paths.get("/"),
            p.version(), p.vendor(), p.imageType(), p.arch());
        return req.matches(synth, currentArch);
    }

    private static JavaInstallation rescanForMatch(Path downloadDir, JavaRequirement req, String arch) {
        for (var install : JavaInstallationDetector.scanRoots(
            Collections.singletonList(downloadDir))) {
            if (req.matches(install, arch)) return install;
        }
        return null;
    }

    private static String installDirName(RemotePackage p) {
        var v = p.version().getOriginalVersionString() == null ? "unknown"
            : p.version().getOriginalVersionString().replaceAll("[^A-Za-z0-9._+-]", "_");
        return (p.vendor().name().toLowerCase(Locale.ROOT))
            + "-" + v + "-" + p.arch();
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file,
                                                           BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // Precedence: -Drelauncher.java.autoDownload=true|false explicit override,
    // then prompter (if clientWithTinyFd), then configAutoDownload, else DENIED
    static ConsentDecision checkConsent(boolean clientWithTinyFd,
                                        boolean configAutoDownload,
                                        ConsentPrompter prompter) {
        var sysProp = System.getProperty("relauncher.java.autoDownload");
        if (sysProp != null) {
            var v = sysProp.trim().toLowerCase(Locale.ROOT);
            if (v.equals("true")) return ConsentDecision.GRANTED;
            if (v.equals("false")) return ConsentDecision.DENIED;
            // Invalid value: log and fall through
            System.err.println("[Relauncher] Ignoring invalid relauncher.java.autoDownload value: " + sysProp);
        }

        if (clientWithTinyFd) {
            return prompter.promptYesNo() ? ConsentDecision.GRANTED : ConsentDecision.DENIED;
        }

        return configAutoDownload ? ConsentDecision.GRANTED : ConsentDecision.DENIED;
    }

    // advisory lock at <downloadDir>/.lock, polls every 500ms up to timeoutMillis
    static LockHandle acquireLock(Path downloadDir, long timeoutMillis) throws IOException {
        Files.createDirectories(downloadDir);
        var lockFile = downloadDir.resolve(".lock");

        var deadline = System.currentTimeMillis() + timeoutMillis;
        while (true) {
            var channel = FileChannel.open(lockFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            try {
                var lock = channel.tryLock();
                if (lock != null) {
                    return new LockHandle(channel, lock);
                }
            } catch (OverlappingFileLockException | IOException ignored) {
                // Fall through to retry / timeout check
            }
            try {
                channel.close();
            } catch (IOException ignored) {
            }

            if (System.currentTimeMillis() >= deadline) {
                throw new IOException("Failed to acquire download lock at " + lockFile
                    + " within " + timeoutMillis + " ms; another instance may be downloading");
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for download lock", e);
            }
        }
    }

    // verifies sha-256, expects lowercase hex, warns and returns when expectedHex
    // is null or blank (the latter happens when an upstream catalog returns an
    // empty checksum field rather than omitting it)
    static void verifySha256(Path file, String expectedHex) throws IOException {
        if (expectedHex == null || expectedHex.trim().isEmpty()) {
            System.err.println("[Relauncher] No checksum provided for " + file.getFileName()
                + "; integrity not verified");
            return;
        }
        var actual = computeSha256Hex(file);
        if (!actual.equalsIgnoreCase(expectedHex.trim())) {
            throw new IOException("Checksum mismatch for " + file.getFileName()
                + ": expected " + expectedHex + ", got " + actual);
        }
    }

    // sha-256 as lowercase hex
    static String computeSha256Hex(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 not available in this JVM", e);
        }

        try (var in = Files.newInputStream(file)) {
            var buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                digest.update(buf, 0, n);
            }
        }

        var out = digest.digest();
        var sb = new StringBuilder(out.length * 2);
        for (var b : out) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    // extracts a zip or tar.gz, strips the single top-level dir Foojay always
    // wraps things in, zip-slip safe (rejects entries that resolve outside destDir)
    static void extract(Path archive, String archiveType, Path destDir) throws IOException {
        Files.createDirectories(destDir);
        var normalizedDest = destDir.toAbsolutePath().normalize();
        var type = archiveType.toLowerCase(Locale.ROOT);

        if (type.equals("zip")) {
            extractZip(archive, normalizedDest);
        } else if (type.equals("tar.gz") || type.equals("tgz")) {
            extractTarGz(archive, normalizedDest);
        } else {
            throw new IOException("Unsupported archive type: " + archiveType);
        }
    }

    private static void extractZip(Path archive, Path destDir) throws IOException {
        try (var zin = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                var stripped = stripTopLevel(entry.getName());
                if (stripped == null) continue;
                var target = resolveSafe(destDir, stripped);
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    try (var out = Files.newOutputStream(target)) {
                        var buf = new byte[8192];
                        int n;
                        while ((n = zin.read(buf)) > 0) out.write(buf, 0, n);
                    }
                }
                zin.closeEntry();
            }
        }
    }

    private static void extractTarGz(Path archive, Path destDir) throws IOException {
        try (var gz = new GZIPInputStream(Files.newInputStream(archive));
             var in = new DataInputStream(gz)) {
            var header = new byte[512];
            while (true) {
                in.readFully(header);
                if (isAllZero(header)) break; // end-of-archive

                var name = readString(header, 0, 100);
                if (name.isEmpty()) break;
                var size = readOctal(header, 124, 12);
                var typeFlag = (char) (header[156] == 0 ? '0' : header[156]);

                // Skip non-regular entries (dirs we can create lazily; symlinks/special files skipped)
                if (typeFlag != '0') {
                    skipBytes(in, paddedSize(size));
                    continue;
                }

                var stripped = stripTopLevel(name);
                if (stripped == null) {
                    skipBytes(in, paddedSize(size));
                    continue;
                }
                var target = resolveSafe(destDir, stripped);
                Files.createDirectories(target.getParent());

                try (var out = Files.newOutputStream(target)) {
                    var remaining = size;
                    var buf = new byte[8192];
                    while (remaining > 0) {
                        var toRead = (int) Math.min(buf.length, remaining);
                        var got = in.read(buf, 0, toRead);
                        if (got < 0) throw new IOException("Unexpected EOF in tar entry " + stripped);
                        out.write(buf, 0, got);
                        remaining -= got;
                    }
                }
                // Apply to execute bit for entries under bin/ when on POSIX filesystems.
                // Mode parsing from the tar header is optional; bin/* chmod +x covers JDK needs.
                if (stripped.startsWith("bin/") || stripped.contains("/bin/")) {
                    try {
                        var perms =
                            PosixFilePermissions.fromString("rwxr-xr-x");
                        Files.setPosixFilePermissions(target, perms);
                    } catch (UnsupportedOperationException ignored) {
                    }
                }

                // Pad to 512-byte boundary
                var padding = paddedSize(size) - size;
                skipBytes(in, padding);
            }
        }
    }

    // strips the single top-level dir from an archive entry, null if the entry
    // is the top-level dir itself, rejects traversal / absolute paths first so
    // stripping can't hide them
    private static String stripTopLevel(String name) throws IOException {
        // Normalize separators
        name = name.replace('\\', '/');
        // Reject unsafe raw entry names before stripping can mask them.
        if (name.startsWith("/")) {
            throw new IOException("Unsafe archive entry path (absolute): " + name);
        }
        if (name.length() >= 2 && name.charAt(1) == ':') {
            throw new IOException("Unsafe archive entry path (drive letter): " + name);
        }
        // Any path segment equal to ".." is a traversal attempt.
        for (var seg : name.split("/", -1)) {
            if (seg.equals("..")) {
                throw new IOException("Unsafe archive entry path (traversal): " + name);
            }
        }
        var slash = name.indexOf('/');
        if (slash < 0) return null;      // top-level file or directory entry itself
        var rest = name.substring(slash + 1);
        if (rest.isEmpty()) return null; // "topdir/" directory marker
        return rest;
    }

    private static Path resolveSafe(Path destDir, String relative) throws IOException {
        if (relative.startsWith("/") || relative.startsWith("\\")
            || relative.contains("..")) {
            throw new IOException("Unsafe archive entry path (traversal): " + relative);
        }
        // Reject Windows drive letters
        if (relative.length() >= 2 && relative.charAt(1) == ':') {
            throw new IOException("Unsafe archive entry path (drive letter): " + relative);
        }
        var resolved = destDir.resolve(relative).normalize();
        if (!resolved.startsWith(destDir)) {
            throw new IOException("Unsafe archive entry path (escapes target): " + relative);
        }
        return resolved;
    }

    private static boolean isAllZero(byte[] buf) {
        for (var b : buf) if (b != 0) return false;
        return true;
    }

    private static String readString(byte[] h, int off, int len) {
        var end = off;
        while (end < off + len && h[end] != 0) end++;
        return new String(h, off, end - off);
    }

    private static long readOctal(byte[] h, int off, int len) {
        long v = 0;
        for (var i = off; i < off + len; i++) {
            var b = h[i];
            if (b == 0 || b == ' ') continue;
            if (b < '0' || b > '7') return 0;
            v = (v << 3) | (b - '0');
        }
        return v;
    }

    private static long paddedSize(long size) {
        return (size + 511) & ~511L;
    }

    private static void skipBytes(DataInputStream in, long n) throws IOException {
        var remaining = n;
        var buf = new byte[4096];
        while (remaining > 0) {
            var toRead = (int) Math.min(buf.length, remaining);
            var got = in.read(buf, 0, toRead);
            if (got < 0) break;
            remaining -= got;
        }
    }

    public enum ConsentDecision {GRANTED, DENIED}

    /**
     * Outcome of {@link #orchestrate}. Carries the install on success and a
     * status the caller can use to produce a precise error message.
     */
    public static final class Result {
        public enum Status {SUCCESS, NO_SPI, CONSENT_DENIED, NO_MATCHING_PACKAGE}

        private final JavaInstallation installation;
        private final Status status;

        private Result(JavaInstallation installation, Status status) {
            this.installation = installation;
            this.status = status;
        }

        public static Result success(JavaInstallation installation) {
            return new Result(installation, Status.SUCCESS);
        }

        public static Result noSpi() {
            return new Result(null, Status.NO_SPI);
        }

        public static Result consentDenied() {
            return new Result(null, Status.CONSENT_DENIED);
        }

        public static Result noMatchingPackage() {
            return new Result(null, Status.NO_MATCHING_PACKAGE);
        }

        public Status status() {
            return status;
        }

        /** Non-null only when {@link #status()} is {@link Status#SUCCESS}. */
        public JavaInstallation installation() {
            return installation;
        }
    }

    /** Yes/no prompt, mockable in tests */
    public interface ConsentPrompter {
        boolean promptYesNo();
    }

    /** Holds a file lock, closing releases the lock and the channel */
    public static final class LockHandle implements AutoCloseable {
        private final FileChannel channel;
        private final FileLock lock;

        LockHandle(FileChannel channel, FileLock lock) {
            this.channel = channel;
            this.lock = lock;
        }

        @Override
        public void close() {
            try {
                lock.release();
            } catch (IOException ignored) {
            }
            try {
                channel.close();
            } catch (IOException ignored) {
            }
        }
    }
}
