// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.smoketest.features.areas;

import com.juanmuscaria.relauncher.RelaunchResult;
import com.juanmuscaria.relauncher.Relauncher;
import com.juanmuscaria.relauncher.smoketest.features.AreaDriver;
import com.juanmuscaria.relauncher.smoketest.harness.Scenario;
import com.juanmuscaria.relauncher.smoketest.harness.ScenarioResult;

import java.nio.file.Path;
import java.util.*;

public final class DepthAndLoopArea implements AreaDriver {
    private static long ms(long n) {
        return (System.nanoTime() - n) / 1_000_000;
    }

    @Override
    public String areaName() {
        return "DepthAndLoopArea";
    }

    @Override
    public List<String> scenarioNames() {
        return Arrays.asList("depth-increments", "loop-guard-fires");
    }

    @Override
    public ScenarioResult run(Scenario s, Path scratchRoot) {
        var start = System.nanoTime();
        return switch (s.name()) {
            case "depth-increments" -> depthIncrements(s, start);
            case "loop-guard-fires" -> loopGuardFires(s, start);
            default -> ScenarioResult.fail(s, "unknown scenario", ms(start));
        };
    }

    private ScenarioResult depthIncrements(Scenario s, long startNs) {
        var relaunched = Relauncher.isRelaunched();
        var depth = Relauncher.getDepth();
        Map<String, Object> cap = new LinkedHashMap<>();
        cap.put("isRelaunched", relaunched);
        cap.put("depth", depth);
        if (relaunched || depth != 0) {
            return ScenarioResult.fail(s, "parent JVM saw didRelaunch/depth set", cap, ms(startNs));
        }
        return ScenarioResult.pass(s, cap, ms(startNs));
    }

    private ScenarioResult loopGuardFires(Scenario s, long startNs) {
        var prev = System.getProperty("relauncher.depth");
        System.setProperty("relauncher.depth", "10");
        try {
            var r = Relauncher.relaunch(Collections.singletonList("-Dfoo=bar"));
            Map<String, Object> cap = new LinkedHashMap<>();
            cap.put("reason", r.reason());
            cap.put("isFailed", r.isFailed());
            if (!r.isFailed() || !r.reason().contains("depth 10")) {
                return ScenarioResult.fail(s, "expected failed depth-10, got " + r, cap, ms(startNs));
            }
            return ScenarioResult.pass(s, cap, ms(startNs));
        } finally {
            if (prev == null) System.clearProperty("relauncher.depth");
            else System.setProperty("relauncher.depth", prev);
        }
    }
}
