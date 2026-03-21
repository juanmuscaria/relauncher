// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.launch;

import com.hypixel.hytale.plugin.early.ClassTransformer;
import com.juanmuscaria.relauncher.RelauncherConfig;
import com.juanmuscaria.relauncher.logger.JavaLoggerAdapter;
import com.juanmuscaria.relauncher.logger.LoggerAdapter;
import com.juanmuscaria.relauncher.logger.StdoutLoggerAdapter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

/**
 * Hooks into Hytale's early plugin system via the {@link ClassTransformer} SPI.
 * <p>
 * We don't actually transform any classes, this is just the earliest hook
 * Hytale gives us. The relaunch is triggered when ServiceLoader instantiates
 * this class during {@code EarlyPluginLoader.loadEarlyPlugins()}.
 */
public class HytaleEarlyEntrypoint implements ClassTransformer, Platform {
    private static final String LOGGER_NAME = "Relauncher";

    private final LoggerAdapter logger = new StdoutLoggerAdapter(LOGGER_NAME);

    public HytaleEarlyEntrypoint() {
        if (register()) {
            logger.info("Relauncher loaded via Hytale early plugin");
            RelauncherConfig.standaloneRelaunch(this);
        }
    }

    @Override
    public int priority() {
        return Integer.MIN_VALUE;
    }

    @Override
    public byte[] transform(String className, String internalName, byte[] classBytes) {
        // We're not here to transform anything, just to get loaded early.
        return null;
    }

    @Override
    public LoggerAdapter logger() {
        return logger;
    }

    @Override
    public Path configDirectory() {
        return Paths.get("config", "relauncher");
    }

    @Override
    public Path modsDirectory() {
        return Paths.get("mods");
    }

    @Override
    public Side side() {
        return Side.SERVER;
    }
}
