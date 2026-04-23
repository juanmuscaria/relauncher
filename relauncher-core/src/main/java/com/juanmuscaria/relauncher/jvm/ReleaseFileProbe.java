// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.jvm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads and parses the {@code release} file shipped with every JDK/JRE since 8.
 * <p>
 * The file is a flat key=value text file. We extract {@code JAVA_VERSION},
 * {@code IMPLEMENTOR}, {@code OS_ARCH}, and {@code IMAGE_TYPE} (JDK 11+).
 */
public final class ReleaseFileProbe {

    private ReleaseFileProbe() {
    }

    /**
     * Load and parse {@code <home>/release}. Returns an empty result if the file is missing.
     */
    public static Result load(Path javaHome) {
        var releaseFile = javaHome.resolve("release");
        if (!Files.isRegularFile(releaseFile)) {
            return new Result();
        }
        try {
            return parse(Files.readAllLines(releaseFile));
        } catch (IOException e) {
            return new Result();
        }
    }

    static Result parse(List<String> lines) {
        var r = new Result();
        if (lines == null) return r;
        for (var line : lines) {
            if (line == null) continue;
            var trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

            var eq = trimmed.indexOf('=');
            if (eq <= 0) continue; // no key or empty key

            var key = trimmed.substring(0, eq).trim();
            var value = trimmed.substring(eq + 1).trim();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }

            switch (key) {
                case "JAVA_VERSION":
                    r.javaVersion = value;
                    break;
                case "IMPLEMENTOR":
                    r.implementor = value;
                    break;
                case "OS_ARCH":
                    r.osArch = value;
                    break;
                case "IMAGE_TYPE":
                    r.imageType = value;
                    break;
                default: /* ignore */
            }
        }
        return r;
    }

    /**
     * Parsed probe data. Any field may be null if absent or unparseable.
     */
    public static final class Result {
        public String javaVersion;
        public String implementor;
        public String osArch;
        public String imageType;

        public boolean isEmpty() {
            return javaVersion == null && implementor == null && osArch == null && imageType == null;
        }
    }
}
