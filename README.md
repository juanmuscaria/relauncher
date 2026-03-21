# Relauncher

JVM relaunch library for Java based applications. Drop a single JAR into your mods folder,
and it should work from Minecraft 1.6.4 to the latest version, or on Hytale Server as an early plugin or regular plugin.

## What it does

Relauncher restarts the JVM process with additional arguments transparently and before the game loads.
This is useful for:

- **Adding JVM flags** - GC tuning, `-XX:+AlwaysPreTouch`, system properties, etc., without needing to touch launcher
  settings
- **Modpack tooling** - other mods can trigger a relaunch programmatically after installing dependencies or configuring
  the environment (javaagents, module layers, you name it)

The relaunch happens very early during startup, before any significant game state is initialized.
External launchers see a single continuous process with no extra windows, no orphaned processes.

## Supported Versions

| Loader                    | Minecraft Versions | Entry Point                                 |
|---------------------------|--------------------|---------------------------------------------|
| Forge (LaunchWrapper)     | 1.6.4 - 1.12.2     | `ITweaker` via manifest                     |
| Forge (ModLauncher)       | 1.13.2+            | `ITransformationService` via SPI            |
| NeoForge (ModLauncher)    | up to 1.21.1       | `ITransformationService` via SPI            |
| NeoForge (FancyModLoader) | 1.21.3+            | `IModFileCandidateLocator` via SPI          |
| Fabric                    | 1.14+              | `PreLaunchEntrypoint` via `fabric.mod.json` |
| Quilt                     | 1.14+              | Uses Fabric's `PreLaunchEntrypoint`         |
| Hytale Server (early)     | -                  | `ClassTransformer` SPI                      |
| Hytale Server (plugin)    | -                  | `JavaPlugin` via `manifest.json`            |

**Java versions**: 8 through 25

**Platforms**: Linux, Windows, and macOS, on both x86_64 and aarch64.

## Installation

1. Download the universal JAR (`relauncher-universal-<version>.jar`)
2. Drop it into your `mods/` folder
3. Launch the game once, it will create a config file at `config/relauncher/config.cfg`
4. Edit the config to add your JVM arguments and set `enabled = true`
5. Relaunch the game

## Configuration

The config file is at `<game directory>/config/relauncher/config.cfg`:

```cfg
# Relauncher Configuration
# Extra JVM arguments to add when relaunching, one per line.
# Lines starting with # are comments, blank lines are ignored.

# Set to false to disable relaunching without removing your arguments.
enabled = true

# Add extra JVM arguments below, one per line:
-XX:+UseG1GC
-XX:+AlwaysPreTouch
-Dsome.java.property=true
```

The config `enabled` flag only controls the config file's own arguments.
If another mod provides a `CommandLineProvider` SPI implementation, the relaunch will happen regardless of this flag.

## How it works

Relauncher tries three strategies to restart the JVM, in order of preference:

1. **POSIX exec** (Linux / macOS) - calls `execvp()` through a native Rust library to replace the current process
   in-place. Same PID, zero overhead, cleanest possible restart.

2. **Windows DLL** - hooks `DllMain(DLL_PROCESS_DETACH)` to spawn a child process *after* the JVM has fully shut down.
   stdin/stdout/stderr are duplicated beforehand so they survive the teardown. The parent waits for the child and
   forwards its exit code.

3. **Fallback** (pure Java) - starts a child process with inherited I/O, waits for it to finish, then halts. Works
   everywhere, but the original process sticks around for the duration of the game session.

## Launcher Compatibility

| Launcher      | Status                                                                                                                                                                                                  |
|---------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Vanilla       | Fully supported                                                                                                                                                                                         |
| PrismLauncher | Fully supported (system properties workaround)                                                                                                                                                          |
| CurseForge    | Fully supported (delegates to vanilla)                                                                                                                                                                  |
| Modrinth      | Fully supported (theseus wrapper workaround)                                                                                                                                                            |
| ATLauncher    | Fully supported                                                                                                                                                                                         |
| MultiMC       | **Not supported** - relies on a stdin protocol that can't be replayed after relaunch. A warning is logged and the relaunch is skipped. Consider switching launchers or applying the arguments manually. |

## For Mod Developers

### API dependency

Add `relauncher-core` as a compile-only dependency - the universal JAR will be present at runtime:

```gradle
dependencies {
    compileOnly 'com.juanmuscaria:relauncher-core:<version>'
}
```

### Programmatic relaunch

If your mod needs to restart the JVM (e.g., after installing a javaagent), call `Relauncher.relaunch()` directly:

```java
import com.juanmuscaria.relauncher.Relauncher;
import com.juanmuscaria.relauncher.RelaunchResult;

List<String> extraArgs = Arrays.asList("-Dfoo=bar", "-XX:+AlwaysPreTouch");
RelaunchResult result = Relauncher.relaunch(extraArgs);

// If we get here, the relaunch didn't happen
if (result.isFailed()) {
    logger.error("Relaunch failed: " + result.reason());
} else if (result.isSkipped()) {
    logger.warn("Relaunch skipped: " + result.reason());
}
```

### SPI provider

You can also inject JVM arguments without calling the API directly, just implement `CommandLineProvider` and register it
via ServiceLoader.
Relauncher scans the mods folder for service files on its own, so your mod doesn't even need to be on the classpath yet.

```java
import com.juanmuscaria.relauncher.CommandLineProvider;

public class MyProvider implements CommandLineProvider {
    @Override
    public List<String> extraJvmArguments() {
        return Collections.singletonList("-Dmy.mod.installed=true");
    }

    @Override
    public int priority() {
        return 0; // lower = runs first
    }
}
```

Register it in `META-INF/services/com.juanmuscaria.relauncher.CommandLineProvider`:

```
com.example.mymod.MyProvider
```

### Checking relaunch state

```java
Relauncher.isRelaunched()  // true if we're inside a relaunched JVM
Relauncher.getDepth()      // 0 = original, 1 = first relaunch, etc.
```

## Debugging

Add `-Drelauncher.debug=true` to your launcher's JVM arguments. This logs the full command-line extraction,
argument assembly, and strategy selection process. Useful for figuring out why a relaunch isn't working the way you
expect.

## Building from source

```bash
./gradlew build                           # Full build
./build-natives.sh                        # Cross-compile native libraries
```

You'll need Java 17+ (used as a toolchain; the output still targets Java 8) and Gradle 9.
For cross-compiling the native Rust libraries you'll also
need [cross](https://github.com/cross-rs/cross), [cargo-xwin](https://github.com/rust-cross/cargo-xwin),
and [cargo-zigbuild](https://github.com/rust-cross/cargo-zigbuild), and Zig toolchain.

## License

[Mozilla Public License 2.0](https://www.mozilla.org/en-US/MPL/2.0/)
