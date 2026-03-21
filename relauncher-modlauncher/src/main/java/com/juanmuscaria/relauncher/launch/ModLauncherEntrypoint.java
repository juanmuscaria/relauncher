package com.juanmuscaria.relauncher.launch;

import com.juanmuscaria.relauncher.RelauncherConfig;
import com.juanmuscaria.relauncher.logger.Log4jLoggerAdapter;
import com.juanmuscaria.relauncher.logger.LoggerAdapter;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import org.apache.logging.log4j.LogManager;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Set;

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
        String[] handlerClasses = {
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
            Field providerField = handlerClass.getDeclaredField("provider");
            providerField.setAccessible(true);
            var provider = providerField.get(null);

            if (provider == null || provider.getClass().getName().contains("DummyProvider")) {
                return false;
            }

            var windowField = provider.getClass().getDeclaredField("window");
            windowField.setAccessible(true);
            long windowHandle = windowField.getLong(provider);

            if (windowHandle == 0) {
                return false;
            }

            var glfwClass = Class.forName("org.lwjgl.glfw.GLFW");
            glfwClass.getMethod("glfwDestroyWindow", long.class).invoke(null, windowHandle);

            logger.info("Early display window destroyed");
            return true;
        } catch (Exception e) {
            logger.warn("Failed to hide early display window; it may remain visible until the child process exits...", e);
            return false;
        }
    }
}
