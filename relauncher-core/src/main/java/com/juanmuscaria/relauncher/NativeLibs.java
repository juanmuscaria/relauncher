// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher;

import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Extracts and loads the {@code relauncher_native} shared library.
 * <p>
 * Prebuilt binaries for each platform live under
 * {@code relauncher-natives/<os>-<arch>/} on the classpath. The library is
 * extracted to a temp file and loaded at most once.
 */
public final class NativeLibs {
    private static final String OS_NAME;
    private static final String ARCH;
    private static final String LIBRARY_FILE_NAME;

    private static volatile boolean loaded;
    private static volatile boolean attemptedLoad;

    static {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            OS_NAME = "windows";
        } else if (os.contains("mac") || os.contains("darwin")) {
            OS_NAME = "macos";
        } else {
            OS_NAME = "linux";
        }

        ARCH = switch (System.getProperty("os.arch", "").toLowerCase()) {
            case "amd64", "x86_64" -> "x86_64";
            case "aarch64", "arm64" -> "aarch64";
            default -> System.getProperty("os.arch", "unknown").toLowerCase();
        };

        LIBRARY_FILE_NAME = switch (OS_NAME) {
            case "windows" -> "relauncher_native.dll";
            case "macos" -> "librelauncher_native.dylib";
            default -> "librelauncher_native.so";
        };
    }

    private NativeLibs() {
    }

    /**
     * Normalized OS name: {@code linux}, {@code macos}, or {@code windows}.
     */
    public static String osName() {
        return OS_NAME;
    }

    /**
     * Normalized architecture: {@code x86_64} or {@code aarch64}.
     */
    public static String arch() {
        return ARCH;
    }

    /**
     * Makes sure the native library is loaded. Safe to call from anywhere,
     * any number of times, the actual load only happens once.
     *
     * @return true if the library is (now) available
     */
    public static boolean ensureLoaded() {
        if (!attemptedLoad) {
            synchronized (NativeLibs.class) {
                if (!attemptedLoad) {
                    loaded = extractAndLoad(
                        "relauncher-natives/" + OS_NAME + "-" + ARCH + "/" + LIBRARY_FILE_NAME,
                        "relauncher_native");
                    attemptedLoad = true;
                }
            }
        }
        return loaded;
    }

    private static boolean extractAndLoad(String resourcePath, String libName) {
        try (var in = NativeLibs.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in != null) {
                var suffix = resourcePath.substring(resourcePath.lastIndexOf('.'));
                var temp = Files.createTempFile("relauncher-" + libName + "-", suffix);
                Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
                System.load(temp.toAbsolutePath().toString());
                return true;
            }
        } catch (Exception e) {
            System.err.println("[Relauncher] Failed to extract native library " + resourcePath + ": " + e.getMessage());
        }

        try {
            System.loadLibrary(libName);
            return true;
        } catch (UnsatisfiedLinkError e) {
            System.err.println("[Relauncher] Native library " + libName + " not available: " + e.getMessage());
            return false;
        }
    }
}
