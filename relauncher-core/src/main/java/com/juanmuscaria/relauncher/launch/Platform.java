package com.juanmuscaria.relauncher.launch;

import com.juanmuscaria.relauncher.logger.LoggerAdapter;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Abstracts away the differences between mod loaders so the core relaunch
 * logic doesn't need to know whether it's running under Fabric, Forge, or NeoForge.
 */
public interface Platform {

    /**
     * The platform that was registered first (there should only ever be one).
     *
     * @throws IllegalStateException if no platform has registered yet
     */
    static Platform current() {
        var platform = Holder.CURRENT.get();
        if (platform == null) {
            throw new IllegalStateException("Platform not yet registered");
        }
        return platform;
    }

    /**
     * Claims this platform as the active one. Only the first caller wins.
     * This prevents double-init when multiple loaders are somehow present.
     *
     * @return true if registered, false if someone else got there first
     */
    default boolean register() {
        return Holder.CURRENT.compareAndSet(null, this);
    }

    /**
     * A logger that routes to whatever logging framework the current loader uses.
     */
    LoggerAdapter logger();

    /**
     * Where our config lives, typically {@code <gameDir>/config/relauncher/}.
     */
    Path configDirectory();

    /**
     * The mods folder, typically {@code <gameDir>/mods/}.
     */
    Path modsDirectory();

    /**
     * Client, server, or unknown.
     */
    Side side();
}

class Holder {
    static final AtomicReference<Platform> CURRENT = new AtomicReference<>();
}
