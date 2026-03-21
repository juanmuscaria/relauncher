package com.hypixel.hytale.plugin.early;

public interface ClassTransformer {
    int priority();

    byte[] transform(String className, String internalName, byte[] classBytes);
}
