// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.foojay;

import com.juanmuscaria.relauncher.jvm.JavaRequirement;
import com.juanmuscaria.relauncher.jvm.JvmImageType;
import com.juanmuscaria.relauncher.jvm.JvmVendor;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoojayClientUrlTest {

    @Test
    void majorsOnlyForLinuxX8664() {
        var req = JavaRequirement.majors(21);
        var url = FoojayClient.buildSearchUrl(req, "x86_64", "linux");
        assertTrue(url.startsWith("https://api.foojay.io/disco/v3.0/packages?"), url);
        assertTrue(url.contains("version=21"), url);
        assertTrue(url.contains("architecture=x86_64"), url);
        assertTrue(url.contains("operating_system=linux"), url);
        assertTrue(url.contains("archive_type=tar.gz%2Czip") || url.contains("archive_type=tar.gz,zip"), url);
        assertTrue(url.contains("directly_downloadable=true"), url);
        assertTrue(url.contains("latest=available"), url);
        assertTrue(url.contains("release_status=ga"), url);
        assertTrue(url.contains("javafx_bundled=false"), url);
        // JRE minimum: omit package_type so the API returns both JDK and JRE.
        assertFalse(url.contains("package_type="), url);
    }

    @Test
    void jreMinImageTypeOmitsPackageTypeParam() {
        var req = JavaRequirement.of(
            List.of(21), Collections.emptyList(), JvmImageType.JRE);
        var url = FoojayClient.buildSearchUrl(req, "x86_64", "linux");
        assertFalse(url.contains("package_type="), url);
    }

    @Test
    void multipleMajorsRepeatParam() {
        var req = JavaRequirement.majors(17, 21);
        var url = FoojayClient.buildSearchUrl(req, "x86_64", "linux");
        assertTrue(url.contains("version=17"), url);
        assertTrue(url.contains("version=21"), url);
    }

    @Test
    void multipleVendorsRepeatParam() {
        var req = JavaRequirement.of(
            List.of(21),
            Arrays.asList(JvmVendor.TEMURIN, JvmVendor.GRAALVM));
        var url = FoojayClient.buildSearchUrl(req, "x86_64", "linux");
        assertTrue(url.contains("distribution=temurin"), url);
        assertTrue(url.contains("distribution=graalvm_community"), url);
    }

    @Test
    void vendorUnknownIsSkipped() {
        var req = JavaRequirement.of(
            List.of(21),
            Arrays.asList(JvmVendor.TEMURIN, JvmVendor.UNKNOWN));
        var url = FoojayClient.buildSearchUrl(req, "x86_64", "linux");
        assertTrue(url.contains("distribution=temurin"), url);
        assertFalse(url.toLowerCase().contains("unknown"), url);
    }

    @Test
    void jdkMinImageTypeSendsJdk() {
        var req = JavaRequirement.of(
            List.of(21), Collections.emptyList(), JvmImageType.JDK);
        var url = FoojayClient.buildSearchUrl(req, "x86_64", "linux");
        assertTrue(url.contains("package_type=jdk"), url);
        assertFalse(url.contains("package_type=jre"), url);
    }

    @Test
    void emptyVendorsOmitsParam() {
        var req = JavaRequirement.majors(21);
        var url = FoojayClient.buildSearchUrl(req, "x86_64", "linux");
        assertFalse(url.contains("distribution="), url);
    }

    @Test
    void osParamRespectsInput() {
        var req = JavaRequirement.majors(21);
        assertTrue(FoojayClient.buildSearchUrl(req, "x86_64", "windows").contains("operating_system=windows"));
        assertTrue(FoojayClient.buildSearchUrl(req, "aarch64", "macos").contains("operating_system=macos"));
    }
}
