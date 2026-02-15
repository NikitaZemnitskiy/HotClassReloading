# HotClassReloading

A lightweight Java library that reloads classes at runtime without restarting the application. Edit a `.java` file, save it, and see the changes instantly.

## Quick Start

**1. Annotate your main class:**

```java
@EnableHotReload(sourcePaths = {"src/main/java"}, pollIntervalMs = 500)
public class MyApp {
    public static void main(String[] args) {
        // your code — no special initialization needed
    }
}
```

**2. Mark reloadable classes:**

```java
@HotReload
public class GreetingService {
    public String greet() {
        return "Hello from version 1!";
    }
}
```

**3. Run with the agent:**

```
java -javaagent:hot-reload-core-1.0-SNAPSHOT.jar -cp ... MyApp
```

Now edit `GreetingService.greet()`, save — the running application picks up the change automatically.

## Architecture

```
hot-reload-parent (pom)
├── hot-reload-core   — the library
└── demo-app          — example application
```

### How It Works

```
JVM starts with -javaagent:hot-reload-core.jar
 │
 ▼
HotReloadAgent.premain()
 ├── Captures Instrumentation from the JVM
 └── Registers HotReloadTransformer (ClassFileTransformer)
      │
      ▼
      Intercepts class loading, scans bytecode
      for @EnableHotReload annotation descriptor
      │
      ▼
      Found → schedules HotReloadEngine start
               │
               ▼
         HotReloadEngine.buildSourceMap()
          ├── Scans all loaded classes via Instrumentation.getAllLoadedClasses()
          ├── Filters by @HotReload annotation
          └── Maps each class to its .java source file using configured sourcePaths
               │
               ▼
         HotClassFileWatcher (daemon thread)
          ├── Monitors source directories via NIO WatchService
          └── On .java file change:
               │
               ├── HotSourceCompiler.compile()
               │    └── javax.tools.JavaCompiler recompiles the file → new bytecode
               │
               └── HotClassReloader.reload()
                    └── Instrumentation.redefineClasses() swaps bytecode in the running JVM
```

### Core Components

| Class | Role |
|-------|------|
| `HotReloadAgent` | Java agent entry point (`premain` / `agentmain`). Captures `Instrumentation` and registers the transformer. |
| `HotReloadTransformer` | `ClassFileTransformer` that detects `@EnableHotReload` during class loading and auto-starts the engine. |
| `HotReloadEngine` | Builds a source-to-class map from loaded `@HotReload` classes, then starts the file watcher. |
| `HotClassFileWatcher` | NIO `WatchService` wrapper. Recursively monitors source directories for `.java` changes. |
| `HotSourceCompiler` | Compiles changed `.java` files using `ToolProvider.getSystemJavaCompiler()`. |
| `HotClassReloader` | Redefines classes in the running JVM via `Instrumentation.redefineClasses()`. |

### Annotations

| Annotation | Target | Purpose |
|------------|--------|---------|
| `@EnableHotReload` | Main class | Enables the engine. Options: `sourcePaths` (default `src/main/java`), `pollIntervalMs` (default `500`). |
| `@HotReload` | Any class | Marks the class for hot-reloading. |

### Fallback Mode (no agent)

If you can't use `-javaagent`, call `HotReload.start()` explicitly:

```java
@EnableHotReload(sourcePaths = {"src/main/java"})
public class MyApp {
    public static void main(String[] args) {
        HotReload.start(MyApp.class); // attaches dynamically via ByteBuddy
    }
}
```

## Build

Requires **JDK 17+** (not JRE — the library needs `javax.tools.JavaCompiler` at runtime).

```bash
./mvnw clean install     # Unix
mvnw.cmd clean install   # Windows
```

## Limitations

- **Method bodies only** — `redefineClasses()` cannot add/remove fields or methods. Structural changes require a restart.
- **Classes must be loaded first** — the engine scans `getAllLoadedClasses()`, so `@HotReload` classes must be loaded before the engine starts. With the agent this happens automatically; with `HotReload.start()`, instantiate your classes before calling `start()`.
- **JDK required** — the runtime compiler (`javax.tools.JavaCompiler`) is only available in JDK distributions.

## License

MIT
