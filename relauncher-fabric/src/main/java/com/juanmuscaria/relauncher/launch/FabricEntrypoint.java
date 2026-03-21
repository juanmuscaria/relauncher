// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.launch;

import com.juanmuscaria.relauncher.RelauncherConfig;
import com.juanmuscaria.relauncher.logger.Log4jLoggerAdapter;
import com.juanmuscaria.relauncher.logger.LoggerAdapter;
import com.juanmuscaria.relauncher.logger.StdoutLoggerAdapter;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.apache.logging.log4j.LogManager;

import java.nio.file.Path;

/**
 * Hooks into Fabric's {@code preLaunch} entrypoint (declared in {@code fabric.mod.json}).
 */
public class FabricEntrypoint implements PreLaunchEntrypoint, Platform {
    private static final String LOGGER_NAME = "Relauncher";

    private final LoggerAdapter logger = makeLogger();

    private static LoggerAdapter makeLogger() {
        try {
            return new Log4jLoggerAdapter(LogManager.getLogger(LOGGER_NAME));
        } catch (Throwable ignored) {
            return new StdoutLoggerAdapter(LOGGER_NAME);
        }
    }

    @Override
    public void onPreLaunch() {
        if (register()) {
            logger.info("Relauncher loaded via Fabric");
            RelauncherConfig.standaloneRelaunch(this);
        }
    }

    @Override
    public LoggerAdapter logger() {
        return logger;
    }

    @Override
    public Path configDirectory() {
        return FabricLoader.getInstance().getConfigDir().resolve("relauncher");
    }

    @Override
    public Path modsDirectory() {
        return FabricLoader.getInstance().getGameDir().resolve("mods");
    }

    @Override
    public Side side() {
        var envType = FabricLoader.getInstance().getEnvironmentType();
        if (envType == EnvType.CLIENT) return Side.CLIENT;
        if (envType == EnvType.SERVER) return Side.SERVER;
        return Side.UNKNOWN;
    }
}
