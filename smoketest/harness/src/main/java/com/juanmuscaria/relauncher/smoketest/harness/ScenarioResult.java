// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.smoketest.harness;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable result of running one scenario.
 */
public final class ScenarioResult {
    private final Scenario scenario;
    private final Outcome outcome;
    private final String reason;
    private final Map<String, Object> captured;
    private final long durationMs;
    private ScenarioResult(Scenario scenario, Outcome outcome, String reason,
                           Map<String, Object> captured, long durationMs) {
        this.scenario = scenario;
        this.outcome = outcome;
        this.reason = reason == null ? "" : reason;
        this.captured = captured == null ? new LinkedHashMap<>() : new LinkedHashMap<>(captured);
        this.durationMs = durationMs;
    }

    public static ScenarioResult pass(Scenario s, long durationMs) {
        return new ScenarioResult(s, Outcome.PASS, "", null, durationMs);
    }

    public static ScenarioResult pass(Scenario s, Map<String, Object> captured, long durationMs) {
        return new ScenarioResult(s, Outcome.PASS, "", captured, durationMs);
    }

    public static ScenarioResult fail(Scenario s, String reason, long durationMs) {
        return new ScenarioResult(s, Outcome.FAIL, reason, null, durationMs);
    }

    public static ScenarioResult fail(Scenario s, String reason, Map<String, Object> captured, long durationMs) {
        return new ScenarioResult(s, Outcome.FAIL, reason, captured, durationMs);
    }

    public static ScenarioResult skipped(Scenario s, String reason, long durationMs) {
        return new ScenarioResult(s, Outcome.SKIPPED, reason, null, durationMs);
    }

    public Scenario scenario() {
        return scenario;
    }

    public Outcome outcome() {
        return outcome;
    }

    public String reason() {
        return reason;
    }

    public Map<String, Object> captured() {
        return Collections.unmodifiableMap(captured);
    }

    public long durationMs() {
        return durationMs;
    }

    public enum Outcome {PASS, FAIL, SKIPPED}
}
