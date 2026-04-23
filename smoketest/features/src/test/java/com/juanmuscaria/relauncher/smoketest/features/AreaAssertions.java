// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.smoketest.features;

import com.juanmuscaria.relauncher.smoketest.harness.MarkerFile;
import com.juanmuscaria.relauncher.smoketest.harness.Scenario;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

final class AreaAssertions {
    private AreaAssertions() {
    }

    /**
     * Forks {@code FeatureRunner <area>} as a subprocess, then produces one
     * dynamic JUnit test per pre-declared scenario. Missing markers = fail.
     */
    static Stream<DynamicTest> runAreaAndAssert(String areaClassName, List<String> scenarioNames) throws Exception {
        var runId = Long.toString(System.currentTimeMillis());
        var markerDir = Paths.get("build", "smoketest", runId, areaClassName).toAbsolutePath();
        Files.createDirectories(markerDir);

        var classpath = System.getProperty("java.class.path");
        var javaHome = System.getProperty("java.home");
        var javaExe = Paths.get(javaHome, "bin", isWindows() ? "java.exe" : "java");

        List<String> cmd = new ArrayList<>();
        cmd.add(javaExe.toString());
        cmd.add("-Drelauncher.test.markerDir=" + markerDir.getParent().toAbsolutePath());
        cmd.add("-Drelauncher.test.runId=" + runId);
        // Propagate network gate
        var allow = System.getProperty("relauncher.test.allowNetwork", "false");
        cmd.add("-Drelauncher.test.allowNetwork=" + allow);
        cmd.add("-cp");
        cmd.add(classpath);
        cmd.add("com.juanmuscaria.relauncher.smoketest.features.FeatureRunner");
        cmd.add(areaClassName);

        var pb = new ProcessBuilder(cmd).inheritIO();
        var p = pb.start();
        var exit = p.waitFor();
        // We don't fail on non-zero exit here, individual
        // marker-level failures are surfaced as per-scenario DynamicTests.
        // But an exit of 2 means the runner never started an area.
        if (exit == 2) {
            fail("FeatureRunner failed to start area " + areaClassName);
        }

        return scenarioNames.stream().map(name ->
            dynamicTest(name, () -> assertScenario(markerDir, areaClassName, name)));
    }

    private static void assertScenario(Path markerDir, String area, String scenarioName) throws Exception {
        var marker = MarkerFile.resolve(markerDir.getParent(), new Scenario(area, scenarioName));
        if (!Files.exists(marker)) {
            fail("Missing marker file for " + area + "/" + scenarioName + ", scenario did not complete");
        }
        var summary = MarkerFile.readSummary(marker);
        var outcome = summary.get("outcome");
        var reason = summary.getOrDefault("reason", "");
        switch (outcome) {
            case "pass":
                break;
            case "skipped":
                Assumptions.abort(reason.isEmpty() ? "skipped" : reason);
                break;
            case "fail":
            default:
                fail(reason.isEmpty() ? "scenario failed" : reason);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
