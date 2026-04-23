// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher;

import java.util.List;
import java.util.ServiceLoader;

/**
 * Implement to inject extra JVM args during a relaunch. Discovered via {@link ServiceLoader},
 * scanned from the mods folder even before the loader sets up the classpath.
 */
public interface CommandLineProvider {

    /** Extra JVM args, prepended to the original args, ordered by {@link #priority()}. */
    List<String> extraJvmArguments();

    /** Lower runs first */
    default int priority() {
        return 0;
    }
}
