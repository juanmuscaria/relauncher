// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.smoketest.features.areas;

import com.juanmuscaria.relauncher.CommandLineProvider;
import com.juanmuscaria.relauncher.Relauncher;

import java.util.Collections;
import java.util.List;

/**
 * Invoked as a sub-subprocess by DuplicatePropertyArea.
 */
public final class DuplicatePropertyPayload {
    public static void main(String[] args) {
        // Grandchild: return immediately.
        if (Boolean.getBoolean("relauncher.test.duplicateProp.grandchild")) {
            System.exit(0);
            return;
        }
        // Use the CommandLineProvider form so that Relauncher adds
        // -Drelauncher.didRelaunch= and -Drelauncher.depth= to the extra args,
        // which is what the DuplicatePropertyArea no-stacking check verifies.
        CommandLineProvider provider = () -> Collections.singletonList(
            "-Drelauncher.test.duplicateProp.grandchild=true");
        Relauncher.relaunch((List<CommandLineProvider>) Collections.singletonList(provider));
        System.exit(0);
    }
}
