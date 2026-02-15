package com.hotreload.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class HotSourceCompilerTest {

    @TempDir
    Path tempDir;

    @Test
    void compile_simpleClass_returnsBytecode() throws Exception {
        // Arrange: write a simple .java file
        Path sourceDir = tempDir.resolve("src");
        Path packageDir = sourceDir.resolve("com/test");
        Files.createDirectories(packageDir);

        Path sourceFile = packageDir.resolve("Dummy.java");
        Files.writeString(sourceFile, """
            package com.test;
            public class Dummy {
                public String hello() { return "v1"; }
            }
            """);

        Path outputDir = tempDir.resolve("out");
        Files.createDirectories(outputDir);

        HotSourceCompiler compiler = new HotSourceCompiler(outputDir);

        // Act
        byte[] bytecode = compiler.compile(sourceFile);

        // Assert
        assertNotNull(bytecode, "Compiled bytecode should not be null");
        assertTrue(bytecode.length > 0, "Compiled bytecode should not be empty");
        // Verify the .class file was written to outputDir
        Path classFile = outputDir.resolve("com/test/Dummy.class");
        assertTrue(Files.exists(classFile), "Class file should exist at " + classFile);
    }

    @Test
    void compile_syntaxError_throwsException() throws Exception {
        Path sourceDir = tempDir.resolve("src");
        Files.createDirectories(sourceDir);

        Path sourceFile = sourceDir.resolve("Bad.java");
        Files.writeString(sourceFile, """
            public class Bad {
                this is not valid java
            }
            """);

        Path outputDir = tempDir.resolve("out");
        Files.createDirectories(outputDir);

        HotSourceCompiler compiler = new HotSourceCompiler(outputDir);

        assertThrows(IOException.class, () -> compiler.compile(sourceFile));
    }

    @Test
    void compile_updatedSource_returnsNewBytecode() throws Exception {
        Path sourceDir = tempDir.resolve("src");
        Path packageDir = sourceDir.resolve("com/test");
        Files.createDirectories(packageDir);

        Path sourceFile = packageDir.resolve("Versioned.java");
        Path outputDir = tempDir.resolve("out");
        Files.createDirectories(outputDir);

        HotSourceCompiler compiler = new HotSourceCompiler(outputDir);

        // Compile v1
        Files.writeString(sourceFile, """
            package com.test;
            public class Versioned {
                public String version() { return "v1"; }
            }
            """);
        byte[] v1 = compiler.compile(sourceFile);

        // Compile v2 (different method body)
        Files.writeString(sourceFile, """
            package com.test;
            public class Versioned {
                public String version() { return "v2"; }
            }
            """);
        byte[] v2 = compiler.compile(sourceFile);

        assertNotNull(v1);
        assertNotNull(v2);
        assertFalse(Arrays.equals(v1, v2),
            "Bytecode should differ between v1 and v2");
    }
}
