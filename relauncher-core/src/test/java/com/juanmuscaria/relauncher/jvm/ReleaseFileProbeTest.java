// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.jvm;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReleaseFileProbeTest {

    @Test
    void parsesJdk17Release() {
        var lines = Arrays.asList(
            "IMPLEMENTOR=\"Eclipse Adoptium\"",
            "IMPLEMENTOR_VERSION=\"Temurin-17.0.8+7\"",
            "JAVA_VERSION=\"17.0.8\"",
            "JAVA_VERSION_DATE=\"2023-07-18\"",
            "OS_ARCH=\"x86_64\"",
            "OS_NAME=\"Linux\"",
            "IMAGE_TYPE=\"JDK\""
        );
        var r = ReleaseFileProbe.parse(lines);
        assertEquals("17.0.8", r.javaVersion);
        assertEquals("Eclipse Adoptium", r.implementor);
        assertEquals("x86_64", r.osArch);
        assertEquals("JDK", r.imageType);
    }

    @Test
    void parsesJdk8Release() {
        var lines = Arrays.asList(
            "JAVA_VERSION=\"1.8.0_291\"",
            "OS_NAME=\"Linux\"",
            "OS_VERSION=\"2.6\"",
            "OS_ARCH=\"amd64\"",
            "SOURCE=\""
        );
        var r = ReleaseFileProbe.parse(lines);
        assertEquals("1.8.0_291", r.javaVersion);
        assertNull(r.implementor);
        assertEquals("amd64", r.osArch);
        assertNull(r.imageType);
    }

    @Test
    void stripsSurroundingQuotes() {
        var lines = List.of("IMPLEMENTOR=\"GraalVM Community\"");
        var r = ReleaseFileProbe.parse(lines);
        assertEquals("GraalVM Community", r.implementor);
    }

    @Test
    void handlesUnquotedValues() {
        var lines = List.of("JAVA_VERSION=21");
        var r = ReleaseFileProbe.parse(lines);
        assertEquals("21", r.javaVersion);
    }

    @Test
    void ignoresMalformedLines() {
        var lines = Arrays.asList(
            "not a key-value line",
            "",
            "# comment",
            "JAVA_VERSION=\"17\"",
            "=orphan"
        );
        var r = ReleaseFileProbe.parse(lines);
        assertEquals("17", r.javaVersion);
    }

    @Test
    void emptyInputReturnsEmptyResult() {
        var r = ReleaseFileProbe.parse(Collections.emptyList());
        assertNull(r.javaVersion);
        assertNull(r.implementor);
        assertNull(r.osArch);
        assertNull(r.imageType);
    }
}
