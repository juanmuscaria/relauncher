// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.smoketest.harness;

import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.game.patch.GameTransformer;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.util.Arguments;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Minimal {@link GameProvider} that declines all intermediary mappings /
 * transformers and advertises {@link StubLaunchTarget} as the entry point.
 * Enough for Fabric Loader's mod-discovery phase to run and fire our
 * {@code PreLaunchEntrypoint}.
 */
public final class SmoketestGameProvider implements GameProvider {
    // Single instance so initialize() calls locateEntrypoints() on the same
    // object that KnotClassDelegate holds, initialising patchedClasses.
    private final GameTransformer transformer = new GameTransformer();
    private Arguments arguments;

    @Override
    public String getGameId() {
        return "minecraft";
    }

    @Override
    public String getGameName() {
        return "Relauncher Smoketest Stub";
    }

    @Override
    public String getRawGameVersion() {
        return "1.0-smoketest";
    }

    @Override
    public String getNormalizedGameVersion() {
        return "1.0";
    }

    @Override
    public Collection<BuiltinMod> getBuiltinMods() {
        return Collections.emptyList();
    }

    @Override
    public String getEntrypoint() {
        return StubLaunchTarget.class.getName();
    }

    @Override
    public Path getLaunchDirectory() {
        return Path.of(".");
    }

    @Override
    public boolean isObfuscated() {
        return false;
    }

    @Override
    public boolean requiresUrlClassLoader() {
        return false;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean locateGame(FabricLauncher launcher, String[] args) {
        this.arguments = new Arguments();
        this.arguments.parse(args);
        return true;
    }

    @Override
    public void initialize(FabricLauncher launcher) {
        // locateEntrypoints must be called to initialise GameTransformer's internal
        // patchedClasses map; pass an empty list, we have no game JARs to patch
        getEntrypointTransformer().locateEntrypoints(launcher, List.of());
    }

    @Override
    public GameTransformer getEntrypointTransformer() {
        return transformer;
    }

    @Override
    public void unlockClassPath(FabricLauncher launcher) {
        // Expose every classpath entry that isn't the fabric-loader itself so that
        // StubLaunchTarget and the rest of the harness/relauncher JARs are visible
        // to the Knot class loader when launch() is called.
        for (var p : launcher.getClassPath()) {
            var name = p.getFileName() == null ? "" : p.getFileName().toString();
            // Skip the loader jar itself, it manages its own visibility
            if (name.startsWith("fabric-loader")) continue;
            launcher.addToClassPath(p);
        }
    }

    @Override
    public void launch(ClassLoader loader) {
        try {
            var target = Class.forName(StubLaunchTarget.class.getName(), true, loader);
            target.getMethod("main", String[].class).invoke(null, (Object) arguments.toArray());
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @Override
    public Arguments getArguments() {
        return arguments;
    }

    @Override
    public String[] getLaunchArguments(boolean sanitize) {
        return arguments == null ? new String[0] : arguments.toArray();
    }

    @Override
    public boolean canOpenErrorGui() {
        return false;
    }

    @Override
    public boolean hasAwtSupport() {
        return false;
    }
}
