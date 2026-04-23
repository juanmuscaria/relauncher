// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.jvm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JvmVersionTest {

    @Test
    void parsesLegacyJava8() {
        var v = new JvmVersion("1.8.0_291");
        assertEquals(8, v.getMajor());
        assertEquals(291, v.getPatch());
        assertEquals("1.8.0_291", v.getOriginalVersionString());
    }

    @Test
    void parsesLegacyWithoutPatch() {
        var v = new JvmVersion("1.8.0");
        assertEquals(8, v.getMajor());
        assertEquals(0, v.getPatch());
    }

    @Test
    void parsesModernJava11() {
        var v = new JvmVersion("11.0.2");
        assertEquals(11, v.getMajor());
        assertEquals(2, v.getPatch());
    }

    @Test
    void parsesModernWithBuild() {
        var v = new JvmVersion("17.0.8+7");
        assertEquals(17, v.getMajor());
        assertEquals(8, v.getPatch());
    }

    @Test
    void parsesEarlyAccess() {
        var v = new JvmVersion("21-ea");
        assertEquals(21, v.getMajor());
        assertEquals(0, v.getPatch());
    }

    @Test
    void parsesPlainMajor() {
        var v = new JvmVersion("25");
        assertEquals(25, v.getMajor());
        assertEquals(0, v.getPatch());
    }

    @Test
    void parsesEmptyAndNullAsZero() {
        assertEquals(0, new JvmVersion("").getMajor());
        assertEquals(0, new JvmVersion(null).getMajor());
    }

    @Test
    void parsesGarbageAsZero() {
        assertEquals(0, new JvmVersion("not a version").getMajor());
    }

    @Test
    void compareByMajor() {
        assertTrue(new JvmVersion("17.0.1").compareTo(new JvmVersion("11.0.2")) > 0);
        assertTrue(new JvmVersion("8.0.292").compareTo(new JvmVersion("17.0.0")) < 0);
    }

    @Test
    void compareByPatchWhenMajorTies() {
        assertTrue(new JvmVersion("17.0.5").compareTo(new JvmVersion("17.0.2")) > 0);
        assertEquals(0, new JvmVersion("17.0.2").compareTo(new JvmVersion("17.0.2")));
    }

    @Test
    void isAtLeast() {
        var v = new JvmVersion("17.0.5");
        assertTrue(v.isAtLeast(17));
        assertTrue(v.isAtLeast(11));
        assertFalse(v.isAtLeast(21));
        assertTrue(v.isAtLeast(17, 5));
        assertFalse(v.isAtLeast(17, 10));
    }

    @Test
    void toSimpleStringForLegacyAndModern() {
        assertEquals("1.8", new JvmVersion("1.8.0_291").toSimpleString());
        assertEquals("17", new JvmVersion("17.0.8").toSimpleString());
    }

    @Test
    void equalsBasedOnMajorAndPatch() {
        // originalVersionString differs but values match
        assertEquals(new JvmVersion("17.0.5"), new JvmVersion("17.0.5+9"));
        assertNotEquals(new JvmVersion("17.0.5"), new JvmVersion("17.0.6"));
    }
}
