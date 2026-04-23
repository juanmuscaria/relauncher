// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.jvm;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JvmVendorTest {

    @Test
    void temurinAliases() {
        assertEquals(JvmVendor.TEMURIN, JvmVendor.guessFromVendor("Eclipse Temurin"));
        assertEquals(JvmVendor.TEMURIN, JvmVendor.guessFromVendor("Eclipse Adoptium"));
        assertEquals(JvmVendor.TEMURIN, JvmVendor.guessFromVendor("AdoptOpenJDK"));
        assertEquals(JvmVendor.TEMURIN, JvmVendor.guessFromVendor("adoptopenjdk"));
    }

    @Test
    void oracle() {
        assertEquals(JvmVendor.ORACLE, JvmVendor.guessFromVendor("Oracle Corporation"));
    }

    @Test
    void zulu() {
        assertEquals(JvmVendor.ZULU, JvmVendor.guessFromVendor("Azul Systems, Inc."));
        assertEquals(JvmVendor.ZULU, JvmVendor.guessFromVendor("Zulu"));
    }

    @Test
    void graalvm() {
        assertEquals(JvmVendor.GRAALVM, JvmVendor.guessFromVendor("GraalVM Community"));
        assertEquals(JvmVendor.GRAALVM, JvmVendor.guessFromVendor("Oracle GraalVM"));
    }

    @Test
    void corretto() {
        assertEquals(JvmVendor.CORRETTO, JvmVendor.guessFromVendor("Amazon.com Inc."));
        assertEquals(JvmVendor.CORRETTO, JvmVendor.guessFromVendor("Corretto"));
    }

    @Test
    void microsoft() {
        assertEquals(JvmVendor.MICROSOFT, JvmVendor.guessFromVendor("Microsoft"));
    }

    @Test
    void liberica() {
        assertEquals(JvmVendor.LIBERICA, JvmVendor.guessFromVendor("BellSoft Liberica"));
        assertEquals(JvmVendor.LIBERICA, JvmVendor.guessFromVendor("Bellsoft"));
    }

    @Test
    void semeru() {
        assertEquals(JvmVendor.SEMERU, JvmVendor.guessFromVendor("IBM Semeru Runtime"));
        assertEquals(JvmVendor.SEMERU, JvmVendor.guessFromVendor("International Business Machines Corporation"));
    }

    @Test
    void sapMachine() {
        assertEquals(JvmVendor.SAP_MACHINE, JvmVendor.guessFromVendor("SAP SE"));
        assertEquals(JvmVendor.SAP_MACHINE, JvmVendor.guessFromVendor("SapMachine"));
        assertEquals(JvmVendor.SAP_MACHINE, JvmVendor.guessFromVendor("SAP-Machine"));
    }

    @Test
    void openjdk() {
        assertEquals(JvmVendor.OPENJDK, JvmVendor.guessFromVendor("OpenJDK"));
    }

    @Test
    void unknownForGarbageInput() {
        assertEquals(JvmVendor.UNKNOWN, JvmVendor.guessFromVendor("Acme JVM"));
        assertEquals(JvmVendor.UNKNOWN, JvmVendor.guessFromVendor(""));
        assertEquals(JvmVendor.UNKNOWN, JvmVendor.guessFromVendor(null));
    }

    @Test
    void preferMoreSpecificMatches() {
        // "Oracle GraalVM" contains "Oracle" but should be classified as GraalVM
        assertEquals(JvmVendor.GRAALVM, JvmVendor.guessFromVendor("Oracle GraalVM"));
    }

    @Test
    void fromConfigTokenAcceptsEnumName() {
        assertEquals(JvmVendor.TEMURIN, JvmVendor.fromConfigToken("temurin"));
        assertEquals(JvmVendor.TEMURIN, JvmVendor.fromConfigToken("TEMURIN"));
        assertEquals(JvmVendor.GRAALVM, JvmVendor.fromConfigToken("graalvm"));
    }

    @Test
    void fromConfigTokenAcceptsAliases() {
        assertEquals(JvmVendor.TEMURIN, JvmVendor.fromConfigToken("adoptium"));
        assertEquals(JvmVendor.TEMURIN, JvmVendor.fromConfigToken("adoptopenjdk"));
        assertEquals(JvmVendor.LIBERICA, JvmVendor.fromConfigToken("bellsoft"));
    }

    @Test
    void fromConfigTokenRejectsUnknown() {
        Assertions.assertNull(JvmVendor.fromConfigToken("nosuchvendor"));
        Assertions.assertNull(JvmVendor.fromConfigToken(""));
        Assertions.assertNull(JvmVendor.fromConfigToken(null));
    }
}
