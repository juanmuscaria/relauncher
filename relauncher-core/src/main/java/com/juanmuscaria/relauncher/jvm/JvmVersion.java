// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.jvm;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsed Java version string.
 * <p>
 * Supports both the legacy Oracle scheme ({@code 1.8.0_291}, {@code 8u291}) and
 * the JEP scheme ({@code 11.0.2}, {@code 17.0.8+7}, {@code 21-ea}).
 *
 * @see <a href="https://www.oracle.com/java/technologies/javase/versioning-naming.html">
 * Java SE version and naming</a>
 */
public final class JvmVersion implements Comparable<JvmVersion> {
    private static final Pattern LEGACY_PATTERN = Pattern.compile("1\\.(\\d+)(?:\\.\\d+)?(?:[._](\\d+))?.*");
    private static final Pattern MODERN_PATTERN = Pattern.compile("(\\d+)(?:\\.\\d+)?(?:\\.(\\d+))?.*");
    private static final Pattern LEGACY_U_PATTERN = Pattern.compile("(\\d+)u(\\d+).*");

    private final int major;
    private final int patch;
    private final String originalVersionString;

    public JvmVersion(String version) {
        this.originalVersionString = version;
        var parsed = parse(version);
        this.major = parsed[0];
        this.patch = parsed[1];
    }

    public static JvmVersion fromSystemProperties() {
        return new JvmVersion(System.getProperty("java.version"));
    }

    private static int[] parse(String version) {
        if (version == null || version.isEmpty()) {
            return new int[]{0, 0};
        }

        var legacy = LEGACY_PATTERN.matcher(version);
        if (legacy.matches()) {
            var major = Integer.parseInt(legacy.group(1));
            var patch = legacy.group(2) != null ? Integer.parseInt(legacy.group(2)) : 0;
            return new int[]{major, patch};
        }

        var legacyU = LEGACY_U_PATTERN.matcher(version);
        if (legacyU.matches()) {
            var major = Integer.parseInt(legacyU.group(1));
            var patch = Integer.parseInt(legacyU.group(2));
            return new int[]{major, patch};
        }

        var modern = MODERN_PATTERN.matcher(version);
        if (modern.matches()) {
            var major = Integer.parseInt(modern.group(1));
            var patch = modern.group(2) != null ? Integer.parseInt(modern.group(2)) : 0;
            return new int[]{major, patch};
        }

        return new int[]{0, 0};
    }

    public int getMajor() {
        return major;
    }

    public int getPatch() {
        return patch;
    }

    public String getOriginalVersionString() {
        return originalVersionString;
    }

    public boolean isAtLeast(int major) {
        return this.major >= major;
    }

    public boolean isAtLeast(int major, int patch) {
        if (this.major != major) {
            return this.major > major;
        }
        return this.patch >= patch;
    }

    public String toSimpleString() {
        return major <= 8 ? "1." + major : String.valueOf(major);
    }

    @Override
    public int compareTo(JvmVersion other) {
        var c = Integer.compare(this.major, other.major);
        return c != 0 ? c : Integer.compare(this.patch, other.patch);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof JvmVersion that)) return false;
        return major == that.major && patch == that.patch;
    }

    @Override
    public int hashCode() {
        return 31 * major + patch;
    }

    @Override
    public String toString() {
        return "JvmVersion{major=" + major + ", patch=" + patch +
            ", originalVersionString='" + originalVersionString + "'}";
    }
}
