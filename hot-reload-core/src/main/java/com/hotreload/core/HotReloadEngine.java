package com.hotreload.core;

import com.hotreload.annotation.EnableHotReload;
import com.hotreload.annotation.HotReload;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HotReloadEngine {

    private static final Logger LOG = Logger.getLogger(HotReloadEngine.class.getName());

    private final Instrumentation instrumentation;
    private final EnableHotReload config;

    public HotReloadEngine(Instrumentation instrumentation, EnableHotReload config) {
        this.instrumentation = instrumentation;
        this.config = config;
    }

    public void start() {
        Map<Path, Class<?>> sourceToClass = buildSourceMap();
        if (sourceToClass.isEmpty()) {
            LOG.warning("[HotReload] No @HotReload classes found. Nothing to watch.");
            return;
        }

        Path outputDir = resolveOutputDir();
        HotSourceCompiler compiler = new HotSourceCompiler(outputDir);
        HotClassReloader reloader = new HotClassReloader(instrumentation);

        HotClassFileWatcher watcher = new HotClassFileWatcher(
            config.sourcePaths(),
            config.pollIntervalMs(),
            sourceToClass,
            compiler,
            reloader
        );

        Thread watchThread = new Thread(watcher, "hot-reload-watcher");
        watchThread.setDaemon(true);
        watchThread.start();

        LOG.log(Level.INFO, "[HotReload] Engine started. Monitoring {0} class(es) across paths: {1}",
            new Object[]{sourceToClass.size(), String.join(", ", config.sourcePaths())});
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
        for (String sourcePath : config.sourcePaths()) {
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
