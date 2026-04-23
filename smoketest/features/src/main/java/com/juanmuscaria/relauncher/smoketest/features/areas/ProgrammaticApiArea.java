// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.smoketest.features.areas;

import com.juanmuscaria.relauncher.CommandLineProvider;
import com.juanmuscaria.relauncher.RelaunchResult;
import com.juanmuscaria.relauncher.Relauncher;
import com.juanmuscaria.relauncher.smoketest.features.AreaDriver;
import com.juanmuscaria.relauncher.smoketest.harness.Scenario;
import com.juanmuscaria.relauncher.smoketest.harness.ScenarioResult;

import java.nio.file.Path;
import java.util.*;

public final class ProgrammaticApiArea implements AreaDriver {
    private static long ms(long n) {
        return (System.nanoTime() - n) / 1_000_000;
    }

    private static Map<String, Object> capture(String k, Object v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k, v);
        return m;
    }

    @Override
    public String areaName() {
        return "ProgrammaticApiArea";
    }

    @Override
    public List<String> scenarioNames() {
        return Arrays.asList("empty-args-skips", "no-op-providers-skip", "relaunchResult-shape");
    }

    @Override
    public ScenarioResult run(Scenario s, Path scratchRoot) {
        var start = System.nanoTime();
        switch (s.name()) {
            case "empty-args-skips": {
                // Use the Collection<CommandLineProvider> overload, that is the path
                // that checks for "nothing to change" and returns skipped.
                // The List<String> overload calls relaunchInternal directly and would
                // attempt an actual relaunch even when the list is empty.
                var r = Relauncher.relaunch(Collections.<CommandLineProvider>emptyList());
                return r.isSkipped()
                    ? ScenarioResult.pass(s, capture("reason", r.reason()), ms(start))
                    : ScenarioResult.fail(s, "expected skipped, got: " + r, ms(start));
            }
            case "no-op-providers-skip": {
                CommandLineProvider empty = () -> Collections.emptyList();
                var r = Relauncher.relaunch(Arrays.asList(empty, empty));
                return r.isSkipped()
                    ? ScenarioResult.pass(s, capture("reason", r.reason()), ms(start))
                    : ScenarioResult.fail(s, "expected skipped, got: " + r, ms(start));
            }
            case "relaunchResult-shape": {
                var failed = RelaunchResult.failed("nope");
                var skipped = RelaunchResult.skipped("fine");
                var ok = failed.isFailed() && !failed.isSkipped()
                    && skipped.isSkipped() && !skipped.isFailed()
                    && "nope".equals(failed.reason()) && "fine".equals(skipped.reason());
                Map<String, Object> cap = new LinkedHashMap<>();
                cap.put("failedReason", failed.reason());
                cap.put("skippedReason", skipped.reason());
                return ok
                    ? ScenarioResult.pass(s, cap, ms(start))
                    : ScenarioResult.fail(s, "RelaunchResult shape wrong", cap, ms(start));
            }
            default:
                return ScenarioResult.fail(s, "unknown scenario", ms(start));
        }
    }
}
