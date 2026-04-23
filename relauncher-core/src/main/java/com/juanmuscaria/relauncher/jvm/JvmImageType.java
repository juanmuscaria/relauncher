// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.jvm;

/**
 * Whether a Java installation ships a full compiler toolchain (JDK)
 * or only the runtime (JRE).
 */
public enum JvmImageType {
    JDK,
    JRE,
    UNKNOWN
}
