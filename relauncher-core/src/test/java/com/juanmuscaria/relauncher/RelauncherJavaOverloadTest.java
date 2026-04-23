// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher;

import com.juanmuscaria.relauncher.jvm.JavaRequirement;
import com.juanmuscaria.relauncher.jvm.JavaRuntimeProvider;
import com.juanmuscaria.relauncher.jvm.JvmVersion;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelauncherJavaOverloadTest {

    @Test
    void skipsWhenNoArgsAndCurrentJvmSatisfies() {
        var currentMajor = JvmVersion.fromSystemProperties().getMajor();
        JavaRuntimeProvider provider = () -> Collections.singletonList(JavaRequirement.majors(currentMajor));

        var result = Relauncher.relaunch(
            Collections.<CommandLineProvider>emptyList(),
            Collections.singletonList(provider));

        assertTrue(result.isSkipped(), () -> "expected skipped, got " + result);
        assertNotNull(result.reason());
    }

    @Test
    void skipsWhenBothProviderListsAreEmpty() {
        var result = Relauncher.relaunch(
            Collections.<CommandLineProvider>emptyList(),
            Collections.<JavaRuntimeProvider>emptyList());
        assertTrue(result.isSkipped());
    }
}
