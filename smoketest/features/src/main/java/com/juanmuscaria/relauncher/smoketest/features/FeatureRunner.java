// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.smoketest.features;

import com.juanmuscaria.relauncher.launch.Side;
import com.juanmuscaria.relauncher.smoketest.harness.MarkerFile;
import com.juanmuscaria.relauncher.smoketest.harness.Scenario;
import com.juanmuscaria.relauncher.smoketest.harness.ScenarioResult;
import com.juanmuscaria.relauncher.smoketest.harness.TestPlatform;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ServiceLoader;

/**
 * Single entry point for the feature harness. Invoked as:
 * <pre>
 *   java -cp ... FeatureRunner &lt;area&gt;
 * </pre>
 * Reads {@code relauncher.test.markerDir} for where to drop markers, finds
 * the matching {@link AreaDriver} via {@code ServiceLoader}, runs every
 * scenario, writes one marker per scenario.
 */
public final class FeatureRunner {
    private FeatureRunner() {
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: FeatureRunner <area>");
            System.exit(2);
        }
        var wantedArea = args[0];

        var markerDirProp = System.getProperty("relauncher.test.markerDir");
        if (markerDirProp == null) {
            System.err.println("relauncher.test.markerDir must be set");
            System.exit(2);
            return;
        }
        var markerDir = Paths.get(markerDirProp);

        AreaDriver driver = null;
        for (var d : ServiceLoader.load(AreaDriver.class)) {
            if (d.areaName().equals(wantedArea)) {
                driver = d;
                break;
            }
        }
        if (driver == null) {
            System.err.println("No AreaDriver registered for " + wantedArea);
            System.exit(2);
            return;
        }

        Path scratch;
        try {
            scratch = Files.createTempDirectory("smoketest-" + wantedArea + "-");
        } catch (Exception e) {
            System.err.println("Failed to create scratch dir: " + e);
            System.exit(2);
            return;
        }

        TestPlatform.install(
            scratch, Side.SERVER);

        var failures = 0;
        for (var scenarioName : driver.scenarioNames()) {
            var s = new Scenario(driver.areaName(), scenarioName);
            var start = System.nanoTime();
            ScenarioResult result;
            try {
                result = driver.run(s, scratch);
            } catch (Throwable t) {
                var dur = (System.nanoTime() - start) / 1_000_000;
                result = ScenarioResult.fail(s, "driver threw: " + t, dur);
            }
            try {
                MarkerFile.write(MarkerFile.resolve(markerDir, s), result);
            } catch (Exception e) {
                System.err.println("Failed to write marker for " + s.area() + "/" + s.name() + ": " + e);
                failures++;
            }
            if (result.outcome() == ScenarioResult.Outcome.FAIL) failures++;
        }

        // Exit 0 regardless, the JUnit wrapper decides pass/fail from
        // marker files, not from our exit code. We only exit non-zero
        // if we failed to *write* markers.
        System.exit(failures == 0 ? 0 : 1);
    }
}
