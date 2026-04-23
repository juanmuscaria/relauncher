// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.smoketest.features.areas;

import com.juanmuscaria.relauncher.jvm.JavaInstallation;
import com.juanmuscaria.relauncher.jvm.JavaInstallationDetector;
import com.juanmuscaria.relauncher.jvm.JavaRequirement;
import com.juanmuscaria.relauncher.jvm.JvmVendor;
import com.juanmuscaria.relauncher.smoketest.features.AreaDriver;
import com.juanmuscaria.relauncher.smoketest.harness.Scenario;
import com.juanmuscaria.relauncher.smoketest.harness.ScenarioResult;

import java.nio.file.Path;
import java.util.*;

public final class JvmMatchingArea implements AreaDriver {
    private static Map<String, Object> capture(String k, Object v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k, v);
        return m;
    }

    private static long ms(long n) {
        return (System.nanoTime() - n) / 1_000_000;
    }

    @Override
    public String areaName() {
        return "JvmMatchingArea";
    }

    @Override
    public List<String> scenarioNames() {
        return Arrays.asList("current-jvm-matches",
            "detects-other-installations",
            "conflicting-requirements-throws");
    }

    @Override
    public ScenarioResult run(Scenario s, Path scratchRoot) {
        var start = System.nanoTime();
        try {
            switch (s.name()) {
                case "current-jvm-matches": {
                    var cur = JavaInstallationDetector.probeHome(
                        JavaInstallationDetector.currentJavaHome());
                    if (cur == null) return ScenarioResult.skipped(s, "couldn't probe current JVM", ms(start));
                    var req = JavaRequirement.of(
                        Collections.singletonList(cur.version().getMajor()),
                        Collections.<JvmVendor>emptyList());
                    var matches = req.matches(cur, System.getProperty("os.arch"));
                    return matches
                        ? ScenarioResult.pass(s, capture("major", cur.version().getMajor()), ms(start))
                        : ScenarioResult.fail(s, "current JVM didn't match trivially-scoped requirement", ms(start));
                }
                case "detects-other-installations": {
                    var detected = JavaInstallationDetector.detect();
                    Map<String, Object> cap = new LinkedHashMap<>();
                    cap.put("count", detected.size());
                    if (detected.size() == 0) {
                        return ScenarioResult.skipped(s, "no installations detected on this host", ms(start));
                    }
                    return ScenarioResult.pass(s, cap, ms(start));
                }
                case "conflicting-requirements-throws": {
                    var a = JavaRequirement.of(Collections.singletonList(8), Collections.<JvmVendor>emptyList());
                    var b = JavaRequirement.of(Collections.singletonList(21), Collections.<JvmVendor>emptyList());
                    try {
                        JavaRequirement.combine(Arrays.asList(a, b));
                        return ScenarioResult.fail(s, "expected UnsatisfiableException", ms(start));
                    } catch (JavaRequirement.UnsatisfiableException expected) {
                        return ScenarioResult.pass(s, capture("message", expected.getMessage()), ms(start));
                    }
                }
                default:
                    return ScenarioResult.fail(s, "unknown scenario", ms(start));
            }
        } catch (Throwable t) {
            return ScenarioResult.fail(s, "threw: " + t, ms(start));
        }
    }
}
