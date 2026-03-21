package com.juanmuscaria.relauncher.launch;

import com.juanmuscaria.relauncher.RelauncherConfig;
import com.juanmuscaria.relauncher.logger.Log4jLoggerAdapter;
import com.juanmuscaria.relauncher.logger.LoggerAdapter;
import com.juanmuscaria.relauncher.logger.StdoutLoggerAdapter;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * Entry point for Forge 1.6.4-1.12.2, which uses LaunchWrapper.
 */
public class LaunchWrapperEntrypoint implements ITweaker, Platform {
    private static final String LOGGER_NAME = "Relauncher";

    private final LoggerAdapter logger = makeLogger();
    private Path configDirectory;
    private Path modsDirectory;
    private Side side;

    private static Side detectSide() {
        // If the client main class is in the classpath, we're on the client
        if (LaunchWrapperEntrypoint.class.getClassLoader()
            .getResource("net/minecraft/client/main/Main.class") != null) {
            return Side.CLIENT;
        }
        return Side.SERVER;
    }

    private static LoggerAdapter makeLogger() {
        try {
            return new Log4jLoggerAdapter(org.apache.logging.log4j.LogManager.getLogger(LOGGER_NAME));
        } catch (Throwable ignored) {
            // Log4j2 not available (Forge 1.6.4), fall back
            return new StdoutLoggerAdapter(LOGGER_NAME);
        }
    }

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
        this.configDirectory = new File(gameDir, "config/relauncher").toPath();
        this.modsDirectory = new File(gameDir, "mods").toPath();
        this.side = detectSide();

        if (register()) {
            logger.info("Relauncher loaded via LaunchWrapper");
            RelauncherConfig.standaloneRelaunch(this);
        }
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
    }

    @Override
    public String getLaunchTarget() {
        return null;
    }

    @Override
    public String[] getLaunchArguments() {
        return new String[0];
    }

    @Override
    public LoggerAdapter logger() {
        return logger;
    }

    @Override
    public Path configDirectory() {
        return configDirectory;
    }

    @Override
    public Path modsDirectory() {
        return modsDirectory;
    }

    @Override
    public Side side() {
        return side;
    }
}
