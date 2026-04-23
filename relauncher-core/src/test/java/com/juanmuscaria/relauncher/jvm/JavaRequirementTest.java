// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.jvm;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JavaRequirementTest {

    private static JavaInstallation fake(JvmVersion v, JvmVendor vendor, JvmImageType img, String arch) {
        return new JavaInstallation(
            Paths.get("/fake"), Paths.get("/fake/bin/java"),
            v, vendor, img, arch);
    }

    @Test
    void majorsFactoryRejectsEmpty() {
        assertThrows(IllegalArgumentException.class, JavaRequirement::majors);
    }

    @Test
    void majorsFactoryBuildsSimple() {
        var r = JavaRequirement.majors(17, 21);
        assertEquals(Arrays.asList(17, 21), r.acceptableMajors());
        assertTrue(r.acceptableVendors().isEmpty());
        assertEquals(JvmImageType.JRE, r.minImageType());
    }

    @Test
    void combineIntersectsMajors() {
        var a = JavaRequirement.majors(17, 21);
        var b = JavaRequirement.majors(21, 25);
        var combined = JavaRequirement.combine(Arrays.asList(a, b));
        assertEquals(Collections.singletonList(21), combined.acceptableMajors());
    }

    @Test
    void combineFailsOnEmptyMajorIntersection() {
        var a = JavaRequirement.majors(17);
        var b = JavaRequirement.majors(21);
        assertThrows(JavaRequirement.UnsatisfiableException.class,
            () -> JavaRequirement.combine(Arrays.asList(a, b)));
    }

    @Test
    void combineIntersectsVendors() {
        var a = JavaRequirement.of(
            List.of(17), Arrays.asList(JvmVendor.TEMURIN, JvmVendor.GRAALVM), JvmImageType.JRE);
        var b = JavaRequirement.of(
            List.of(17), List.of(JvmVendor.TEMURIN), JvmImageType.JRE);
        var combined = JavaRequirement.combine(Arrays.asList(a, b));
        assertEquals(Collections.singletonList(JvmVendor.TEMURIN), combined.acceptableVendors());
    }

    @Test
    void combineFailsOnEmptyVendorIntersection() {
        var a = JavaRequirement.of(
            List.of(17), List.of(JvmVendor.TEMURIN), JvmImageType.JRE);
        var b = JavaRequirement.of(
            List.of(17), List.of(JvmVendor.GRAALVM), JvmImageType.JRE);
        assertThrows(JavaRequirement.UnsatisfiableException.class,
            () -> JavaRequirement.combine(Arrays.asList(a, b)));
    }

    @Test
    void emptyVendorListDoesNotConstrain() {
        var a = JavaRequirement.majors(17); // empty vendors = any
        var b = JavaRequirement.of(
            List.of(17), List.of(JvmVendor.GRAALVM), JvmImageType.JRE);
        var combined = JavaRequirement.combine(Arrays.asList(a, b));
        assertEquals(Collections.singletonList(JvmVendor.GRAALVM), combined.acceptableVendors());
    }

    @Test
    void imageTypeTakesMax() {
        var jre = JavaRequirement.of(
            List.of(17), Collections.emptyList(), JvmImageType.JRE);
        var jdk = JavaRequirement.of(
            List.of(17), Collections.emptyList(), JvmImageType.JDK);
        var combined = JavaRequirement.combine(Arrays.asList(jre, jdk));
        assertEquals(JvmImageType.JDK, combined.minImageType());
    }

    @Test
    void combineEmptyListReturnsUnconstrained() {
        assertThrows(IllegalArgumentException.class,
            () -> JavaRequirement.combine(Collections.emptyList()));
    }

    @Test
    void matchesChecksMajor() {
        var r = JavaRequirement.majors(17, 21);
        var i17 = fake(new JvmVersion("17.0.5"), JvmVendor.TEMURIN, JvmImageType.JDK, "x86_64");
        var i11 = fake(new JvmVersion("11.0.5"), JvmVendor.TEMURIN, JvmImageType.JDK, "x86_64");
        assertTrue(r.matches(i17, "x86_64"));
        assertFalse(r.matches(i11, "x86_64"));
    }

    @Test
    void matchesChecksArch() {
        var r = JavaRequirement.majors(17);
        var x86 = fake(new JvmVersion("17"), JvmVendor.TEMURIN, JvmImageType.JDK, "x86_64");
        assertTrue(r.matches(x86, "x86_64"));
        assertFalse(r.matches(x86, "aarch64"));
    }

    @Test
    void matchesChecksVendorWhenConstrained() {
        var r = JavaRequirement.of(
            List.of(17), List.of(JvmVendor.TEMURIN), JvmImageType.JRE);
        var temurin = fake(new JvmVersion("17"), JvmVendor.TEMURIN, JvmImageType.JDK, "x86_64");
        var graalvm = fake(new JvmVersion("17"), JvmVendor.GRAALVM, JvmImageType.JDK, "x86_64");
        assertTrue(r.matches(temurin, "x86_64"));
        assertFalse(r.matches(graalvm, "x86_64"));
    }

    @Test
    void matchesJdkSatisfiesJreMinimum() {
        var r = JavaRequirement.of(
            List.of(17), Collections.emptyList(), JvmImageType.JRE);
        var jdk = fake(new JvmVersion("17"), JvmVendor.TEMURIN, JvmImageType.JDK, "x86_64");
        assertTrue(r.matches(jdk, "x86_64"));
    }

    @Test
    void matchesJreDoesNotSatisfyJdkMinimum() {
        var r = JavaRequirement.of(
            List.of(17), Collections.emptyList(), JvmImageType.JDK);
        var jre = fake(new JvmVersion("17"), JvmVendor.TEMURIN, JvmImageType.JRE, "x86_64");
        assertFalse(r.matches(jre, "x86_64"));
    }

    @Test
    void matchesUnknownImageTypeTreatedAsJre() {
        var jreReq = JavaRequirement.of(
            List.of(17), Collections.emptyList(), JvmImageType.JRE);
        var jdkReq = JavaRequirement.of(
            List.of(17), Collections.emptyList(), JvmImageType.JDK);
        var unknown = fake(new JvmVersion("17"), JvmVendor.TEMURIN, JvmImageType.UNKNOWN, "x86_64");
        assertTrue(jreReq.matches(unknown, "x86_64"));
        assertFalse(jdkReq.matches(unknown, "x86_64"));
    }
}
