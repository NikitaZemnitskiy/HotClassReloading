package com.hotreload.core;

import com.hotreload.annotation.HotReload;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HotReloadEngine {

    private static final Logger LOG = Logger.getLogger(HotReloadEngine.class.getName());
    private static final long DEFAULT_POLL_INTERVAL_MS = 500;
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    public static final String[] DEFAULT_SOURCE_PATHS = {"src/main/java"};

    private final Instrumentation instrumentation;
    private final String[] sourcePaths;
    private final long pollIntervalMs;

    public HotReloadEngine(Instrumentation instrumentation, String[] sourcePaths, long pollIntervalMs) {
        this.instrumentation = instrumentation;
        this.sourcePaths = sourcePaths.clone();
        this.pollIntervalMs = pollIntervalMs;
    }

    public static void startIfNotRunning(Instrumentation instrumentation, String[] sourcePaths) {
        if (RUNNING.compareAndSet(false, true)) {
            new HotReloadEngine(instrumentation, sourcePaths, DEFAULT_POLL_INTERVAL_MS).start();
        } else {
            LOG.info("[HotReload] Engine already running, skipping start.");
        }
    }

    public void start() {
        RUNNING.set(true);

        Map<Path, Class<?>> sourceToClass = buildSourceMap();
        if (sourceToClass.isEmpty()) {
            LOG.warning("[HotReload] No @HotReload classes found. Nothing to watch.");
            return;
        }

        Path outputDir = resolveOutputDir();
        HotSourceCompiler compiler = new HotSourceCompiler(outputDir);
        HotClassReloader reloader = new HotClassReloader(instrumentation);

        HotClassFileWatcher watcher = new HotClassFileWatcher(
            sourcePaths,
            pollIntervalMs,
            sourceToClass,
            compiler,
            reloader
        );

        Thread watchThread = new Thread(watcher, "hot-reload-watcher");
        watchThread.setDaemon(true);
        watchThread.start();

        LOG.log(Level.INFO, "[HotReload] Engine started. Monitoring {0} class(es) across paths: {1}",
            new Object[]{sourceToClass.size(), String.join(", ", sourcePaths)});
    }

    private Map<Path, Class<?>> buildSourceMap() {
        Map<Path, Class<?>> map = new HashMap<>();

        for (Class<?> clazz : instrumentation.getAllLoadedClasses()) {
            if (clazz.isAnnotationPresent(HotReload.class)) {
                Path sourceFile = findSourceFile(clazz);
                if (sourceFile != null) {
                    map.put(sourceFile, clazz);
                    LOG.log(Level.FINE, "[HotReload] Mapped {0} -> {1}",
                        new Object[]{clazz.getName(), sourceFile});
                } else {
                    LOG.log(Level.WARNING, "[HotReload] Could not locate source for {0}", clazz.getName());
                }
            }
        }
        return map;
    }

    private Path findSourceFile(Class<?> clazz) {
        String relativePath = clazz.getName().replace('.', '/') + ".java";
        for (String sourcePath : sourcePaths) {
            Path candidate = Paths.get(sourcePath).toAbsolutePath().resolve(relativePath);
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static Path resolveOutputDir() {
        Path outputDir = Paths.get("target", "classes").toAbsolutePath();
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "[HotReload] Could not create output directory: {0}", outputDir);
        }
        return outputDir;
    }
}
