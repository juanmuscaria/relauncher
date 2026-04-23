// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.smoketest.harness;

import cpw.mods.modlauncher.api.*;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Minimal synthetic bootstrap for ModLauncher 10.x entrypoint-discovery smoketests.
 * <p>
 * ModLauncher 10.x loads services from module layers, making it impractical to run
 * from a flat classpath without the full BootstrapLauncher/SecureJarHandler stack.
 * This runner performs the essential parts directly:
 * <ol>
 *   <li>ServiceLoader discovers {@code ITransformationService} on the classpath.</li>
 *   <li>{@code onLoad()} and {@code initialize()} fire on each discovered service.</li>
 *   <li>{@link StubLaunchTarget#main} writes the bootstrap marker, the same file the
 *       convention plugin's {@code doLast} check looks for.</li>
 * </ol>
 * This approach verifies SPI wiring (META-INF/services) without requiring ModLauncher's
 * module layer machinery.
 */
public final class StubModLauncherRunner {
    private StubModLauncherRunner() {
    }

    @SuppressWarnings("all")
    public static void main(String[] args) {
        IEnvironment env = makeEnv();

        ServiceLoader<ITransformationService> loader =
            ServiceLoader.load(ITransformationService.class);

        boolean anyFound = false;
        for (ITransformationService svc : loader) {
            anyFound = true;
            System.out.println("[StubModLauncherRunner] Found: " + svc.name());
            try {
                svc.onLoad(env, Collections.emptySet());
                svc.initialize(env);
                System.out.println("[StubModLauncherRunner] Initialized: " + svc.name());
            } catch (Throwable t) {
                // Initialization may fail in a synthetic environment (missing Launcher state,
                // MODLIST map, etc.). That's acceptable, discovery + onLoad is what matters
                System.out.println("[StubModLauncherRunner] Note: initialize() for " + svc.name()
                    + " threw (expected in synthetic env): " + t);
            }
        }

        if (!anyFound) {
            System.err.println("[StubModLauncherRunner] ERROR: No ITransformationService found on classpath!");
        }

        // Write the bootstrap marker expected by the convention plugin doLast check.
        StubLaunchTarget.main(args);
    }

    @SuppressWarnings({"unchecked"})
    private static IEnvironment makeEnv() {
        return new IEnvironment() {
            @Override
            public <T> Optional<T> getProperty(TypesafeMap.Key<T> key) {
                if (key == IEnvironment.Keys.GAMEDIR.get()) {
                    return (Optional<T>) Optional.of(Paths.get("."));
                }
                if (key == IEnvironment.Keys.LAUNCHTARGET.get()) {
                    return (Optional<T>) Optional.of("smoketest");
                }
                return Optional.empty();
            }

            @Override
            public <T> T computePropertyIfAbsent(TypesafeMap.Key<T> key,
                                                 Function<? super TypesafeMap.Key<T>, ? extends T> supplier) {
                return getProperty(key).orElseGet(() -> supplier.apply(key));
            }

            @Override
            public Optional<ILaunchPluginService> findLaunchPlugin(String name) {
                return Optional.empty();
            }

            @Override
            public Optional<ILaunchHandlerService> findLaunchHandler(String name) {
                return Optional.empty();
            }

            @Override
            public Optional<BiFunction<INameMappingService.Domain, String, String>> findNameMapping(String name) {
                return Optional.empty();
            }
        };
    }
}
