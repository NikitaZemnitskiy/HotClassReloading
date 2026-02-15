# HotClassReloading

A lightweight Java library that reloads classes at runtime without restarting the application. Edit a `.java` file, save it, and see the changes instantly.

## Quick Start

**1. Mark reloadable classes:**

```java
@HotReload
public class GreetingService {
    public String greet() {
        return "Hello from version 1!";
    }
}
```

**2. Run with the agent (preferred):**

Add `@EnableHotReload` to your main class if your source path differs from the default `src/main/java`:

```java
@EnableHotReload(sourcePaths = {"my-module/src/main/java"})
public class MyApp {
    public static void main(String[] args) {
        // no special initialization needed — engine starts automatically
    }
}
```

```
java -javaagent:hot-reload-core-1.0-SNAPSHOT.jar -cp ... MyApp
```

If your sources are in the standard `src/main/java`, the annotation is optional — the agent will use default paths.

**3. Or start manually (fallback, no agent):**

```java
public class MyApp {
    public static void main(String[] args) {
        new GreetingService(); // ensure @HotReload classes are loaded
        HotReload.start("src/main/java"); // attaches dynamically via ByteBuddy
    }
}
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
      Found → reads sourcePaths from annotation (or uses default)
              → schedules HotReloadEngine start
               │
               ▼
         HotReloadEngine.startIfNotRunning()
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
| `HotReloadEngine` | Builds a source-to-class map from loaded `@HotReload` classes, then starts the file watcher. Protected from double-start via `startIfNotRunning()`. |
| `HotClassFileWatcher` | NIO `WatchService` wrapper. Recursively monitors source directories for `.java` changes. |
| `HotSourceCompiler` | Compiles changed `.java` files using `ToolProvider.getSystemJavaCompiler()`. |
| `HotClassReloader` | Redefines classes in the running JVM via `Instrumentation.redefineClasses()`. |

### Annotations

| Annotation | Target | Purpose |
|------------|--------|---------|
| `@EnableHotReload` | Main class | Configures source paths for the agent. Optional if using the default `src/main/java`. Attribute: `sourcePaths` (default `{"src/main/java"}`). |
| `@HotReload` | Any class | Marks the class for hot-reloading. |

## Build

Requires **JDK 17+** (not JRE — the library needs `javax.tools.JavaCompiler` at runtime).

```bash
./mvnw clean install   
```
```bash
java -javaagent:hot-reload-core/target/hot-reload-core-1.0-SNAPSHOT.jar -jar .\demo-app\target\demo-app-1.0-SNAPSHOT.jar
```


## Limitations

- **Method bodies only** — `redefineClasses()` cannot add/remove fields or methods. Structural changes require a restart.
- **Classes must be loaded first** — the engine scans `getAllLoadedClasses()`, so `@HotReload` classes must be loaded before the engine starts. With the agent this happens automatically; with `HotReload.start()`, instantiate your classes before calling `start()`.
- **JDK required** — the runtime compiler (`javax.tools.JavaCompiler`) is only available in JDK distributions.

