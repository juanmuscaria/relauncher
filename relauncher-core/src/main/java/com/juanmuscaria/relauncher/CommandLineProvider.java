// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher;

import java.util.List;
import java.util.ServiceLoader;

/**
 * Implement this to inject extra JVM arguments during a relaunch.
 * <p>
 * Implementations are discovered via {@link ServiceLoader}. Just drop a
 * standard service file in your JAR and Relauncher will pick it up from
 * the mods folder, even before the mod loader adds your JAR to the classpath.
 */
public interface CommandLineProvider {

    /**
     * The extra JVM arguments to add. These go before the original arguments,
     * ordered by {@link #priority()} across all providers.
     */
    List<String> extraJvmArguments();

    /**
     * Controls ordering when multiple providers are present. Lower values run first.
     */
    default int priority() {
        return 0;
    }
}
