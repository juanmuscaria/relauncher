// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.smoketest.features;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.stream.Stream;

/**
 * One test factory per feature area. Each forks {@code FeatureRunner}
 * and produces one {@link DynamicTest} per pre-declared scenario.
 * <p>
 * As areas are added in Phase 4, add a new factory method and register
 * its scenario list.
 */
class FeatureHarnessTest {

    @TestFactory
    Stream<DynamicTest> spiDiscovery() throws Exception {
        return AreaAssertions.runAreaAndAssert("SpiDiscoveryArea",
            List.of("classpath-only", "mods-folder-scan", "priority-ordering"));
    }

    @TestFactory
    Stream<DynamicTest> programmaticApi() throws Exception {
        return AreaAssertions.runAreaAndAssert("ProgrammaticApiArea",
            List.of("empty-args-skips", "no-op-providers-skip", "relaunchResult-shape"));
    }

    @TestFactory
    Stream<DynamicTest> depthAndLoop() throws Exception {
        return AreaAssertions.runAreaAndAssert("DepthAndLoopArea",
            List.of("depth-increments", "loop-guard-fires"));
    }

    @TestFactory
    Stream<DynamicTest> config() throws Exception {
        return AreaAssertions.runAreaAndAssert("ConfigArea",
            List.of("enabled-false-parsed-but-disabled",
                "extra-args-parsed",
                "java-versions-parsed",
                "java-vendors-parsed",
                "java-imageType-jdk",
                "java-autoDownload-true",
                "java-downloadDir-set"));
    }

    @TestFactory
    Stream<DynamicTest> duplicateProperty() throws Exception {
        return AreaAssertions.runAreaAndAssert("DuplicatePropertyArea",
            List.of("no-stacking-in-grandchild"));
    }

    @TestFactory
    Stream<DynamicTest> jvmMatching() throws Exception {
        return AreaAssertions.runAreaAndAssert("JvmMatchingArea",
            List.of("current-jvm-matches", "detects-other-installations", "conflicting-requirements-throws"));
    }

    @TestFactory
    Stream<DynamicTest> foojayDownload() throws Exception {
        return AreaAssertions.runAreaAndAssert("FoojayDownloadArea",
            List.of("download-jre21-succeeds"));
    }

    @TestFactory
    Stream<DynamicTest> debugLogging() throws Exception {
        return AreaAssertions.runAreaAndAssert("DebugLoggingArea",
            List.of("debug-markers-present"));
    }

    @TestFactory
    Stream<DynamicTest> strategies() throws Exception {
        return AreaAssertions.runAreaAndAssert("StrategiesArea",
            List.of("default-strategy", "forced-fallback", "disabled-exec"));
    }
}
