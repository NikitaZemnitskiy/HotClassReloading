package com.hotreload.core;

import java.io.IOException;
import java.lang.instrument.UnmodifiableClassException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

public class HotClassFileWatcher implements Runnable {

    private static final Logger LOG = Logger.getLogger(HotClassFileWatcher.class.getName());

    private final String[] sourcePaths;
    private final long pollIntervalMs;
    private final Map<Path, Class<?>> sourceToClass;
    private final HotSourceCompiler compiler;
    private final HotClassReloader reloader;

    public HotClassFileWatcher(
            String[] sourcePaths,
            long pollIntervalMs,
            Map<Path, Class<?>> sourceToClass,
            HotSourceCompiler compiler,
            HotClassReloader reloader) {
        this.sourcePaths = sourcePaths.clone();
        this.pollIntervalMs = pollIntervalMs;
        this.sourceToClass = new HashMap<>(sourceToClass);
        this.compiler = compiler;
        this.reloader = reloader;
    }

    @Override
    public void run() {
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            Map<WatchKey, Path> keyToDir = new HashMap<>();

            for (String sourcePath : sourcePaths) {
                Path dir = Paths.get(sourcePath).toAbsolutePath();
                registerAll(dir, watchService, keyToDir);
            }

            LOG.log(Level.INFO, "[HotReload] Watching for changes in: {0}", String.join(", ", sourcePaths));

            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key;
                try {
                    key = watchService.poll(pollIntervalMs, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                if (key == null) continue;

                Path dir = keyToDir.get(key);
                if (dir == null) {
                    key.cancel();
                    continue;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                    @SuppressWarnings("unchecked")
                    Path changed = dir.resolve(((WatchEvent<Path>) event).context());

                    if ((kind == ENTRY_MODIFY || kind == ENTRY_CREATE)
                            && changed.toString().endsWith(".java")) {
                        handleChange(changed);
                    }
                }

                key.reset();
            }
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "[HotReload] WatchService error", e);
        }
    }

    private void handleChange(Path changedFile) {
        Class<?> targetClass = sourceToClass.get(changedFile);
        if (targetClass == null) {
            String fileName = changedFile.getFileName().toString();
            for (Map.Entry<Path, Class<?>> entry : sourceToClass.entrySet()) {
                if (entry.getKey().getFileName().toString().equals(fileName)) {
                    targetClass = entry.getValue();
                    break;
                }
            }
        }

        if (targetClass == null) {
            LOG.log(Level.FINE, "[HotReload] No @HotReload class mapped for: {0}", changedFile);
            return;
        }

        LOG.log(Level.INFO, "[HotReload] Detected change in: {0}", changedFile.getFileName());

        try {
            byte[] newBytecode = compiler.compile(changedFile);
            reloader.reload(targetClass, newBytecode);
            LOG.log(Level.INFO, "[HotReload] Successfully reloaded: {0}", targetClass.getName());
        } catch (IOException | ClassNotFoundException | UnmodifiableClassException e) {
            LOG.log(Level.WARNING, "[HotReload] Reload failed for " + targetClass.getName(), e);
        }
    }

    private static void registerAll(Path dir, WatchService watchService, Map<WatchKey, Path> keyToDir)
            throws IOException {
        if (!Files.isDirectory(dir)) return;
        try (var stream = Files.walk(dir)) {
            stream.filter(Files::isDirectory).forEach(d -> {
                try {
                    WatchKey key = d.register(watchService, ENTRY_MODIFY, ENTRY_CREATE);
                    keyToDir.put(key, d);
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "[HotReload] Cannot watch directory: " + d, e);
                }
            });
        }
    }
}
