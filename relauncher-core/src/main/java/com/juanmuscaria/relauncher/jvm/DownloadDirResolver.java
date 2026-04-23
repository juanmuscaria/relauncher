// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.jvm;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Computes the default directory for Relauncher-managed downloaded JDKs.
 * <p>
 * Precedence:
 * <ol>
 *   <li>{@code -Drelauncher.java.downloadDir=/path/} system property.</li>
 *   <li>Platform convention ({@code $XDG_DATA_HOME/relauncher/jdks},
 *       {@code ~/Library/Application Support/relauncher/jdks},
 *       {@code %LOCALAPPDATA%\relauncher\jdks}).</li>
 * </ol>
 * Config-based overrides ({@code java.downloadDir} in {@code config.cfg}) are
 * NOT visible here. The orchestrator re-resolves with the config present.
 */
public final class DownloadDirResolver {

    private DownloadDirResolver() {
    }

    /**
     * Resolve the default download directory based on system property + platform conventions.
     * Always returns a non-null path. Does <b>not</b> create the directory.
     */
    public static Path resolve() {
        var sysProp = System.getProperty("relauncher.java.downloadDir");
        if (sysProp != null && !sysProp.trim().isEmpty()) {
            return Paths.get(sysProp.trim());
        }
        return platformDefault();
    }

    private static Path platformDefault() {
        var os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        var userHome = System.getProperty("user.home", "");

        if (os.contains("win")) {
            var localAppData = System.getenv("LOCALAPPDATA");
            var base = (localAppData != null && !localAppData.isEmpty())
                ? Paths.get(localAppData)
                : Paths.get(userHome, "AppData", "Local");
            return base.resolve("relauncher").resolve("jdks");
        }

        if (os.contains("mac")) {
            return Paths.get(userHome, "Library", "Application Support", "relauncher", "jdks");
        }

        // Linux / other Unix: XDG_DATA_HOME with fallback to ~/.local/share
        var xdg = System.getenv("XDG_DATA_HOME");
        var base = (xdg != null && !xdg.isEmpty())
            ? Paths.get(xdg)
            : Paths.get(userHome, ".local", "share");
        return base.resolve("relauncher").resolve("jdks");
    }
}
