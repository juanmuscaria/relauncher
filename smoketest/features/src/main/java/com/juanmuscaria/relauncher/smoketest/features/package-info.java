// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

/**
 * Feature harness. A plain-JVM {@code FeatureRunner} dispatches to one area
 * driver per feature group; each scenario writes a marker-file result. A
 * JUnit 5 wrapper forks subprocesses and surfaces markers as assertions.
 */
package com.juanmuscaria.relauncher.smoketest.features;
