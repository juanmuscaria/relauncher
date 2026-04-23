// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.foojay;

/**
 * Raw DTO mirroring the subset of Foojay's package schema we care about.
 * Populated by Gson, then transformed into a {@code RemotePackage} by the client.
 */
final class FoojayPackage {
    String id;
    String distribution;
    String java_version;
    String package_type;      // "jdk" | "jre"
    String architecture;
    String operating_system;
    Long size;
    String archive_type;
    boolean directly_downloadable;
    Links links;

    static final class Links {
        String pkg_download_redirect;
        String pkg_info_uri;
    }
}
