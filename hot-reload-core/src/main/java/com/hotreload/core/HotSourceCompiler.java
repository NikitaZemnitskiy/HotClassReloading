package com.hotreload.core;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class HotSourceCompiler {

    private final Path outputDir;
    private final JavaCompiler compiler;

    public HotSourceCompiler(Path outputDir) {
        this.outputDir = outputDir;
        this.compiler = ToolProvider.getSystemJavaCompiler();
        if (this.compiler == null) {
            throw new IllegalStateException(
                "Java compiler not available. Make sure you are running on a JDK, not a JRE.");
        }
    }

    public byte[] compile(Path sourceFile) throws IOException {
        String classpath = System.getProperty("java.class.path");

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager =
                 compiler.getStandardFileManager(diagnostics, null, null)) {

            Iterable<? extends JavaFileObject> compilationUnits =
                fileManager.getJavaFileObjects(sourceFile.toFile());

            List<String> options = List.of(
                "-classpath", classpath,
                "-d", outputDir.toAbsolutePath().toString()
            );

            JavaCompiler.CompilationTask task =
                compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits);

            if (!task.call()) {
                StringBuilder sb = new StringBuilder("Compilation failed for ").append(sourceFile).append(":\n");
                for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                    if (d.getKind() == Diagnostic.Kind.ERROR) {
                        sb.append(d).append('\n');
                    }
                }
                throw new IOException(sb.toString());
            }
        }

        String sourceFileName = sourceFile.getFileName().toString();
        String classFileName = sourceFileName.replace(".java", ".class");
        return findClassBytes(outputDir, classFileName);
    }

    private static byte[] findClassBytes(Path dir, String classFileName) throws IOException {
        try (var stream = Files.walk(dir)) {
            Path classFile = stream
                .filter(p -> p.getFileName().toString().equals(classFileName))
                .findFirst()
                .orElseThrow(() -> new IOException(
                    "Compiled class not found: " + classFileName + " under " + dir));
            return Files.readAllBytes(classFile);
        }
    }
}
