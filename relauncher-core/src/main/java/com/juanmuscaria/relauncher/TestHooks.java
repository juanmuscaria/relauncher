// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher;

/**
 * Visible-for-testing knobs that let the smoketest suite control the
 * relaunch-strategy selection and observe internal state without
 * shipping a second "test" JAR.
 * <p>
 * Not a public contract. These system properties may change or disappear
 * in any release. Production code must not read them.
 */
final class TestHooks {
    private TestHooks() {
    }

    /**
     * Skip POSIX exec AND Windows DLL; go straight to the Java fallback.
     */
    static boolean forceFallback() {
        return Boolean.getBoolean("relauncher.test.forceFallback");
    }

    /**
     * Skip POSIX exec only; keep Windows DLL on Windows.
     */
    static boolean disableNativeExec() {
        return Boolean.getBoolean("relauncher.test.disableNativeExec");
    }

    /**
     * Base directory where relauncher-core writes optional instrumentation
     * markers (final command line, chosen strategy, depth). {@code null}
     * when unset, no markers are written.
     */
    static String markerDir() {
        return System.getProperty("relauncher.test.markerDir");
    }
}
