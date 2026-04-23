// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.smoketest.harness;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

/**
 * The "game main class" returned by our stub launch handlers / game providers.
 * Writes a marker saying "reached the launch target" and exits.
 */
public final class StubLaunchTarget {
    public static void main(String[] args) {
        try {
            var baseDir = System.getProperty("relauncher.test.markerDir");
            if (baseDir != null) {
                var base = Paths.get(baseDir, "bootstrap");
                Files.createDirectories(base);
                var f = base.resolve("reached-launch-target-" + ProcessHandle.current().pid() + ".txt");
                Files.write(f, ("reached at " + Instant.now()
                    + "\nargs=" + String.join(" ", args) + "\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable ignored) {
            // Best-effort, don't fail the harness if marker write breaks
        }
    }
}
