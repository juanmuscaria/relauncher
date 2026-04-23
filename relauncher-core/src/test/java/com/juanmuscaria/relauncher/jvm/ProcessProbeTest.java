// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.jvm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProcessProbeTest {

    @Test
    void parsesModernJavaVersion() {
        // A real -XshowSettings:properties dump prints both java.version and
        // java.specification.version. We want the full JEP 223 string (patch + build).
        var stderr = String.join("\n",
            "Property settings:",
            "    java.specification.version = 17",
            "    java.version = 17.0.8",
            "    java.vendor = Eclipse Adoptium",
            "    os.arch = amd64",
            "    os.name = Linux",
            "",
            "openjdk version \"17.0.8\" 2023-07-18");
        var r = ProcessProbe.parseStderr(stderr);
        assertEquals("17.0.8", r.javaVersion);
        assertEquals("Eclipse Adoptium", r.implementor);
        assertEquals("amd64", r.osArch);
    }

    @Test
    void prefersJavaVersionOverSpecification() {
        var stderr = """
            Property settings:
                java.specification.version = 21
                java.version = 21.0.2+13
                java.vendor = Temurin
            """;
        var r = ProcessProbe.parseStderr(stderr);
        assertEquals("21.0.2+13", r.javaVersion);
    }

    @Test
    void fallsBackToSpecificationWhenJavaVersionMissing() {
        // Synthetic output where only the spec version is present (shouldn't happen with
        // -XshowSettings, but the fallback keeps the probe robust).
        var stderr = "Property settings:\n\tjava.specification.version = 21\n\tjava.vendor = Temurin\n";
        var r = ProcessProbe.parseStderr(stderr);
        assertEquals("21", r.javaVersion);
        assertEquals("Temurin", r.implementor);
    }

    @Test
    void handlesLegacyJava8Format() {
        // Java 8 prints java.version = 1.8.0_291 and java.specification.version = 1.8
        var stderr = """
            Property settings:
                java.specification.version = 1.8
                java.version = 1.8.0_291
                java.vendor = Oracle Corporation
            """;
        var r = ProcessProbe.parseStderr(stderr);
        assertEquals("1.8.0_291", r.javaVersion);
        assertEquals("Oracle Corporation", r.implementor);
    }

    @Test
    void returnsNullsForMissingData() {
        var r = ProcessProbe.parseStderr("some unrelated output\n");
        assertNull(r.javaVersion);
        assertNull(r.implementor);
        assertNull(r.osArch);
    }

    @Test
    void handlesNullInput() {
        var r = ProcessProbe.parseStderr(null);
        assertNull(r.javaVersion);
    }
}
