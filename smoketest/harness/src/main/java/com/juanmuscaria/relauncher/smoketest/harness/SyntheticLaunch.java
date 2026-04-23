// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.smoketest.harness;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Spawns a JVM with the given classpath + entry point. Captures stdout/stderr
 * to the marker directory for post-mortem, returns the exit code.
 */
public final class SyntheticLaunch {
    private SyntheticLaunch() {
    }

    public static int run(List<Path> classpath, String mainClass, List<String> jvmArgs,
                          List<String> appArgs, Path captureDir) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add(Paths.get(System.getProperty("java.home"), "bin",
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                ? "java.exe" : "java").toString());
        cmd.addAll(jvmArgs);
        cmd.add("-cp");
        cmd.add(joinClasspath(classpath));
        cmd.add(mainClass);
        cmd.addAll(appArgs);

        Files.createDirectories(captureDir);
        var stdout = captureDir.resolve("stdout.log");
        var stderr = captureDir.resolve("stderr.log");
        var cmdFile = captureDir.resolve("command.txt");
        Files.write(cmdFile, String.join("\n", cmd).getBytes());

        var p = new ProcessBuilder(cmd)
            .redirectOutput(stdout.toFile())
            .redirectError(stderr.toFile())
            .start();
        return p.waitFor();
    }

    private static String joinClasspath(List<Path> cp) {
        var sb = new StringBuilder();
        var sep = File.pathSeparator;
        for (var i = 0; i < cp.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(cp.get(i).toString());
        }
        return sb.toString();
    }
}
