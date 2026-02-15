package com.hotreload;

import com.hotreload.agent.HotReloadAgent;
import com.hotreload.core.HotReloadEngine;
import net.bytebuddy.agent.ByteBuddyAgent;

import java.lang.instrument.Instrumentation;

/**
 * Public API entry point for the Hot Reload library.
 *
 * <p>Preferred usage — with {@code -javaagent:hot-reload-core.jar} JVM flag
 * and {@code @EnableHotReload} on the main class:
 * <pre>{@code
 * @EnableHotReload(sourcePaths = {"src/main/java"})
 * public class MyApp {
 *     public static void main(String[] args) {
 *         // engine starts automatically, no explicit call needed
 *     }
 * }
 * }</pre>
 *
 * <p>Fallback usage — without java agent:
 * <pre>{@code
 * public class MyApp {
 *     public static void main(String[] args) {
 *         HotReload.start("src/main/java");
 *     }
 * }
 * }</pre>
 */
public class HotReload {

    private HotReload() {}

    /**
     * Starts the hot reload engine monitoring the given source paths.
     * Uses agent-provided Instrumentation if available, otherwise falls back
     * to dynamic attach via ByteBuddy.
     * If the engine is already running (e.g. started by the agent), this call is ignored.
     *
     * @param sourcePaths directories containing {@code .java} source files to watch
     */
    public static void start(String... sourcePaths) {
        Instrumentation inst = HotReloadAgent.getInstrumentation();
        if (inst == null) {
            inst = ByteBuddyAgent.install();
        }

        HotReloadEngine.startIfNotRunning(inst, sourcePaths);
    }
}
