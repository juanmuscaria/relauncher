// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.smoketest.harness;

import com.juanmuscaria.relauncher.launch.Platform;
import com.juanmuscaria.relauncher.launch.Side;
import com.juanmuscaria.relauncher.logger.LoggerAdapter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Hand-rolled {@link Platform} used by the feature harness. Routes logs to
 * {@code java.util.logging} (always available), points config and mods at
 * scratch directories, and exposes {@link Side} as configurable.
 */
public final class TestPlatform implements Platform {
    private static final Logger LOG = Logger.getLogger("relauncher-smoketest");

    private final Path configDir;
    private final Path modsDir;
    private final Side side;
    private final LoggerAdapter logger;

    private TestPlatform(Path configDir, Path modsDir, Side side) {
        this.configDir = configDir;
        this.modsDir = modsDir;
        this.side = side;
        this.logger = new JulLoggerAdapter(LOG);
    }

    public static TestPlatform install(Path scratchRoot, Side side) {
        try {
            var cfg = scratchRoot.resolve("config");
            var mods = scratchRoot.resolve("mods");
            Files.createDirectories(cfg);
            Files.createDirectories(mods);
            var p = new TestPlatform(cfg, mods, side);
            if (!p.register()) {
                throw new IllegalStateException("Platform already registered, test isolation broken");
            }
            return p;
        } catch (Exception e) {
            throw new RuntimeException("Failed to install TestPlatform", e);
        }
    }

    @Override
    public LoggerAdapter logger() {
        return logger;
    }

    @Override
    public Path configDirectory() {
        return configDir;
    }

    @Override
    public Path modsDirectory() {
        return modsDir;
    }

    @Override
    public Side side() {
        return side;
    }

    private static final class JulLoggerAdapter implements LoggerAdapter {
        private final Logger jul;

        JulLoggerAdapter(Logger jul) {
            this.jul = jul;
        }

        @Override
        public void debug(String msg) {
            jul.fine(msg);
        }

        @Override
        public void info(String msg) {
            jul.info(msg);
        }

        @Override
        public void warn(String msg) {
            jul.warning(msg);
        }

        @Override
        public void warn(String msg, Throwable t) {
            jul.log(Level.WARNING, msg, t);
        }

        @Override
        public void error(String msg) {
            jul.severe(msg);
        }

        @Override
        public void error(String msg, Throwable t) {
            jul.log(Level.SEVERE, msg, t);
        }
    }
}
