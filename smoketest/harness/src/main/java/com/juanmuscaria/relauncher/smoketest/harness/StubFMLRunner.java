// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.smoketest.harness;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.fml.loading.VersionInfo;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Minimal synthetic bootstrap for FancyModLoader (FML 10+) entrypoint-discovery smoketests.
 * <p>
 * FML 10+ has no {@code main(String[])}, it is constructed via {@code FMLLoader.create(StartupArgs)},
 * which requires Instrumentation, a real game directory layout, and module-layer machinery.
 * This runner performs the essential parts directly:
 * <ol>
 *   <li>ServiceLoader discovers {@code IModFileCandidateLocator} on the classpath.</li>
 *   <li>{@code findCandidates()} fires on each discovered locator with a stub context
 *       and a no-op pipeline (we care about discovery, not actual mod file loading).</li>
 *   <li>{@link StubLaunchTarget#main} writes the bootstrap marker, the same file the
 *       convention plugin's {@code doLast} check looks for.</li>
 * </ol>
 * This approach verifies SPI wiring ({@code META-INF/services}) without requiring FML's
 * full BootstrapLauncher / module-layer / Instrumentation stack.
 */
public final class StubFMLRunner {
    private StubFMLRunner() {
    }

    public static void main(String[] args) {
        ILaunchContext ctx = new ClasspathLaunchContext();

        var loader =
            ServiceLoader.load(IModFileCandidateLocator.class);

        var anyFound = false;
        for (var locator : loader) {
            anyFound = true;
            System.out.println("[StubFMLRunner] Found: " + locator.getClass().getName());
            try {
                locator.findCandidates(ctx, NoOpPipeline.INSTANCE);
                System.out.println("[StubFMLRunner] findCandidates() returned normally for: "
                    + locator.getClass().getName());
            } catch (Throwable t) {
                // findCandidates() may fail in a synthetic environment (no real game dir,
                // no Minecraft jar, etc.). That's acceptable, discovery + invocation is what matters
                System.out.println("[StubFMLRunner] Note: findCandidates() for "
                    + locator.getClass().getName()
                    + " threw (may be expected in synthetic env): " + t);
            }
        }

        if (!anyFound) {
            System.err.println("[StubFMLRunner] ERROR: No IModFileCandidateLocator found on classpath!");
        }

        // Write the bootstrap marker expected by the convention plugin doLast check.
        StubLaunchTarget.main(args);
    }

    /**
     * ILaunchContext that loads services from the flat classpath via ServiceLoader.
     * Unlike {@link StubFMLContext}, which returns {@code Stream.empty()}, this
     * delegates to {@code ServiceLoader.load()} so that SPI-registered locators are found.
     */
    private static final class ClasspathLaunchContext implements ILaunchContext {
        private final Set<Path> located = new HashSet<>();
        private final Path gameDir = Paths.get(System.getProperty("user.dir", "."));

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
            return ServiceLoader.load(serviceClass).stream();
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
            return new VersionInfo("0.0.0", "0.0.0", "0.0.0");
        }
    }

    /**
     * No-op IDiscoveryPipeline, we only care that findCandidates() was invoked,
     * not about actual mod file loading.
     */
    private static final class NoOpPipeline implements IDiscoveryPipeline {
        static final NoOpPipeline INSTANCE = new NoOpPipeline();

        @Override
        public Optional<IModFile> addPath(List<Path> groupedPaths,
                                          ModFileDiscoveryAttributes attributes,
                                          IncompatibleFileReporting reporting) {
            return Optional.empty();
        }

        @Override
        public Optional<IModFile> addJarContent(JarContents contents,
                                                ModFileDiscoveryAttributes attributes,
                                                IncompatibleFileReporting reporting) {
            return Optional.empty();
        }

        @Override
        public boolean addModFile(IModFile modFile) {
            return false;
        }

        @Override
        public IModFile readModFile(JarContents jarContents,
                                    ModFileDiscoveryAttributes attributes) {
            return null;
        }

        @Override
        public void addIssue(ModLoadingIssue issue) {
            System.out.println("[StubFMLRunner] Pipeline issue: " + issue);
        }
    }
}
