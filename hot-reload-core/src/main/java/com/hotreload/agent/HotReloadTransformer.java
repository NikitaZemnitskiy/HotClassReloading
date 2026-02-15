package com.hotreload.agent;

import com.hotreload.annotation.EnableHotReload;
import com.hotreload.core.HotReloadEngine;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;
import java.security.ProtectionDomain;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HotReloadTransformer implements ClassFileTransformer {

    private static final Logger LOG = Logger.getLogger(HotReloadTransformer.class.getName());
    private static final byte[] ANNOTATION_PATTERN =
            "Lcom/hotreload/annotation/EnableHotReload;".getBytes(StandardCharsets.US_ASCII);

    private final Instrumentation instrumentation;
    private volatile boolean started;

    public HotReloadTransformer(Instrumentation instrumentation) {
        this.instrumentation = instrumentation;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (started || classBeingRedefined != null || loader == null) {
            return new byte[0];
        }

        if (containsAnnotation(classfileBuffer)) {
            started = true;
            String dotName = className.replace('/', '.');
            LOG.log(Level.INFO, "[HotReload] Found @EnableHotReload on {0}, scheduling engine start...", dotName);

            Thread starter = new Thread(() -> startEngine(loader, dotName), "hot-reload-starter");
            starter.setDaemon(true);
            starter.start();
        }

        return new byte[0];
    }

    private static boolean containsAnnotation(byte[] bytecode) {
        for (int i = 0; i <= bytecode.length - ANNOTATION_PATTERN.length; i++) {
            if (matchesAt(bytecode, i)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAt(byte[] bytecode, int offset) {
        for (int j = 0; j < ANNOTATION_PATTERN.length; j++) {
            if (bytecode[offset + j] != ANNOTATION_PATTERN[j]) {
                return false;
            }
        }
        return true;
    }

    private void startEngine(ClassLoader loader, String className) {
        try {
            Thread.sleep(500);

            Class<?> appClass = Class.forName(className, true, loader);
            EnableHotReload config = appClass.getAnnotation(EnableHotReload.class);
            if (config == null) {
                LOG.log(Level.WARNING, "[HotReload] @EnableHotReload not found on {0} at runtime", className);
                return;
            }

            new HotReloadEngine(instrumentation, config).start();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warning("[HotReload] Engine start interrupted");
        } catch (ReflectiveOperationException e) {
            LOG.log(Level.SEVERE, "[HotReload] Failed to auto-start engine", e);
        }
    }
}
