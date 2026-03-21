package com.juanmuscaria.relauncher;

import com.juanmuscaria.relauncher.launch.Platform;
import com.juanmuscaria.relauncher.logger.LoggerAdapter;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;

/**
 * Handles the standalone config file that lets users add JVM arguments
 * without writing code.
 * <p>
 * The format is deliberately simple: comment lines start with {@code #},
 * blank lines are ignored, {@code enabled = true/false} is the only setting,
 * and everything else is treated as a JVM argument.
 */
public final class RelauncherConfig {
    private static final String CONFIG_FILE_NAME = "config.cfg";
    private static final String SPI_SERVICE_FILE =
        "META-INF/services/" + CommandLineProvider.class.getName();
    private final boolean enabled;
    private final List<String> extraJvmArgs;

    private RelauncherConfig(boolean enabled, List<String> extraJvmArgs) {
        this.enabled = enabled;
        this.extraJvmArgs = extraJvmArgs;
    }

    /**
     * Reads the config from the given directory. On first run the file won't
     * exist yet, so we create a default (disabled) one for the user to edit.
     */
    public static RelauncherConfig load(Path configDir) throws IOException {
        var configFile = configDir.resolve(CONFIG_FILE_NAME);

        if (!Files.exists(configFile)) {
            createDefault(configFile);
            return new RelauncherConfig(false, Collections.emptyList());
        }

        boolean enabled = true;
        var args = new ArrayList<String>();

        try (var reader = Files.newBufferedReader(configFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // Skip comments and blank lines
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                // Parse key=value settings
                if (line.startsWith("enabled")) {
                    var eqIdx = line.indexOf('=');
                    if (eqIdx >= 0) {
                        enabled = Boolean.parseBoolean(line.substring(eqIdx + 1).trim());
                        continue;
                    }
                }

                // Everything else is a JVM argument
                args.add(line);
            }
        }

        return new RelauncherConfig(enabled, Collections.unmodifiableList(args));
    }

    /**
     * The main "auto-relaunch" path used by loader entry points.
     * <p>
     * Reads the config, discovers SPI providers from the mods folder, merges
     * everything together, and relaunches if there's anything to add. Bails
     * out early if we've already been relaunched or there's nothing to do.
     */
    public static void standaloneRelaunch(Platform platform) {
        standaloneRelaunch(platform, null);
    }

    /**
     * Same as {@link #standaloneRelaunch(Platform)}, but with a hook that runs
     * right before the relaunch - useful for cleanup like hiding Forge's early
     * loading window so the user doesn't see a frozen splash screen.
     */
    public static void standaloneRelaunch(Platform platform, Runnable beforeRelaunch) {
        if (Relauncher.isRelaunched()) {
            return;
        }

        var logger = platform.logger();

        RelauncherConfig config;
        try {
            config = load(platform.configDirectory());
        } catch (IOException e) {
            logger.error("Failed to load config", e);
            return;
        }

        var providers = createProviderFromConfig(config);

        var spiProviders = discoverProviders(platform, logger);
        providers.addAll(spiProviders);

        if (providers.isEmpty()) {
            logger.debug("No extra JVM args configured, skipping relaunch");
            return;
        }

        logger.info("Relaunching JVM with extra arguments from config and " +
            spiProviders.size() + " SPI provider(s)");

        if (beforeRelaunch != null) {
            beforeRelaunch.run();
        }

        var result = Relauncher.relaunch(providers);
        if (result.isFailed()) {
            if (result.cause() != null) {
                logger.error("Failed to relaunch JVM: " + result.reason(), result.cause());
            } else {
                logger.error("Failed to relaunch JVM: " + result.reason());
            }
        } else if (result.isSkipped()) {
            logger.warn("Relaunch skipped: " + result.reason());
        }
        System.exit(1);
    }

    private static ArrayList<CommandLineProvider> createProviderFromConfig(RelauncherConfig config) {
        var providers = new ArrayList<CommandLineProvider>();

        if (config.isEnabled() && !config.getExtraJvmArgs().isEmpty()) {
            var configArgs = Collections.unmodifiableList(config.getExtraJvmArgs());
            providers.add(new CommandLineProvider() {
                @Override
                public List<String> extraJvmArguments() {
                    return configArgs;
                }

                @Override
                public int priority() {
                    return Integer.MIN_VALUE;
                }
            });
        }
        return providers;
    }

    /**
     * Finds {@link CommandLineProvider} implementations on the classpath and
     * in the mods folder.
     * <p>
     * Because we run before mod loaders have set up the classpath, we manually
     * scan the mods directory for JARs containing the SPI service file and
     * load them through a temporary {@link URLClassLoader}. Providers are
     * deduplicated by class name so a JAR that's already on the classpath
     * doesn't get counted twice.
     */
    private static List<CommandLineProvider> discoverProviders(Platform platform, LoggerAdapter logger) {
        var seen = new HashSet<String>();
        var providers = new ArrayList<CommandLineProvider>();

        for (var provider : ServiceLoader.load(CommandLineProvider.class)) {
            if (seen.add(provider.getClass().getName())) {
                providers.add(provider);
            }
        }

        var modsDir = platform.modsDirectory();
        if (!Files.isDirectory(modsDir)) {
            return providers;
        }

        var modJarUrls = new ArrayList<URL>();
        try (var stream = Files.newDirectoryStream(modsDir, "*.jar")) {
            for (var jarPath : stream) {
                if (hasServiceEntry(jarPath)) {
                    logger.debug("Found CommandLineProvider service in " + jarPath.getFileName());
                    modJarUrls.add(jarPath.toUri().toURL());
                }
            }
        } catch (IOException e) {
            logger.warn("Failed to scan mods folder for CommandLineProvider services", e);
            return providers;
        }

        if (modJarUrls.isEmpty()) {
            return providers;
        }

        var loader = new URLClassLoader(
            modJarUrls.toArray(new URL[0]),
            RelauncherConfig.class.getClassLoader());
        try {
            for (var provider : ServiceLoader.load(CommandLineProvider.class, loader)) {
                if (seen.add(provider.getClass().getName())) {
                    providers.add(provider);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to load CommandLineProvider services from mods folder", e);
        }
        // Intentionally not closing the URLClassLoader as the providers are live objects
        // whose classes must remain loadable for the duration of the relaunch call.

        return providers;
    }

    /**
     * Quick check for the SPI service file inside a JAR, without fully loading it.
     */
    private static boolean hasServiceEntry(Path jarPath) {
        try (var jar = new JarFile(jarPath.toFile(), false, JarFile.OPEN_READ)) {
            return jar.getEntry(SPI_SERVICE_FILE) != null;
        } catch (IOException e) {
            return false;
        }
    }

    private static void createDefault(Path configFile) throws IOException {
        Files.createDirectories(configFile.getParent());
        Files.write(configFile, Arrays.asList(
            "# Relauncher Configuration",
            "# Extra JVM arguments to add when relaunching, one per line.",
            "# Lines starting with # are comments, blank lines are ignored.",
            "",
            "# Set to false to disable relaunching without removing your arguments.",
            "enabled = false",
            "",
            "# Add extra JVM arguments below, one per line:",
            "# -XX:+AlwaysPreTouch",
            "# -XX:+UseG1GC",
            "# -Dfoo=bar"
        ));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<String> getExtraJvmArgs() {
        return extraJvmArgs;
    }
}
