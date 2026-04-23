// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.smoketest.features.areas;

import com.juanmuscaria.relauncher.RelauncherConfig;
import com.juanmuscaria.relauncher.jvm.JvmImageType;
import com.juanmuscaria.relauncher.smoketest.features.AreaDriver;
import com.juanmuscaria.relauncher.smoketest.harness.Scenario;
import com.juanmuscaria.relauncher.smoketest.harness.ScenarioResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ConfigArea implements AreaDriver {
    private static void write(Path dir, String content) throws Exception {
        Files.write(dir.resolve("config.cfg"), content.getBytes(StandardCharsets.UTF_8));
    }

    private static ScenarioResult assertEquals(Scenario s, long startNs, Object expected, Object actual) {
        Map<String, Object> cap = new LinkedHashMap<>();
        cap.put("expected", String.valueOf(expected));
        cap.put("actual", String.valueOf(actual));
        if (expected.equals(actual)) return ScenarioResult.pass(s, cap, ms(startNs));
        return ScenarioResult.fail(s, "expected != actual", cap, ms(startNs));
    }

    private static ScenarioResult assertTrue(Scenario s, long startNs, boolean ok, String msg) {
        return ok ? ScenarioResult.pass(s, ms(startNs)) : ScenarioResult.fail(s, msg, ms(startNs));
    }

    private static long ms(long n) {
        return (System.nanoTime() - n) / 1_000_000;
    }

    @Override
    public String areaName() {
        return "ConfigArea";
    }

    @Override
    public List<String> scenarioNames() {
        return Arrays.asList(
            "enabled-false-parsed-but-disabled",
            "extra-args-parsed",
            "java-versions-parsed",
            "java-vendors-parsed",
            "java-imageType-jdk",
            "java-autoDownload-true",
            "java-downloadDir-set");
    }

    @Override
    public ScenarioResult run(Scenario s, Path scratchRoot) {
        var start = System.nanoTime();
        try {
            var cfgDir = Files.createTempDirectory(scratchRoot, "config-" + s.name() + "-");
            switch (s.name()) {
                case "enabled-false-parsed-but-disabled":
                    write(cfgDir, "enabled = false\n-Xmx1G\n");
                    var cfg = RelauncherConfig.load(cfgDir);
                    Map<String, Object> cap = new LinkedHashMap<>();
                    cap.put("enabled", cfg.isEnabled());
                    cap.put("extraArgs", cfg.getExtraJvmArgs());
                    if (cfg.isEnabled()) {
                        return ScenarioResult.fail(s, "expected isEnabled()=false", cap, ms(start));
                    }
                    if (!cfg.getExtraJvmArgs().contains("-Xmx1G")) {
                        return ScenarioResult.fail(s, "expected extraJvmArgs to contain -Xmx1G", cap, ms(start));
                    }
                    return ScenarioResult.pass(s, cap, ms(start));
                case "extra-args-parsed":
                    write(cfgDir, "enabled = true\n-XX:+UseG1GC\n-Dfoo=bar\n");
                    return assertEquals(s, start,
                        Arrays.asList("-XX:+UseG1GC", "-Dfoo=bar"),
                        RelauncherConfig.load(cfgDir).getExtraJvmArgs());
                case "java-versions-parsed":
                    write(cfgDir, "java.versions = 17, 21\n");
                    return assertEquals(s, start,
                        Arrays.asList(17, 21),
                        RelauncherConfig.load(cfgDir).getJavaVersions());
                case "java-vendors-parsed":
                    write(cfgDir, "java.versions = 17\njava.vendors = temurin, graalvm\n");
                    return assertTrue(s, start,
                        RelauncherConfig.load(cfgDir).getJavaVendors().size() == 2,
                        "expected 2 vendors");
                case "java-imageType-jdk":
                    write(cfgDir, "java.versions = 21\njava.imageType = jdk\n");
                    return assertEquals(s, start, JvmImageType.JDK,
                        RelauncherConfig.load(cfgDir).getJavaImageType());
                case "java-autoDownload-true":
                    write(cfgDir, "java.autoDownload = true\n");
                    return assertTrue(s, start,
                        RelauncherConfig.load(cfgDir).isJavaAutoDownload(),
                        "expected autoDownload=true");
                case "java-downloadDir-set":
                    write(cfgDir, "java.downloadDir = /tmp/rlx\n");
                    var downloadDir = RelauncherConfig.load(cfgDir).getJavaDownloadDir();
                    return assertTrue(s, start,
                        downloadDir != null && downloadDir.toString().endsWith("rlx"),
                        "expected /tmp/rlx, got " + downloadDir);
                default:
                    return ScenarioResult.fail(s, "unknown scenario", ms(start));
            }
        } catch (Throwable t) {
            return ScenarioResult.fail(s, "threw: " + t, ms(start));
        }
    }
}
