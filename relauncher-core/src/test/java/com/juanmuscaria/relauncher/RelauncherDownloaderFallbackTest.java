// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher;

import com.juanmuscaria.relauncher.jvm.JavaRequirement;
import com.juanmuscaria.relauncher.jvm.JavaRuntimeProvider;
import com.juanmuscaria.relauncher.jvm.JvmVersion;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RelauncherDownloaderFallbackTest {

    /**
     * Smoke test: when requirements match the current JVM, the downloader path
     * is never reached. Spec A+B's existing short-circuit handles it.
     * This confirms we didn't regress the happy path.
     */
    @Test
    void currentJvmSatisfyStillShortCircuits() {
        var currentMajor = JvmVersion.fromSystemProperties().getMajor();
        JavaRuntimeProvider p = () -> Collections.singletonList(JavaRequirement.majors(currentMajor));
        var r = Relauncher.relaunch(
            Collections.<CommandLineProvider>emptyList(),
            Collections.singletonList(p));
        assertTrue(r.isSkipped());
    }
}
