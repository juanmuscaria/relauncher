// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.smoketest.harness;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

// Stub Launch that works on Java 9+, the real one casts AppClassLoader to
// URLClassLoader and crashes
public final class StubLaunchWrapperLauncher {

    public static void main(String[] args) throws Exception {
        var version = "unknown";
        String tweakClass = null;
        var gameDir = new File(".");

        for (var i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--tweakClass":
                    if (i + 1 < args.length) tweakClass = args[++i];
                    break;
                case "--version":
                    if (i + 1 < args.length) version = args[++i];
                    break;
                case "--gameDir":
                    if (i + 1 < args.length) gameDir = new File(args[++i]);
                    break;
            }
        }

        if (tweakClass == null) {
            throw new IllegalArgumentException("--tweakClass is required");
        }

        var urls = getClasspathUrls();
        var classLoader = new LaunchClassLoader(urls);
        Thread.currentThread().setContextClassLoader(classLoader);

        Launch.blackboard = new HashMap<>();
        Launch.classLoader = classLoader;
        Launch.minecraftHome = gameDir;

        List<String> tweakClassNames = new ArrayList<>(Collections.singletonList(tweakClass));
        Launch.blackboard.put("TweakClasses", tweakClassNames);
        List<String> argumentList = new ArrayList<>();
        Launch.blackboard.put("ArgumentList", argumentList);

        Set<String> visited = new HashSet<>();
        List<ITweaker> allTweakers = new ArrayList<>();
        ITweaker primaryTweaker = null;

        do {
            List<ITweaker> batch = new ArrayList<>();
            for (var it = tweakClassNames.iterator(); it.hasNext(); ) {
                var name = it.next();
                if (visited.contains(name)) {
                    it.remove();
                    continue;
                }
                visited.add(name);
                it.remove();

                // load the tweaker pkg from the parent loader so the interface cast works across both
                var pkg = name.substring(0, name.lastIndexOf('.'));
                classLoader.addClassLoaderExclusion(pkg);

                var tweaker = (ITweaker) Class.forName(name, true, classLoader).newInstance();
                batch.add(tweaker);
                if (primaryTweaker == null) primaryTweaker = tweaker;
            }

            for (var tweaker : batch) {
                tweaker.acceptOptions(Collections.emptyList(), gameDir, null, version);
                tweaker.injectIntoClassLoader(classLoader);
                allTweakers.add(tweaker);
            }
        } while (!tweakClassNames.isEmpty());

        for (var tweaker : allTweakers) {
            argumentList.addAll(Arrays.asList(tweaker.getLaunchArguments()));
        }

        assert primaryTweaker != null;
        var launchTarget = primaryTweaker.getLaunchTarget();
        if (launchTarget == null) {
            // tweaker didn't want to launch, e.g. already relaunched
            return;
        }

        var targetClass = Class.forName(launchTarget, false, classLoader);
        var mainMethod = targetClass.getMethod("main", String[].class);
        mainMethod.invoke(null, (Object) argumentList.toArray(new String[0]));
    }

    private static URL[] getClasspathUrls() {
        // on Java 8 the system class loader is a URLClassLoader, grab it directly
        var cl = ClassLoader.getSystemClassLoader();
        if (cl instanceof URLClassLoader) {
            return ((URLClassLoader) cl).getURLs();
        }
        // on Java 9+, fall back to parsing java.class.path
        var cp = System.getProperty("java.class.path", "");
        if (cp.isEmpty()) return new URL[0];
        var entries = cp.split(File.pathSeparator, -1);
        List<URL> urls = new ArrayList<>(entries.length);
        for (var entry : entries) {
            try {
                urls.add(new File(entry).toURI().toURL());
            } catch (Exception ignored) { /* skip bad entries */ }
        }
        return urls.toArray(new URL[0]);
    }
}
