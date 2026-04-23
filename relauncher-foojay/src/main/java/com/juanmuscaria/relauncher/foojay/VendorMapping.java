// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.foojay;

import com.juanmuscaria.relauncher.jvm.JvmVendor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Maps between Relauncher's {@link JvmVendor} enum and Foojay's
 * {@code distribution} parameter names.
 */
final class VendorMapping {

    private static final Map<JvmVendor, String> FORWARD;
    private static final Map<String, JvmVendor> REVERSE;

    static {
        Map<JvmVendor, String> fwd = new HashMap<>();
        fwd.put(JvmVendor.TEMURIN, "temurin");
        fwd.put(JvmVendor.ORACLE, "oracle");
        fwd.put(JvmVendor.ZULU, "zulu");
        fwd.put(JvmVendor.GRAALVM, "graalvm_community");
        fwd.put(JvmVendor.CORRETTO, "corretto");
        fwd.put(JvmVendor.MICROSOFT, "microsoft");
        fwd.put(JvmVendor.LIBERICA, "liberica");
        fwd.put(JvmVendor.SEMERU, "semeru");
        fwd.put(JvmVendor.SAP_MACHINE, "sapmachine");
        fwd.put(JvmVendor.OPENJDK, "openjdk");
        FORWARD = Collections.unmodifiableMap(fwd);

        Map<String, JvmVendor> rev = new HashMap<>();
        for (var e : fwd.entrySet()) {
            rev.put(e.getValue(), e.getKey());
        }
        REVERSE = Collections.unmodifiableMap(rev);
    }

    private VendorMapping() {
    }

    static String toFoojay(JvmVendor vendor) {
        if (vendor == null) return null;
        return FORWARD.get(vendor);
    }

    static JvmVendor fromFoojay(String distribution) {
        if (distribution == null) return JvmVendor.UNKNOWN;
        var v = REVERSE.get(distribution.toLowerCase(Locale.ROOT));
        return v != null ? v : JvmVendor.UNKNOWN;
    }
}
