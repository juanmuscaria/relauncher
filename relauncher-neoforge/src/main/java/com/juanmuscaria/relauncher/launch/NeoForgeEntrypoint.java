// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.launch;

import com.juanmuscaria.relauncher.RelauncherConfig;
import com.juanmuscaria.relauncher.logger.Log4jLoggerAdapter;
import com.juanmuscaria.relauncher.logger.LoggerAdapter;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import org.apache.logging.log4j.LogManager;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
        if (register()) {
            logger.info("Relauncher loaded via NeoForge (FML 10+)");
            this.configDirectory = context.gameDirectory().resolve("config/relauncher");
            this.modsDirectory = context.gameDirectory().resolve("mods");
            this.side = detectSide(context);
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
            var providerField = handlerClass.getDeclaredField("provider");
            providerField.setAccessible(true);
            var provider = providerField.get(null);

            if (provider == null || provider.getClass().getName().contains("DummyProvider")) {
                return;
            }

            var providerClass = provider.getClass();
            var windowHandle = (long) readField(providerClass, "window", provider);
            if (windowHandle == 0) {
                return;
            }

            // Destroying the window from any thread other than FML's render thread
            // is a use-after-free: the render thread is still inside initRender(),
            // holding the GL context in its GLFW TLS. Once we free the context,
            // initRender's trailing glfwMakeContextCurrent(0) jumps through a NULL
            // function pointer (SIGSEGV at PC=0). Wait for init, stop the periodic
            // ticker, then run the destroy on the render thread itself.
            var initFuture = (Future<?>) readField(providerClass, "initializationFuture", provider);
            if (initFuture != null) {
                initFuture.get(30, TimeUnit.SECONDS);
            }
            var windowTick = (Future<?>) readField(providerClass, "windowTick", provider);
            if (windowTick != null) {
                windowTick.cancel(false);
            }
            var renderScheduler = (ExecutorService) readField(providerClass, "renderScheduler", provider);
            var glfwClass = Class.forName("org.lwjgl.glfw.GLFW");
            var makeContextCurrent = glfwClass.getMethod("glfwMakeContextCurrent", long.class);
            var destroyWindow = glfwClass.getMethod("glfwDestroyWindow", long.class);

            renderScheduler.submit(() -> {
                try {
                    makeContextCurrent.invoke(null, 0L);
                    destroyWindow.invoke(null, windowHandle);
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            }).get(5, TimeUnit.SECONDS);

            logger.info("Early display window destroyed");
        } catch (Exception e) {
            logger.warn("Failed to hide early display window; it may remain visible until the child process exits....", e);
        }
    }

    private static Object readField(Class<?> cls, String name, Object target) throws ReflectiveOperationException {
        var f = cls.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }
}
