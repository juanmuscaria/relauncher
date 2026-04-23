// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.jvm;

import java.util.List;
import java.util.ServiceLoader;

/**
 * Implement this to contribute Java runtime requirements during a relaunch.
 * <p>
 * Discovered via {@link ServiceLoader} in the same way as
 * {@link com.juanmuscaria.relauncher.CommandLineProvider}: drop a service
 * file in your JAR and Relauncher will pick it up from the mods folder even
 * before the mod loader has added your JAR to the classpath.
 * <p>
 * All contributed requirements are intersected - a mod that accepts Java 17 or
 * 21 and another that accepts Java 21 or 25 will result in a combined
 * requirement for Java 21. Conflicting requirements (no overlap) abort the
 * launch with an error dialog listing the offending providers.
 */
public interface JavaRuntimeProvider {

    /**
     * One or more {@link JavaRequirement}s contributed by this provider.
     */
    List<JavaRequirement> javaRequirements();

    /**
     * Controls ordering when the error message for an unsatisfiable combination
     * lists sources. Lower values appear first. Doesn't affect the math.
     */
    default int priority() {
        return 0;
    }
}
