// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher;

import com.juanmuscaria.relauncher.jvm.JavaRequirement;
import com.juanmuscaria.relauncher.jvm.JavaRuntimeProvider;
import com.juanmuscaria.relauncher.jvm.JvmImageType;
import com.juanmuscaria.relauncher.jvm.JvmVendor;
import com.juanmuscaria.relauncher.launch.Platform;
import com.juanmuscaria.relauncher.logger.LoggerAdapter;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarFile;

/**
 * Standalone config file, lets users add JVM args without writing code.
 * {@code #} comments, blank lines ignored, {@code enabled = true/false} plus a
 * few {@code java.*} keys, everything else is treated as a JVM arg.
 */
public final class RelauncherConfig {
    private static final String CONFIG_FILE_NAME = "config.cfg";
    private final boolean enabled;
    private final List<String> extraJvmArgs;
    private final List<Integer> javaVersions;
    private final List<JvmVendor> javaVendors;
    private final JvmImageType javaImageType;
    private final boolean javaAutoDownload;
    private final Path javaDownloadDir;

    private RelauncherConfig(boolean enabled, List<String> extraJvmArgs,
                             List<Integer> javaVersions, List<JvmVendor> javaVendors,
                             JvmImageType javaImageType,
                             boolean javaAutoDownload, Path javaDownloadDir) {
        this.enabled = enabled;
        this.extraJvmArgs = extraJvmArgs;
        this.javaVersions = javaVersions;
        this.javaVendors = javaVendors;
        this.javaImageType = javaImageType;
        this.javaAutoDownload = javaAutoDownload;
        this.javaDownloadDir = javaDownloadDir;
    }

    /** Reads config from {@code configDir}, writes a default disabled one on first run */
    public static RelauncherConfig load(Path configDir) throws IOException {
        var configFile = configDir.resolve(CONFIG_FILE_NAME);

        if (!Files.exists(configFile)) {
            createDefault(configFile);
            return new RelauncherConfig(false, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), JvmImageType.JRE,
                false, null);
        }

        var enabled = true;
        var args = new ArrayList<String>();
        var javaVersions = new ArrayList<Integer>();
        var javaVendors = new ArrayList<JvmVendor>();
        var javaImageType = JvmImageType.JRE;
        var javaAutoDownload = false;
        Path javaDownloadDir = null;

        try (var reader = Files.newBufferedReader(configFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                if (line.startsWith("enabled")) {
                    var eqIdx = line.indexOf('=');
                    if (eqIdx >= 0) {
                        enabled = Boolean.parseBoolean(line.substring(eqIdx + 1).trim());
                        continue;
                    }
                }

                if (line.startsWith("java.versions")) {
                    var eqIdx = line.indexOf('=');
                    if (eqIdx >= 0) {
                        javaVersions.addAll(parseMajors(line.substring(eqIdx + 1)));
                        continue;
                    }
                }

                if (line.startsWith("java.vendors")) {
                    var eqIdx = line.indexOf('=');
                    if (eqIdx >= 0) {
                        javaVendors.addAll(parseVendors(line.substring(eqIdx + 1)));
                        continue;
                    }
                }

                if (line.startsWith("java.imageType")) {
                    var eqIdx = line.indexOf('=');
                    if (eqIdx >= 0) {
                        var parsed = parseImageType(line.substring(eqIdx + 1));
                        if (parsed != null) javaImageType = parsed;
                        continue;
                    }
                }

                if (line.startsWith("java.autoDownload")) {
                    var eqIdx = line.indexOf('=');
                    if (eqIdx >= 0) {
                        javaAutoDownload = Boolean.parseBoolean(line.substring(eqIdx + 1).trim());
                        continue;
                    }
                }

                if (line.startsWith("java.downloadDir")) {
                    var eqIdx = line.indexOf('=');
                    if (eqIdx >= 0) {
                        var raw = line.substring(eqIdx + 1).trim();
                        if (!raw.isEmpty()) {
                            javaDownloadDir = Paths.get(raw);
                        }
                        continue;
                    }
                }

                // Everything else is a JVM argument
                args.add(line);
            }
        }

        return new RelauncherConfig(enabled,
            Collections.unmodifiableList(args),
            Collections.unmodifiableList(javaVersions),
            Collections.unmodifiableList(dedupe(javaVendors)),
            javaImageType,
            javaAutoDownload, javaDownloadDir);
    }

    private static List<Integer> parseMajors(String value) {
        var out = new ArrayList<Integer>();
        for (var token : value.split(",")) {
            var t = token.trim();
            if (t.isEmpty()) continue;
            try {
                out.add(Integer.parseInt(t));
            } catch (NumberFormatException e) {
                System.err.println("[Relauncher] Ignoring invalid java.versions token: " + t);
            }
        }
        return out;
    }

    private static List<JvmVendor> parseVendors(String value) {
        var out = new ArrayList<JvmVendor>();
        for (var token : value.split(",")) {
            var t = token.trim();
            if (t.isEmpty()) continue;
            var v = JvmVendor.fromConfigToken(t);
            if (v == null) {
                System.err.println("[Relauncher] Ignoring unknown java.vendors token: " + t);
            } else {
                out.add(v);
            }
        }
        return out;
    }

    private static JvmImageType parseImageType(String value) {
        var t = value.trim();
        if (t.equalsIgnoreCase("jdk")) return JvmImageType.JDK;
        if (t.equalsIgnoreCase("jre")) return JvmImageType.JRE;
        System.err.println("[Relauncher] Ignoring invalid java.imageType value: " + t);
        return null;
    }

    private static <T> List<T> dedupe(List<T> in) {
        var seen = new LinkedHashSet<>(in);
        return new ArrayList<>(seen);
    }

    /**
     * Auto-relaunch path used by loader entry points. Reads the config,
     * discovers SPI providers in the mods folder, merges and relaunches.
     * Bails early if already relaunched or there's nothing to do.
     */
    public static void standaloneRelaunch(Platform platform) {
        standaloneRelaunch(platform, null);
    }

    /**
     * Same as {@link #standaloneRelaunch(Platform)}, with a hook to run right
     * before relaunch, e.g. to hide Forge's early loading window so the user
     * doesn't see a frozen splash screen
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

        var argProviders = createArgProviderFromConfig(config);
        argProviders.addAll(discoverSpi(CommandLineProvider.class, platform, logger));

        var javaProviders = new ArrayList<JavaRuntimeProvider>();
        var configJavaProvider = config.toJavaRuntimeProvider();
        if (configJavaProvider != null) javaProviders.add(configJavaProvider);
        javaProviders.addAll(discoverSpi(JavaRuntimeProvider.class, platform, logger));

        if (argProviders.isEmpty() && javaProviders.isEmpty()) {
            logger.debug("No extra JVM args or Java requirements configured, skipping relaunch");
            return;
        }

        logger.info("Relaunch decision: " + argProviders.size() + " arg provider(s), "
            + javaProviders.size() + " Java-runtime provider(s)");

        if (beforeRelaunch != null) {
            beforeRelaunch.run();
        }

        var result = Relauncher.relaunch(argProviders, javaProviders);
        if (result.isFailed()) {
            if (result.cause() != null) {
                logger.error("Failed to relaunch JVM: " + result.reason(), result.cause());
            } else {
                logger.error("Failed to relaunch JVM: " + result.reason());
            }
            System.exit(1);
        } else if (result.isSkipped()) {
            logger.warn("Relaunch skipped: " + result.reason());
            // Don't exit, skip genuinely means "current JVM is fine, keep going"
        }
    }

    private static ArrayList<CommandLineProvider> createArgProviderFromConfig(RelauncherConfig config) {
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

    /** Visible for testing, exposes {@code discoverSpi} so the smoketest can assert it without relaunching */
    public static <T> List<T> discoverSpiForTesting(Class<T> spi, Platform platform, LoggerAdapter logger) {
        return discoverSpi(spi, platform, logger);
    }

    // We run before the mod loader sets up the classpath, so we manually scan
    // the mods folder for jars with the service file and load them through a
    // temp URLClassLoader. The initial pass walks several classloaders because
    // the thread context CL during early ModLauncher init doesn't expose this
    // mod's META-INF/services. Dedupe by class name so a jar already on the cp
    // doesn't get counted twice. {@code platform} and {@code logger} may be
    // null - the mods scan and logging are skipped respectively.
    static <T> List<T> discoverSpi(Class<T> spi, Platform platform, LoggerAdapter logger) {
        var seen = new HashSet<String>();
        var providers = new ArrayList<T>();

        var classLoaders = new ArrayList<ClassLoader>(3);
        classLoaders.add(spi.getClassLoader());
        classLoaders.add(Thread.currentThread().getContextClassLoader());
        classLoaders.add(ClassLoader.getSystemClassLoader());
        for (var cl : classLoaders) {
            if (cl == null) continue;
            try {
                for (var provider : ServiceLoader.load(spi, cl)) {
                    if (seen.add(provider.getClass().getName())) {
                        providers.add(provider);
                    }
                }
            } catch (Throwable ignored) {
                // Some classloaders reject ServiceLoader queries; skip them.
            }
        }

        if (platform == null) return providers;

        var modsDir = platform.modsDirectory();
        if (!Files.isDirectory(modsDir)) {
            return providers;
        }

        var serviceFileName = "META-INF/services/" + spi.getName();

        var modJarUrls = new ArrayList<URL>();
        try (var stream = Files.newDirectoryStream(modsDir, "*.jar")) {
            for (var jarPath : stream) {
                if (hasServiceEntry(jarPath, serviceFileName)) {
                    if (logger != null) {
                        logger.debug("Found " + spi.getSimpleName() + " service in " + jarPath.getFileName());
                    }
                    modJarUrls.add(jarPath.toUri().toURL());
                }
            }
        } catch (IOException e) {
            if (logger != null) {
                logger.warn("Failed to scan mods folder for " + spi.getSimpleName() + " services", e);
            }
            return providers;
        }

        if (modJarUrls.isEmpty()) return providers;

        var loader = new URLClassLoader(
            modJarUrls.toArray(new URL[0]),
            RelauncherConfig.class.getClassLoader());
        try {
            for (var provider : ServiceLoader.load(spi, loader)) {
                if (seen.add(provider.getClass().getName())) {
                    providers.add(provider);
                }
            }
        } catch (Exception e) {
            if (logger != null) {
                logger.warn("Failed to load " + spi.getSimpleName() + " services from mods folder", e);
            }
        }
        // Not closing the loader, providers need their classes alive until relaunch
        return providers;
    }

    private static boolean hasServiceEntry(Path jarPath, String serviceFileName) {
        try (var jar = new JarFile(jarPath.toFile(), false, JarFile.OPEN_READ)) {
            return jar.getEntry(serviceFileName) != null;
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
            "# -Dfoo=bar",
            "",
            "# Optional Java runtime requirement. Relauncher will switch to a",
            "# matching installation on the system if the current one doesn't",
            "# satisfy the rules below.",
            "",
            "# Comma-separated list of acceptable Java major versions.",
            "# java.versions = 17, 21",
            "",
            "# Comma-separated list of acceptable vendors (temurin, graalvm,",
            "# zulu, corretto, liberica, microsoft, semeru, sap_machine,",
            "# openjdk, oracle). Unset = any vendor.",
            "# java.vendors = temurin, graalvm",
            "",
            "# Minimum image type: jre (default) or jdk.",
            "# java.imageType = jre",
            "",
            "# Allow Relauncher to download Java when no local install matches.",
            "# Client: triggers a confirmation dialog. Server/headless: required (no prompt).",
            "# Can also be set via -Drelauncher.java.autoDownload=true (overrides this).",
            "# java.autoDownload = false",
            "",
            "# Where to place downloaded JDKs. Default uses platform conventions:",
            "#   Linux:   $XDG_DATA_HOME/relauncher/jdks  (fallback ~/.local/share/relauncher/jdks)",
            "#   macOS:   ~/Library/Application Support/relauncher/jdks",
            "#   Windows: %LOCALAPPDATA%\\relauncher\\jdks",
            "# For custom paths, -Drelauncher.java.downloadDir=/path is recommended (visible",
            "# to the detector for next-launch discovery).",
            "# java.downloadDir = /custom/path"
        ));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<String> getExtraJvmArgs() {
        return extraJvmArgs;
    }

    public List<Integer> getJavaVersions() {
        return javaVersions;
    }

    public List<JvmVendor> getJavaVendors() {
        return javaVendors;
    }

    public JvmImageType getJavaImageType() {
        return javaImageType;
    }

    public boolean isJavaAutoDownload() {
        return javaAutoDownload;
    }

    public Path getJavaDownloadDir() {
        return javaDownloadDir;
    }

    public boolean hasJavaRequirement() {
        return !javaVersions.isEmpty() || !javaVendors.isEmpty();
    }

    /**
     * Synthetic {@link JavaRuntimeProvider} from the config, null if no java
     * keys were set, or only non-major keys (majors are mandatory)
     */
    public JavaRuntimeProvider toJavaRuntimeProvider() {
        if (!hasJavaRequirement()) return null;
        if (javaVersions.isEmpty()) {
            System.err.println("[Relauncher] java.vendors or java.imageType set but java.versions is not - "
                + "ignoring Java requirement from config (majors are mandatory)");
            return null;
        }
        var req = JavaRequirement.of(javaVersions, javaVendors, javaImageType);
        return new JavaRuntimeProvider() {
            @Override
            public List<JavaRequirement> javaRequirements() {
                return Collections.singletonList(req);
            }

            @Override
            public int priority() {
                return Integer.MIN_VALUE;
            }
        };
    }
}
