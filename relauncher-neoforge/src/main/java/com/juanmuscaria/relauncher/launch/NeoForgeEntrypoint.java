package com.juanmuscaria.relauncher.launch;

import com.juanmuscaria.relauncher.RelauncherConfig;
import com.juanmuscaria.relauncher.logger.Log4jLoggerAdapter;
import com.juanmuscaria.relauncher.logger.LoggerAdapter;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import org.apache.logging.log4j.LogManager;

import java.lang.reflect.Field;
import java.nio.file.Path;

/**
 * Entry point for NeoForge 1.21.3+, which dropped ModLauncher in favor of
 * FancyModLoader.
 * <p>
 * FML 10+ scans JARs for {@code META-INF/services/} entries matching a fixed
 * set of SPI types. We piggyback on {@link IModFileCandidateLocator}, which
 * runs during mod discovery - early enough for a relaunch.
 */
public class NeoForgeEntrypoint implements IModFileCandidateLocator, Platform {
    private static final String LOGGER_NAME = "Relauncher";

    private final LoggerAdapter logger = new Log4jLoggerAdapter(LogManager.getLogger(LOGGER_NAME));
    private Path configDirectory;
    private Path modsDirectory;
    private Side side = Side.UNKNOWN;

    private static Side detectSide(ILaunchContext context) {
        // Dist is in a separate artifact not on our compile classpath,
        // call getRequiredDistribution() via reflection and check the enum name
        try {
            var dist = context.getClass().getMethod("getRequiredDistribution").invoke(context);
            var name = ((Enum<?>) dist).name();
            if ("CLIENT".equals(name)) {
                return Side.CLIENT;
            } else if ("DEDICATED_SERVER".equals(name)) {
                return Side.SERVER;
            }
        } catch (Exception ignored) {
        }
        return Side.UNKNOWN;
    }

    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        this.configDirectory = context.gameDirectory().resolve("config/relauncher");
        this.modsDirectory = context.gameDirectory().resolve("mods");
        this.side = detectSide(context);
        if (register()) {
            logger.info("Relauncher loaded via NeoForge (FML 10+)");
            RelauncherConfig.standaloneRelaunch(this, this::hideEarlyWindow);
        }
        // We don't locate any mods, we are just using the hook for early initialization
    }

    @Override
    public int getPriority() {
        return HIGHEST_SYSTEM_PRIORITY;
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

    /**
     * Destroys NeoForge's early loading window so it doesn't freeze on screen during relaunch.
     */
    private void hideEarlyWindow() {
        try {
            var handlerClass = Class.forName("net.neoforged.fml.loading.ImmediateWindowHandler");
            Field providerField = handlerClass.getDeclaredField("provider");
            providerField.setAccessible(true);
            var provider = providerField.get(null);

            if (provider == null || provider.getClass().getName().contains("DummyProvider")) {
                return;
            }

            var windowField = provider.getClass().getDeclaredField("window");
            windowField.setAccessible(true);
            long windowHandle = windowField.getLong(provider);

            if (windowHandle == 0) {
                return;
            }

            var glfwClass = Class.forName("org.lwjgl.glfw.GLFW");
            glfwClass.getMethod("glfwDestroyWindow", long.class).invoke(null, windowHandle);

            logger.info("Early display window destroyed");
        } catch (Exception e) {
            logger.warn("Failed to hide early display window; it will likely remain visible until the child process exits", e);
        }
    }
}
