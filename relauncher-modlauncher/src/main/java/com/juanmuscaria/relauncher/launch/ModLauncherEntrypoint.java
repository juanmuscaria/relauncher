// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.launch;

import com.juanmuscaria.relauncher.RelauncherConfig;
import com.juanmuscaria.relauncher.logger.Log4jLoggerAdapter;
import com.juanmuscaria.relauncher.logger.LoggerAdapter;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import org.apache.logging.log4j.LogManager;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Entry point for Forge 1.13.2+ and NeoForge up to 1.21.1, which use ModLauncher.
 * <p>
 * Discovered via SPI ({@code META-INF/services/cpw.mods.modlauncher.api.ITransformationService}).
 */
public class ModLauncherEntrypoint implements ITransformationService, Platform {
    private static final String LOGGER_NAME = "Relauncher";

    private final LoggerAdapter logger = new Log4jLoggerAdapter(LogManager.getLogger(LOGGER_NAME));
    private Path configDirectory;
    private Path modsDirectory;
    private Side side = Side.UNKNOWN;

    private static Side detectSide(IEnvironment env) {
        var launchTarget = env.getProperty(IEnvironment.Keys.LAUNCHTARGET.get()).orElse("");
        if (launchTarget.contains("client")) {
            return Side.CLIENT;
        } else if (launchTarget.contains("server")) {
            return Side.SERVER;
        }
        return Side.UNKNOWN;
    }

    @Override
    public String name() {
        return LOGGER_NAME;
    }

    @Override
    public void initialize(IEnvironment env) {
        var gameDir = env.getProperty(IEnvironment.Keys.GAMEDIR.get())
            .orElseGet(() -> Paths.get("."));
        this.configDirectory = gameDir.resolve("config/relauncher");
        this.modsDirectory = gameDir.resolve("mods");
        this.side = detectSide(env);

        if (register()) {
            logger.info("Relauncher loaded via ModLauncher");
            // Pass hideEarlyWindow as a pre-relaunch hook.
            RelauncherConfig.standaloneRelaunch(this, this::hideEarlyWindow);
        }
    }

    @Override
    public void beginScanning(IEnvironment env) {
    }

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) {
    }

    @SuppressWarnings("rawtypes")
    @Override
    public List<ITransformer> transformers() {
        return Collections.emptyList();
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
     * Destroys (Neo)Forge's early loading window so the user doesn't see a
     * frozen splash screen while the child process boots up.
     * <p>
     * FML's TransformationService initializes before ours, so the GLFW window
     * is already on screen by the time we reach {@code initialize()}. We tear
     * it down via reflection to avoid a compile-time dependency on Forge internals.
     * If it fails, the window just stays visible - ugly, but harmless.
     * <p>
     * Works with both Forge and NeoForge (same field layout, different package).
     */
    private void hideEarlyWindow() {
        // Try NeoForge first, then Forge; same field names, different package
        var handlerClasses = new String[]{
            "net.neoforged.fml.loading.ImmediateWindowHandler",
            "net.minecraftforge.fml.loading.ImmediateWindowHandler",
        };

        for (var handlerClassName : handlerClasses) {
            try {
                var handlerClass = Class.forName(handlerClassName);
                if (tryHideWindow(handlerClass)) {
                    return;
                }
            } catch (ClassNotFoundException ignored) {
                // Not this loader, try next
            }
        }
    }

    private boolean tryHideWindow(Class<?> handlerClass) {
        try {
            var providerField = handlerClass.getDeclaredField("provider");
            providerField.setAccessible(true);
            var provider = providerField.get(null);

            if (provider == null || provider.getClass().getName().contains("DummyProvider")) {
                return false;
            }

            var providerClass = provider.getClass();
            var windowHandle = (long) readField(providerClass, "window", provider);
            if (windowHandle == 0) {
                return false;
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
            return true;
        } catch (Exception e) {
            logger.warn("Failed to hide early display window; it may remain visible until the child process exits...", e);
            return false;
        }
    }

    private static Object readField(Class<?> cls, String name, Object target) throws ReflectiveOperationException {
        var f = cls.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }
}
