package com.hypixel.hytale.server.core.plugin;

import java.nio.file.Path;

public abstract class PluginBase {
    public Path getDataDirectory() {
        throw new UnsupportedOperationException("stub");
    }

    protected void setup() {
    }
}
