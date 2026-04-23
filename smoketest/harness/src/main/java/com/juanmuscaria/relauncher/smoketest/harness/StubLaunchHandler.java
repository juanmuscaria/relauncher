// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.smoketest.harness;

import cpw.mods.modlauncher.api.ILaunchHandlerService;
import cpw.mods.modlauncher.api.ITransformingClassLoader;
import cpw.mods.modlauncher.api.ITransformingClassLoaderBuilder;

import java.util.concurrent.Callable;

/**
 * Minimal ModLauncher launch handler. Advertises a "smoketest" target whose
 * callable writes a marker and exits. Used by synthetic ModLauncher bootstrap.
 */
public final class StubLaunchHandler implements ILaunchHandlerService {
    @Override
    public String name() {
        return "smoketest";
    }

    @Override
    public void configureTransformationClassLoader(ITransformingClassLoaderBuilder builder) {
    }

    @Override
    public Callable<Void> launchService(String[] args, ITransformingClassLoader classLoader) {
        return () -> {
            StubLaunchTarget.main(args);
            return null;
        };
    }
}
