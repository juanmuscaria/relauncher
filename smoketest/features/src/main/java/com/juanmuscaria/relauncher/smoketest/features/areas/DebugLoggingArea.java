// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.smoketest.features.areas;

import com.juanmuscaria.relauncher.smoketest.features.AreaDriver;
import com.juanmuscaria.relauncher.smoketest.harness.Scenario;
import com.juanmuscaria.relauncher.smoketest.harness.ScenarioResult;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public final class DebugLoggingArea implements AreaDriver {
    private static long ms(long n) {
        return (System.nanoTime() - n) / 1_000_000;
    }

    @Override
    public String areaName() {
        return "DebugLoggingArea";
    }

    @Override
    public List<String> scenarioNames() {
        return Collections.singletonList("debug-markers-present");
    }

    @Override
    public ScenarioResult run(Scenario s, Path scratchRoot) {
        var start = System.nanoTime();
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(Paths.get(System.getProperty("java.home"), "bin",
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                    ? "java.exe" : "java").toString());
            cmd.add("-cp");
            cmd.add(System.getProperty("java.class.path"));
            cmd.add("-Drelauncher.debug=true");
            cmd.add("-Drelauncher.test.forceFallback=true");
            cmd.add(DebugLoggingPayload.class.getName());

            var p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            var out = new StringBuilder();
            try (var br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) out.append(line).append('\n');
            }
            p.waitFor();

            var log = out.toString();
            var wanted = new String[]{
                "[Relauncher Debug] Executable:",
                "[Relauncher Debug] ProcessHandle source:",
                "[Relauncher Debug] Original args",
                "[Relauncher Debug] Extra args",
                "[Relauncher Debug] Final command"
            };
            List<String> missing = new ArrayList<>();
            for (var w : wanted) if (!log.contains(w)) missing.add(w);

            Map<String, Object> cap = new LinkedHashMap<>();
            cap.put("missing", missing);
            cap.put("logBytes", log.length());
            if (missing.isEmpty()) return ScenarioResult.pass(s, cap, ms(start));
            return ScenarioResult.fail(s, "missing debug markers: " + missing, cap, ms(start));
        } catch (Throwable t) {
            return ScenarioResult.fail(s, "threw: " + t, ms(start));
        }
    }
}
