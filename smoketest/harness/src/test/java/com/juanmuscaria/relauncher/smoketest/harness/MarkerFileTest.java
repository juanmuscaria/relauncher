// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.smoketest.harness;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MarkerFileTest {

    @Test
    void roundTripPass(@TempDir Path dir) throws Exception {
        var s = new Scenario("MyArea", "basic");
        var path = MarkerFile.resolve(dir, s);
        MarkerFile.write(path, ScenarioResult.pass(s, 42));

        assertTrue(Files.exists(path));
        var summary = MarkerFile.readSummary(path);
        assertEquals("MyArea", summary.get("area"));
        assertEquals("basic", summary.get("scenario"));
        assertEquals("pass", summary.get("outcome"));
        assertEquals("", summary.get("reason"));
    }

    @Test
    void roundTripFailWithCapturedMap(@TempDir Path dir) throws Exception {
        var s = new Scenario("Another", "complex");
        Map<String, Object> captured = new LinkedHashMap<>();
        captured.put("depth", 3);
        captured.put("extraArgs", Arrays.asList("-Dfoo=bar", "-Xmx1G"));
        captured.put("didRelaunch", true);

        var path = MarkerFile.resolve(dir, s);
        MarkerFile.write(path, ScenarioResult.fail(s, "something broke\nmultiline", captured, 100));

        var text = new String(Files.readAllBytes(path));
        assertTrue(text.contains("\"outcome\": \"fail\""));
        assertTrue(text.contains("\"depth\": 3"));
        assertTrue(text.contains("\"didRelaunch\": true"));
        assertTrue(text.contains("\"-Dfoo=bar\""));
        assertTrue(text.contains("something broke\\nmultiline"));

        var summary = MarkerFile.readSummary(path);
        assertEquals("fail", summary.get("outcome"));
        assertEquals("something broke\nmultiline", summary.get("reason"));
    }

    @Test
    void atomicWriteLeavesNoTempFile(@TempDir Path dir) throws Exception {
        var s = new Scenario("X", "y");
        var path = MarkerFile.resolve(dir, s);
        MarkerFile.write(path, ScenarioResult.pass(s, 1));

        try (var stream = Files.list(path.getParent())) {
            var tmpCount = stream.filter(p -> p.getFileName().toString().endsWith(".tmp")).count();
            assertEquals(0, tmpCount);
        }
    }

    @Test
    void scenarioRejectsBadInputs() {
        // null/empty area
        assertThrows(IllegalArgumentException.class, () -> new Scenario(null, "x"));
        assertThrows(IllegalArgumentException.class, () -> new Scenario("", "x"));
        // forward slash in area
        assertThrows(IllegalArgumentException.class, () -> new Scenario("a/b", "x"));
        // backslash in area
        assertThrows(IllegalArgumentException.class, () -> new Scenario("a\\b", "x"));

        // null/empty name
        assertThrows(IllegalArgumentException.class, () -> new Scenario("a", null));
        assertThrows(IllegalArgumentException.class, () -> new Scenario("a", ""));
        // forward slash in name
        assertThrows(IllegalArgumentException.class, () -> new Scenario("a", "x/y"));
        // backslash in name
        assertThrows(IllegalArgumentException.class, () -> new Scenario("a", "x\\y"));
    }

    @Test
    void skippedRoundTrip(@TempDir Path dir) throws Exception {
        var s = new Scenario("Area", "skipme");
        var path = MarkerFile.resolve(dir, s);
        MarkerFile.write(path, ScenarioResult.skipped(s, "some reason", 7));

        var summary = MarkerFile.readSummary(path);
        assertEquals("skipped", summary.get("outcome"));
        assertEquals("some reason", summary.get("reason"));
    }
}
