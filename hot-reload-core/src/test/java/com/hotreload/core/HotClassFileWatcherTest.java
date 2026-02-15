package com.hotreload.core;

import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HotClassFileWatcherTest {

    static Instrumentation instrumentation;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void installAgent() {
        instrumentation = ByteBuddyAgent.install();
    }

    @Test
    void watcher_detectsFileChange_andTriggersRecompileAndReload() throws Exception {
        // Setup source directory structure
        Path sourceRoot = tempDir.resolve("src");
        Path packageDir = sourceRoot.resolve("com/hotreload/watchsubject");
        Files.createDirectories(packageDir);

        Path outputDir = tempDir.resolve("out");
        Files.createDirectories(outputDir);

        // Write v1 source
        Path sourceFile = packageDir.resolve("WatchTarget.java");
        Files.writeString(sourceFile, """
            package com.hotreload.watchsubject;
            public class WatchTarget {
                public String value() { return "v1"; }
            }
            """);

        // Compile and load v1
        HotSourceCompiler compiler = new HotSourceCompiler(outputDir);
        compiler.compile(sourceFile);

        URLClassLoader loader = new URLClassLoader(
            new URL[]{outputDir.toUri().toURL()},
            getClass().getClassLoader()
        );
        Class<?> targetClass = loader.loadClass("com.hotreload.watchsubject.WatchTarget");
        Object instance = targetClass.getDeclaredConstructor().newInstance();

        // Verify v1
        assertEquals("v1", targetClass.getMethod("value").invoke(instance));

        // Setup watcher
        Map<Path, Class<?>> sourceToClass = new HashMap<>();
        sourceToClass.put(sourceFile.toAbsolutePath(), targetClass);

        HotClassReloader reloader = new HotClassReloader(instrumentation);

        HotClassFileWatcher watcher = new HotClassFileWatcher(
            new String[]{sourceRoot.toAbsolutePath().toString()},
            100, // fast poll
            sourceToClass,
            compiler,
            reloader
        );

        Thread watchThread = new Thread(watcher, "test-watcher");
        watchThread.setDaemon(true);
        watchThread.start();

        // Give WatchService time to register
        Thread.sleep(500);

        // Modify the source file → trigger reload
        Files.writeString(sourceFile, """
            package com.hotreload.watchsubject;
            public class WatchTarget {
                public String value() { return "v2"; }
            }
            """);

        // Wait for the watcher to pick up the change and reload
        boolean reloaded = false;
        for (int i = 0; i < 30; i++) { // up to 3 seconds
            Thread.sleep(100);
            String result = (String) targetClass.getMethod("value").invoke(instance);
            if ("v2".equals(result)) {
                reloaded = true;
                break;
            }
        }

        assertTrue(reloaded,
            "Watcher should detect the file change, recompile, and reload the class. " +
            "Current value: " + targetClass.getMethod("value").invoke(instance));
    }
}
