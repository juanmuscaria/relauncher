// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.jvm;

import com.juanmuscaria.relauncher.launch.Platform;
import com.juanmuscaria.relauncher.launch.Side;
import com.juanmuscaria.relauncher.logger.LoggerAdapter;

/**
 * User-visible error surface for unrecoverable relaunch problems.
 * <p>
 * Always writes the message to the logger and {@code System.err}. When running
 * on the client and LWJGL3's {@code TinyFileDialogs} is available, also shows
 * a native blocking message box via reflection. Then calls {@code System.exit(1)}.
 */
public final class RelaunchErrorDialog {

    private RelaunchErrorDialog() {
    }

    /**
     * Display the error and terminate the JVM with exit code 1. Never returns.
     */
    public static void showAndExit(String title, String message, LoggerAdapter logger) {
        if (logger != null) {
            logger.error("[" + title + "] " + message);
        }
        System.err.println("[Relauncher] " + title);
        System.err.println(message);

        var isClient = false;
        try {
            isClient = Platform.current().side() == Side.CLIENT;
        } catch (Throwable ignored) {
            // No platform registered - treat as headless, skip dialog
        }

        if (isClient) {
            tryTinyFileDialog(title, message);
        }

        System.exit(1);
    }

    private static void tryTinyFileDialog(String title, String message) {
        try {
            var tfd = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
            tfd.getMethod("tinyfd_messageBox",
                    CharSequence.class, CharSequence.class,
                    CharSequence.class, CharSequence.class, boolean.class)
                .invoke(null, title, message, "ok", "error", true);
        } catch (Throwable ignored) {
            // LWJGL3 missing, loader rejected, or invocation failed - stderr+logger are enough
        }
    }
}
