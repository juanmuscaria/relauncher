// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.jvm;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Implement this to provide a remote catalog of Java installations that
 * Relauncher can fall back to when local detection finds no match.
 * <p>
 * Discovered via {@link ServiceLoader}. Implementations live in optional
 * modules (e.g. {@code relauncher-foojay}) that are excluded from
 * CurseForge-distributed editions.
 * <p>
 * <b>Contract:</b> {@link #search} returns candidates (possibly empty).
 * Throws on transport/API errors. The orchestrator treats throw as "skip
 * this SPI, try the next". {@link #download} streams archive bytes to the
 * supplied path, invoking {@code progress} periodically, and honors
 * {@link Thread#isInterrupted()}.
 */
public interface JavaRuntimeDownloader {

    List<RemotePackage> search(JavaRequirement req, String arch) throws IOException;

    void download(RemotePackage pkg, Path targetFile, ProgressListener progress) throws IOException;

    /**
     * Lower values are tried first when multiple downloaders are registered. Default 0.
     */
    default int priority() {
        return 0;
    }
}
