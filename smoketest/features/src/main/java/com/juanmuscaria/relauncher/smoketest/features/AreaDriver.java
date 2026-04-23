// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.smoketest.features;

import com.juanmuscaria.relauncher.smoketest.harness.Scenario;
import com.juanmuscaria.relauncher.smoketest.harness.ScenarioResult;

import java.nio.file.Path;
import java.util.List;

/**
 * A feature area: a named group of related scenarios that the feature
 * harness runs in a single JVM. Drivers pre-declare their scenario names
 * so the JUnit wrapper knows what to expect.
 */
public interface AreaDriver {
    /**
     * Area name, matches the class name by convention (e.g. {@code "SpiDiscoveryArea"}).
     */
    String areaName();

    /**
     * Pre-declared scenario names. Missing markers for declared names = fail.
     */
    List<String> scenarioNames();

    /**
     * Run one scenario. Return its result, caller writes the marker.
     */
    ScenarioResult run(Scenario scenario, Path scratchRoot);
}
