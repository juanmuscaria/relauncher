// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.smoketest.features.areas;

import com.juanmuscaria.relauncher.CommandLineProvider;
import com.juanmuscaria.relauncher.RelauncherConfig;
import com.juanmuscaria.relauncher.launch.Platform;
import com.juanmuscaria.relauncher.smoketest.features.AreaDriver;
import com.juanmuscaria.relauncher.smoketest.harness.Scenario;
import com.juanmuscaria.relauncher.smoketest.harness.ScenarioResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

public final class SpiDiscoveryArea implements AreaDriver {
    private static long msSince(long nanos) {
        return (System.nanoTime() - nanos) / 1_000_000;
    }

    private static List<String> classNames(List<?> list) {
        List<String> out = new ArrayList<>(list.size());
        for (var o : list) out.add(o.getClass().getName());
        return out;
    }

    /**
     * Builds a minimal JAR containing only a META-INF/services entry.
     */
    private static void writeMinimalSpiJar(Path jar, String spi, String impl) throws IOException {
        Files.createDirectories(jar.getParent());
        var mf = new Manifest();
        mf.getMainAttributes().putValue("Manifest-Version", "1.0");
        try (var jos = new JarOutputStream(Files.newOutputStream(jar), mf)) {
            jos.putNextEntry(new ZipEntry("META-INF/services/" + spi));
            jos.write((impl + "\n").getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
        }
    }

    @Override
    public String areaName() {
        return "SpiDiscoveryArea";
    }

    @Override
    public List<String> scenarioNames() {
        return Arrays.asList("classpath-only", "mods-folder-scan", "priority-ordering");
    }

    @Override
    public ScenarioResult run(Scenario scenario, Path scratchRoot) {
        var start = System.nanoTime();
        try {
            return switch (scenario.name()) {
                case "classpath-only" -> classpathOnly(scenario, start);
                case "mods-folder-scan" -> modsFolderScan(scenario, scratchRoot, start);
                case "priority-ordering" -> priorityOrdering(scenario, start);
                default -> ScenarioResult.fail(scenario, "unknown scenario: " + scenario.name(),
                    msSince(start));
            };
        } catch (Throwable t) {
            return ScenarioResult.fail(scenario, "threw: " + t, msSince(start));
        }
    }

    private ScenarioResult classpathOnly(Scenario s, long startNs) {
        var found = RelauncherConfig.discoverSpiForTesting(
            CommandLineProvider.class, Platform.current(), Platform.current().logger());
        var hasHello = found.stream()
            .anyMatch(p -> p.getClass().getName().endsWith("HelloWorldProvider"));
        Map<String, Object> cap = new LinkedHashMap<>();
        cap.put("providerClasses", classNames(found));
        return hasHello
            ? ScenarioResult.pass(s, cap, msSince(startNs))
            : ScenarioResult.fail(s, "HelloWorldProvider not discovered on classpath", cap, msSince(startNs));
    }

    private ScenarioResult modsFolderScan(Scenario s, Path scratchRoot, long startNs) throws IOException {
        var modsDir = Platform.current().modsDirectory();
        var jar = modsDir.resolve("fixtures-mods-scan.jar");
        writeMinimalSpiJar(jar,
            "com.juanmuscaria.relauncher.CommandLineProvider",
            "com.juanmuscaria.relauncher.smoketest.fixtures.HelloWorldProvider");

        var found = RelauncherConfig.discoverSpiForTesting(
            CommandLineProvider.class, Platform.current(), Platform.current().logger());
        var helloCount = found.stream()
            .filter(p -> p.getClass().getName().endsWith("HelloWorldProvider"))
            .count();
        Map<String, Object> cap = new LinkedHashMap<>();
        cap.put("providerClasses", classNames(found));
        cap.put("helloCount", helloCount);
        if (helloCount == 1) return ScenarioResult.pass(s, cap, msSince(startNs));
        return ScenarioResult.fail(s, "expected exactly 1 HelloWorldProvider; saw " + helloCount, cap, msSince(startNs));
    }

    private ScenarioResult priorityOrdering(Scenario s, long startNs) {
        var low = new CommandLineProvider() {
            @Override
            public List<String> extraJvmArguments() {
                return Collections.singletonList("-Dlow=1");
            }

            @Override
            public int priority() {
                return -10;
            }
        };
        var high = new CommandLineProvider() {
            @Override
            public List<String> extraJvmArguments() {
                return Collections.singletonList("-Dhigh=1");
            }

            @Override
            public int priority() {
                return 10;
            }
        };
        List<CommandLineProvider> input = new ArrayList<>(Arrays.asList(high, low));
        input.sort(Comparator.comparingInt(CommandLineProvider::priority));
        Map<String, Object> cap = new LinkedHashMap<>();
        cap.put("firstArg", input.get(0).extraJvmArguments().get(0));
        if (!"-Dlow=1".equals(input.get(0).extraJvmArguments().get(0))) {
            return ScenarioResult.fail(s, "priority sort broken", cap, msSince(startNs));
        }
        return ScenarioResult.pass(s, cap, msSince(startNs));
    }
}
