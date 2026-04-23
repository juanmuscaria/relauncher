// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.jvm;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A Java runtime requirement expressed as a set of acceptable major versions,
 * optional vendor constraint, and a minimum image type.
 * <p>
 * Combining multiple requirements takes the intersection on majors and vendors
 * and the maximum on image type. An empty vendor list means "any vendor". An
 * empty major intersection or non-empty disjoint vendor intersection throws
 * {@link UnsatisfiableException}.
 */
public final class JavaRequirement {
    private final List<Integer> acceptableMajors;
    private final List<JvmVendor> acceptableVendors;
    private final JvmImageType minImageType;

    private JavaRequirement(List<Integer> majors, List<JvmVendor> vendors, JvmImageType minImageType) {
        this.acceptableMajors = Collections.unmodifiableList(new ArrayList<>(majors));
        this.acceptableVendors = Collections.unmodifiableList(new ArrayList<>(vendors));
        this.minImageType = minImageType;
    }

    /**
     * Build from majors only; any vendor, JRE minimum.
     */
    public static JavaRequirement majors(int... majors) {
        if (majors.length == 0) {
            throw new IllegalArgumentException("At least one acceptable major required");
        }
        List<Integer> list = new ArrayList<>(majors.length);
        for (var m : majors) list.add(m);
        return new JavaRequirement(list, Collections.emptyList(), JvmImageType.JRE);
    }

    public static JavaRequirement of(List<Integer> majors, List<JvmVendor> vendors) {
        return of(majors, vendors, JvmImageType.JRE);
    }

    public static JavaRequirement of(List<Integer> majors, List<JvmVendor> vendors, JvmImageType minImageType) {
        if (majors == null || majors.isEmpty()) {
            throw new IllegalArgumentException("At least one acceptable major required");
        }
        return new JavaRequirement(majors, vendors == null ? Collections.emptyList() : vendors,
            minImageType == null ? JvmImageType.JRE : minImageType);
    }

    /**
     * Combine (intersect) multiple requirements.
     *
     * @throws IllegalArgumentException if the list is empty
     * @throws UnsatisfiableException   if majors or vendors have empty intersection
     */
    public static JavaRequirement combine(Collection<JavaRequirement> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            throw new IllegalArgumentException("At least one requirement required");
        }

        Set<Integer> majors = null;
        Set<JvmVendor> vendors = null;
        var imageType = JvmImageType.JRE;

        for (var r : requirements) {
            if (majors == null) {
                majors = new LinkedHashSet<>(r.acceptableMajors);
            } else {
                majors.retainAll(r.acceptableMajors);
            }

            if (!r.acceptableVendors.isEmpty()) {
                if (vendors == null) {
                    vendors = new LinkedHashSet<>(r.acceptableVendors);
                } else {
                    vendors.retainAll(r.acceptableVendors);
                }
            }

            if (r.minImageType == JvmImageType.JDK) {
                imageType = JvmImageType.JDK;
            }
        }

        if (majors.isEmpty()) {
            throw new UnsatisfiableException("No overlapping major versions across " + requirements.size()
                + " requirements: " + describeMajors(requirements));
        }
        if (vendors != null && vendors.isEmpty()) {
            throw new UnsatisfiableException("No overlapping vendors across requirements: "
                + describeVendors(requirements));
        }

        return new JavaRequirement(
            new ArrayList<>(majors),
            vendors == null ? Collections.emptyList() : new ArrayList<>(vendors),
            imageType);
    }

    /**
     * Canonicalize JVM/OS arch strings so aliases compare equal.
     * {@code os.arch} reports {@code amd64} on most JDKs while {@code release}
     * files often say {@code x86_64}; {@code aarch64} / {@code arm64} similarly diverge.
     */
    static String canonicalArch(String arch) {
        if (arch == null) return "";
        var a = arch.trim().toLowerCase(Locale.ROOT);
        return switch (a) {
            case "amd64", "x86_64", "x64" -> "x86_64";
            case "aarch64", "arm64" -> "aarch64";
            case "x86", "i386", "i486", "i586", "i686" -> "x86";
            default -> a;
        };
    }

    private static boolean imageTypeSatisfies(JvmImageType actual, JvmImageType minimum) {
        if (minimum == JvmImageType.JRE) return true; // anything (including UNKNOWN) satisfies JRE
        // minimum is JDK: only actual JDK satisfies
        return actual == JvmImageType.JDK;
    }

    private static String describeMajors(Collection<JavaRequirement> reqs) {
        return reqs.stream()
            .map(r -> r.acceptableMajors.toString())
            .collect(Collectors.joining(" ∩ "));
    }

    private static String describeVendors(Collection<JavaRequirement> reqs) {
        return reqs.stream()
            .filter(r -> !r.acceptableVendors.isEmpty())
            .map(r -> r.acceptableVendors.toString())
            .collect(Collectors.joining(" ∩ "));
    }

    public List<Integer> acceptableMajors() {
        return acceptableMajors;
    }

    public List<JvmVendor> acceptableVendors() {
        return acceptableVendors;
    }

    public JvmImageType minImageType() {
        return minImageType;
    }

    /**
     * Does the given installation satisfy this requirement, given the current process arch?
     */
    public boolean matches(JavaInstallation install, String currentArch) {
        if (install.arch() != null && currentArch != null
            && !canonicalArch(install.arch()).equals(canonicalArch(currentArch))) {
            return false;
        }
        if (install.version() == null || !acceptableMajors.contains(install.version().getMajor())) {
            return false;
        }
        if (!acceptableVendors.isEmpty() && !acceptableVendors.contains(install.vendor())) {
            return false;
        }
        if (!imageTypeSatisfies(install.imageType(), minImageType)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "JavaRequirement{majors=" + acceptableMajors
            + ", vendors=" + (acceptableVendors.isEmpty() ? "any" : acceptableVendors)
            + ", minImageType=" + minImageType + "}";
    }

    /**
     * Thrown when requirements can't be combined because they have no overlap.
     */
    public static final class UnsatisfiableException extends RuntimeException {
        public UnsatisfiableException(String message) {
            super(message);
        }
    }
}
