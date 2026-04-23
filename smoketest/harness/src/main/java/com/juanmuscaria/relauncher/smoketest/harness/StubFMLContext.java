// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.smoketest.harness;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.VersionInfo;
import net.neoforged.neoforgespi.ILaunchContext;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Minimal {@link ILaunchContext} for invoking FML's candidate-locator phase
 * without a real game module. Discovery item: if FML hard-requires a
 * Minecraft module to even enter candidate location, this stub isn't enough
 * and the affected combo should switch to {@code useRealGradlePlugin = 'neogradle'}.
 *
 * <p>{@link VersionInfo} is a record with three fields:
 * {@code neoForgeVersion}, {@code mcVersion}, {@code neoFormVersion}.
 * All three are passed as {@code "0.0.0"} here.
 */
public final class StubFMLContext implements ILaunchContext {

    private final Set<Path> located = new HashSet<>();
    private final Path gameDir;

    public StubFMLContext() {
        this(Paths.get(System.getProperty("user.dir", ".")));
    }

    public StubFMLContext(Path gameDir) {
        this.gameDir = gameDir;
    }

    @Override
    public Dist getRequiredDistribution() {
        return Dist.DEDICATED_SERVER;
    }

    @Override
    public Path gameDirectory() {
        return gameDir;
    }

    @Override
    public <T> Stream<ServiceLoader.Provider<T>> loadServices(Class<T> serviceClass) {
        return Stream.empty();
    }

    @Override
    public boolean isLocated(Path path) {
        return located.contains(path);
    }

    @Override
    public boolean addLocated(Path path) {
        return located.add(path);
    }

    @Override
    public VersionInfo getVersions() {
        // VersionInfo is a record: VersionInfo(String neoForgeVersion, String mcVersion, String neoFormVersion)
        return new VersionInfo("0.0.0", "0.0.0", "0.0.0");
    }
}
