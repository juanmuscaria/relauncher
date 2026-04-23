// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.jvm;

import java.util.regex.Pattern;

/**
 * Known JVM vendors, matched against the {@code java.vendor} system property or
 * the {@code IMPLEMENTOR} key in a JDK's {@code release} file.
 * <p>
 * The regex aliases are deliberately lenient, vendors publish their own names in
 * different forms (e.g. "Eclipse Temurin", "Eclipse Adoptium", "AdoptOpenJDK" all
 * refer to what we call {@link #TEMURIN}).
 */
public enum JvmVendor {
    // Order matters: more specific patterns come first so e.g. "Oracle GraalVM"
    // matches GRAALVM instead of ORACLE.
    GRAALVM("graalvm", "GraalVM"),
    TEMURIN("temurin|adoptium|adoptopenjdk", "Eclipse Temurin"),
    ZULU("zulu|azul", "Azul Zulu"),
    CORRETTO("corretto|amazon", "Amazon Corretto"),
    MICROSOFT("microsoft", "Microsoft Build of OpenJDK"),
    LIBERICA("liberica|bellsoft", "BellSoft Liberica"),
    SEMERU("semeru|(?:\\bibm\\b)|international business machines", "IBM Semeru"),
    SAP_MACHINE("sap[ _-]?machine|\\bsap\\b", "SapMachine"),
    OPENJDK("openjdk", "OpenJDK"),
    ORACLE("oracle", "Oracle"),
    UNKNOWN("$a^", "Unknown Vendor");

    private final Pattern pattern;
    private final String displayName;

    JvmVendor(String regex, String displayName) {
        this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        this.displayName = displayName;
    }

    /**
     * Classify a vendor string. Returns {@link #UNKNOWN} for unrecognized inputs.
     */
    public static JvmVendor guessFromVendor(String vendor) {
        if (vendor == null || vendor.isEmpty()) {
            return UNKNOWN;
        }
        for (var v : values()) {
            if (v == UNKNOWN) continue;
            if (v.pattern.matcher(vendor).find()) {
                return v;
            }
        }
        return UNKNOWN;
    }

    /**
     * Parse a user-supplied vendor name (from config). Returns null if the
     * token doesn't match any known vendor - callers log + drop such tokens.
     */
    public static JvmVendor fromConfigToken(String token) {
        if (token == null) return null;
        var trimmed = token.trim();
        if (trimmed.isEmpty()) return null;
        for (var v : values()) {
            if (v == UNKNOWN) continue;
            if (v.name().equalsIgnoreCase(trimmed)) return v;
        }
        var guessed = guessFromVendor(trimmed);
        return guessed == UNKNOWN ? null : guessed;
    }

    public String getDisplayName() {
        return displayName;
    }
}
