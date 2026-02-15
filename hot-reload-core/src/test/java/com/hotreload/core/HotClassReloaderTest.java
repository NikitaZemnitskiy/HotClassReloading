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

import static org.junit.jupiter.api.Assertions.*;

class HotClassReloaderTest {

    static Instrumentation instrumentation;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void installAgent() {
        instrumentation = ByteBuddyAgent.install();
    }

    @Test
    void reload_changedMethodBody_affectsExistingInstances() throws Exception {
        // This is the critical end-to-end test:
        // 1. Compile a class v1
        // 2. Load it
        // 3. Create an instance, verify v1 behavior
        // 4. Compile v2
        // 5. Redefine the class
        // 6. Call the SAME instance — should return v2

        Path packageDir = tempDir.resolve("com/hotreload/testsubject");
        Files.createDirectories(packageDir);

        Path outputDir = tempDir.resolve("out");
        Files.createDirectories(outputDir);

        // Write and compile v1
        Path sourceFile = packageDir.resolve("Subject.java");
        Files.writeString(sourceFile, """
            package com.hotreload.testsubject;
            public class Subject {
                public String value() { return "v1"; }
            }
            """);

        HotSourceCompiler compiler = new HotSourceCompiler(outputDir);
        byte[] v1Bytecode = compiler.compile(sourceFile);

        // Load class from outputDir using a URLClassLoader
        URLClassLoader loader = new URLClassLoader(
            new URL[]{outputDir.toUri().toURL()},
            getClass().getClassLoader()
        );
        Class<?> subjectClass = loader.loadClass("com.hotreload.testsubject.Subject");
        Object instance = subjectClass.getDeclaredConstructor().newInstance();

        // Verify v1
        String v1Result = (String) subjectClass.getMethod("value").invoke(instance);
        assertEquals("v1", v1Result, "Initial version should be v1");

        // Compile v2
        Files.writeString(sourceFile, """
            package com.hotreload.testsubject;
            public class Subject {
                public String value() { return "v2"; }
            }
            """);
        byte[] v2Bytecode = compiler.compile(sourceFile);

        // Redefine the class
        HotClassReloader reloader = new HotClassReloader(instrumentation);
        reloader.reload(subjectClass, v2Bytecode);

        // The SAME instance should now return v2
        String v2Result = (String) subjectClass.getMethod("value").invoke(instance);
        assertEquals("v2", v2Result,
            "After redefineClasses, the same instance should reflect the new method body");
    }
}
