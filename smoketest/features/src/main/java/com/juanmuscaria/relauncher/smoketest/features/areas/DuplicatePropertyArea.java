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
import java.util.stream.Stream;

public final class DuplicatePropertyArea implements AreaDriver {
    private static int countOccurrences(String haystack, String needle) {
        int count = 0, idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    private static long ms(long n) {
        return (System.nanoTime() - n) / 1_000_000;
    }

    @Override
    public String areaName() {
        return "DuplicatePropertyArea";
    }

    @Override
    public List<String> scenarioNames() {
        return Collections.singletonList("no-stacking-in-grandchild");
    }

    @Override
    public ScenarioResult run(Scenario s, Path scratchRoot) {
        var start = System.nanoTime();
        try {
            var markerDir = scratchRoot.resolve("dup-marker");
            Files.createDirectories(markerDir);

            List<String> cmd = new ArrayList<>();
            cmd.add(Paths.get(System.getProperty("java.home"), "bin",
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                    ? "java.exe" : "java").toString());
            cmd.add("-cp");
            cmd.add(System.getProperty("java.class.path"));
            cmd.add("-Drelauncher.test.forceFallback=true");
            cmd.add("-Drelauncher.test.markerDir=" + markerDir.toAbsolutePath());
            cmd.add("-Drelauncher.didRelaunch=true");
            cmd.add("-Drelauncher.depth=1");
            cmd.add(DuplicatePropertyPayload.class.getName());

            var p = new ProcessBuilder(cmd).inheritIO().start();
            var exit = p.waitFor();

            // Read the core-instrumentation marker.
            var instr = markerDir.resolve("core-instrumentation");
            if (!Files.isDirectory(instr)) {
                return ScenarioResult.fail(s, "no core-instrumentation marker directory; exit=" + exit, ms(start));
            }
            String finalCommand;
            try (var list = Files.list(instr)) {
                var any = list.filter(Files::isRegularFile).findFirst().orElse(null);
                if (any == null) return ScenarioResult.fail(s, "instrumentation dir empty", ms(start));
                var text = new String(Files.readAllBytes(any));
                // Extract only the finalCommand JSON array so we count occurrences
                // in the assembled command, not in the extraArgs field.
                var fc = text.indexOf("\"finalCommand\":");
                if (fc < 0) return ScenarioResult.fail(s, "no finalCommand field in marker", ms(start));
                finalCommand = text.substring(fc);
            }
            var didCount = countOccurrences(finalCommand, "-Drelauncher.didRelaunch=");
            var depthCount = countOccurrences(finalCommand, "-Drelauncher.depth=");
            Map<String, Object> cap = new LinkedHashMap<>();
            cap.put("didCount", didCount);
            cap.put("depthCount", depthCount);
            if (didCount == 1 && depthCount == 1) return ScenarioResult.pass(s, cap, ms(start));
            return ScenarioResult.fail(s, "expected exactly one of each prop; saw didCount=" + didCount
                + " depthCount=" + depthCount, cap, ms(start));
        } catch (Throwable t) {
            return ScenarioResult.fail(s, "threw: " + t, ms(start));
        }
    }
}
