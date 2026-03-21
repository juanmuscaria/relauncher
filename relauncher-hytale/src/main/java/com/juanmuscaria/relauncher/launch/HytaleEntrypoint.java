// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.launch;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.juanmuscaria.relauncher.RelauncherConfig;
import com.juanmuscaria.relauncher.logger.JavaLoggerAdapter;
import com.juanmuscaria.relauncher.logger.LoggerAdapter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

/**
 * Normal Hytale plugin entry point. Triggers a relaunch during the {@code setup}
 * phase of the plugin lifecycle.
 * <p>
 * This runs later than the early plugin, after PluginManager has loaded all
 * plugins but before the server starts ticking. Use this when you can't (or
 * don't want to) use the early plugin system.
 */
public class HytaleEntrypoint extends JavaPlugin implements Platform {
    private static final String LOGGER_NAME = "Relauncher";

    private final LoggerAdapter relauncherLogger = new JavaLoggerAdapter(Logger.getLogger(LOGGER_NAME));

    public HytaleEntrypoint(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        if (register()) {
            relauncherLogger.info("Relauncher loaded via Hytale plugin");
            RelauncherConfig.standaloneRelaunch(this);
        }
    }

    @Override
    public LoggerAdapter logger() {
        return relauncherLogger;
    }

    @Override
    public Path configDirectory() {
        return getDataDirectory();
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
