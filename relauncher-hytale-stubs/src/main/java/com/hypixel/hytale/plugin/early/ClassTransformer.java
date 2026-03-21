// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.hypixel.hytale.plugin.early;

public interface ClassTransformer {
    int priority();

    byte[] transform(String className, String internalName, byte[] classBytes);
}
