// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.jvm;

import java.nio.file.Path;
import java.util.Objects;

/** A detected Java install, immutable, fields filled by {@link JavaInstallationDetector} */
public final class JavaInstallation {
    private final Path home;
    private final Path executable;
    private final JvmVersion version;
    private final JvmVendor vendor;
    private final JvmImageType imageType;
    private final String arch;

    public JavaInstallation(Path home, Path executable, JvmVersion version,
                            JvmVendor vendor, JvmImageType imageType, String arch) {
        this.home = home;
        this.executable = executable;
        this.version = version;
        this.vendor = vendor;
        this.imageType = imageType;
        this.arch = arch;
    }

    public Path home() {
        return home;
    }

    public Path executable() {
        return executable;
    }

    public JvmVersion version() {
        return version;
    }

    public JvmVendor vendor() {
        return vendor;
    }

    public JvmImageType imageType() {
        return imageType;
    }

    public String arch() {
        return arch;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof JavaInstallation that)) return false;
        return Objects.equals(home, that.home);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(home);
    }

    @Override
    public String toString() {
        return "JavaInstallation{home=" + home +
            ", version=" + (version != null ? version.getOriginalVersionString() : "?") +
            ", vendor=" + vendor +
            ", imageType=" + imageType +
            ", arch=" + arch + "}";
    }
}
