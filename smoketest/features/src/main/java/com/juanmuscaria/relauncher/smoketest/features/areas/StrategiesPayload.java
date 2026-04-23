// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.smoketest.features.areas;

import com.juanmuscaria.relauncher.CommandLineProvider;
import com.juanmuscaria.relauncher.Relauncher;

import java.util.Collections;
import java.util.List;

public final class StrategiesPayload {
    public static void main(String[] args) {
        if (Boolean.getBoolean("relauncher.test.strategies.child")) {
            System.exit(0);
            return;
        }
        CommandLineProvider provider = () -> Collections.singletonList("-Drelauncher.test.strategies.child=true");
        Relauncher.relaunch(Collections.singletonList(provider));
        System.exit(0);
    }
}
