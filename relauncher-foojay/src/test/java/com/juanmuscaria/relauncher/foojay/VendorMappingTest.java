// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.foojay;

import com.juanmuscaria.relauncher.jvm.JvmVendor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class VendorMappingTest {

    @Test
    void forwardMapping() {
        assertEquals("temurin", VendorMapping.toFoojay(JvmVendor.TEMURIN));
        assertEquals("graalvm_community", VendorMapping.toFoojay(JvmVendor.GRAALVM));
        assertEquals("zulu", VendorMapping.toFoojay(JvmVendor.ZULU));
        assertEquals("corretto", VendorMapping.toFoojay(JvmVendor.CORRETTO));
        assertEquals("microsoft", VendorMapping.toFoojay(JvmVendor.MICROSOFT));
        assertEquals("liberica", VendorMapping.toFoojay(JvmVendor.LIBERICA));
        assertEquals("semeru", VendorMapping.toFoojay(JvmVendor.SEMERU));
        assertEquals("sapmachine", VendorMapping.toFoojay(JvmVendor.SAP_MACHINE));
        assertEquals("openjdk", VendorMapping.toFoojay(JvmVendor.OPENJDK));
        assertEquals("oracle", VendorMapping.toFoojay(JvmVendor.ORACLE));
    }

    @Test
    void forwardUnknownIsNull() {
        assertNull(VendorMapping.toFoojay(JvmVendor.UNKNOWN));
    }

    @Test
    void reverseMapping() {
        assertEquals(JvmVendor.TEMURIN, VendorMapping.fromFoojay("temurin"));
        assertEquals(JvmVendor.GRAALVM, VendorMapping.fromFoojay("graalvm_community"));
        assertEquals(JvmVendor.SAP_MACHINE, VendorMapping.fromFoojay("sapmachine"));
    }

    @Test
    void reverseUnknownReturnsUnknown() {
        assertEquals(JvmVendor.UNKNOWN, VendorMapping.fromFoojay("nosuch"));
        assertEquals(JvmVendor.UNKNOWN, VendorMapping.fromFoojay(null));
    }

    @Test
    void reverseIsCaseInsensitive() {
        assertEquals(JvmVendor.TEMURIN, VendorMapping.fromFoojay("TEMURIN"));
        assertEquals(JvmVendor.GRAALVM, VendorMapping.fromFoojay("GraalVM_Community"));
    }
}
