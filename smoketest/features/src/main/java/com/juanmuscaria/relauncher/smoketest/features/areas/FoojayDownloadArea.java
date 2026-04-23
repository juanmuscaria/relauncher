// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.smoketest.features.areas;

import com.juanmuscaria.relauncher.jvm.*;
import com.juanmuscaria.relauncher.smoketest.features.AreaDriver;
import com.juanmuscaria.relauncher.smoketest.harness.Scenario;
import com.juanmuscaria.relauncher.smoketest.harness.ScenarioResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class FoojayDownloadArea implements AreaDriver {
    private static long ms(long n) {
        return (System.nanoTime() - n) / 1_000_000;
    }

    @Override
    public String areaName() {
        return "FoojayDownloadArea";
    }

    @Override
    public List<String> scenarioNames() {
        return Collections.singletonList("download-jre21-succeeds");
    }

    @Override
    public ScenarioResult run(Scenario s, Path scratchRoot) {
        var start = System.nanoTime();
        if (!Boolean.getBoolean("relauncher.test.allowNetwork")) {
            return ScenarioResult.skipped(s, "network gate closed (set -Prelauncher.test.allowNetwork=true)", ms(start));
        }
        try {
            var downloadDir = Files.createTempDirectory(scratchRoot, "foojay-");
            var req = JavaRequirement.of(
                Collections.singletonList(21),
                Collections.singletonList(JvmVendor.TEMURIN));
            List<JavaRuntimeDownloader> downloaders = new ArrayList<>();
            for (var javaRuntimeDownloader : ServiceLoader.load(JavaRuntimeDownloader.class))
                downloaders.add(javaRuntimeDownloader);
            if (downloaders.isEmpty()) {
                return ScenarioResult.fail(s, "no JavaRuntimeDownloader SPI on classpath", ms(start));
            }
            var result = DownloadOrchestrator.orchestrate(
                req, System.getProperty("os.arch"),
                true, false, () -> true,
                downloadDir, downloaders);
            if (result.status() != DownloadOrchestrator.Result.Status.SUCCESS) {
                return ScenarioResult.fail(s, "orchestrator returned " + result.status(), ms(start));
            }
            var installed = result.installation();
            Map<String, Object> cap = new LinkedHashMap<>();
            cap.put("installedVersion", installed.version().toString());
            cap.put("home", installed.home().toString());
            return ScenarioResult.pass(s, cap, ms(start));
        } catch (Throwable t) {
            return ScenarioResult.fail(s, "threw: " + t, ms(start));
        }
    }
}
