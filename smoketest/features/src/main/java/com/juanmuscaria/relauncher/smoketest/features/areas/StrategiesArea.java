// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.smoketest.features.areas;

import com.juanmuscaria.relauncher.smoketest.features.AreaDriver;
import com.juanmuscaria.relauncher.smoketest.harness.Scenario;
import com.juanmuscaria.relauncher.smoketest.harness.ScenarioResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public final class StrategiesArea implements AreaDriver {
    private static long ms(long n) {
        return (System.nanoTime() - n) / 1_000_000;
    }

    @Override
    public String areaName() {
        return "StrategiesArea";
    }

    @Override
    public List<String> scenarioNames() {
        return Arrays.asList("default-strategy", "forced-fallback", "disabled-exec");
    }

    @Override
    public ScenarioResult run(Scenario s, Path scratchRoot) {
        var start = System.nanoTime();
        try {
            switch (s.name()) {
                case "default-strategy": {
                    var os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
                    var expected = os.contains("win") ? "windows-dll" : "posix-exec";
                    return runAndAssertStrategy(s, scratchRoot, Collections.<String>emptyList(),
                        expected, start);
                }
                case "forced-fallback":
                    return runAndAssertStrategy(s, scratchRoot,
                        Collections.singletonList("-Drelauncher.test.forceFallback=true"),
                        "java-fallback", start);
                case "disabled-exec": {
                    var os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
                    var expected = os.contains("win") ? "windows-dll" : "java-fallback";
                    return runAndAssertStrategy(s, scratchRoot,
                        Collections.singletonList("-Drelauncher.test.disableNativeExec=true"),
                        expected, start);
                }
                default:
                    return ScenarioResult.fail(s, "unknown scenario", ms(start));
            }
        } catch (Throwable t) {
            return ScenarioResult.fail(s, "threw: " + t, ms(start));
        }
    }

    private ScenarioResult runAndAssertStrategy(Scenario s, Path scratchRoot,
                                                List<String> extraProps,
                                                String expectedStrategy,
                                                long startNs) throws Exception {
        var markerDir = Files.createTempDirectory(scratchRoot, "strat-" + s.name() + "-");
        List<String> cmd = new ArrayList<>();
        cmd.add(Paths.get(System.getProperty("java.home"), "bin",
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                ? "java.exe" : "java").toString());
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("-Drelauncher.test.markerDir=" + markerDir.toAbsolutePath());
        cmd.addAll(extraProps);
        cmd.add(StrategiesPayload.class.getName());

        var p = new ProcessBuilder(cmd).inheritIO().start();
        p.waitFor();

        var instr = markerDir.resolve("core-instrumentation");
        if (!Files.isDirectory(instr)) {
            return ScenarioResult.fail(s, "no instrumentation dir", ms(startNs));
        }
        var found = false;
        try (var stream = Files.list(instr)) {
            for (var f : (Iterable<Path>) stream::iterator) {
                var text = new String(Files.readAllBytes(f));
                if (text.contains("\"strategy\": \"" + expectedStrategy + "\"")) {
                    found = true;
                    break;
                }
            }
        }
        Map<String, Object> cap = new LinkedHashMap<>();
        cap.put("expectedStrategy", expectedStrategy);
        return found
            ? ScenarioResult.pass(s, cap, ms(startNs))
            : ScenarioResult.fail(s, "did not see " + expectedStrategy + " in any marker", cap, ms(startNs));
    }
}
