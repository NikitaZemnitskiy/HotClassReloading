package com.hotreload;

import com.hotreload.agent.HotReloadAgent;
import com.hotreload.annotation.EnableHotReload;
import com.hotreload.core.HotReloadEngine;
import net.bytebuddy.agent.ByteBuddyAgent;

import java.lang.instrument.Instrumentation;

/**
 * Public API entry point for the Hot Reload library.
 *
 * <p>Preferred usage — with {@code -javaagent:hot-reload-core.jar} JVM flag:
 * <pre>{@code
 * @EnableHotReload
 * public class MyApp {
 *     public static void main(String[] args) {
 *         // engine starts automatically, no explicit call needed
 *     }
 * }
 * }</pre>
 *
 * <p>Fallback usage — without java agent:
 * <pre>{@code
 * @EnableHotReload
 * public class MyApp {
 *     public static void main(String[] args) {
 *         HotReload.start(MyApp.class);
 *     }
 * }
 * }</pre>
 */
public class HotReload {

    private HotReload() {}

    /**
     * Manually starts the hot reload engine for the given application class.
     * Uses agent-provided Instrumentation if available, otherwise falls back
     * to dynamic attach via ByteBuddy.
     *
     * @param appClass the main application class carrying {@code @EnableHotReload}
     * @throws IllegalArgumentException if the class lacks the {@code @EnableHotReload} annotation
     */
    public static void start(Class<?> appClass) {
        EnableHotReload config = appClass.getAnnotation(EnableHotReload.class);
        if (config == null) {
            throw new IllegalArgumentException(
                appClass.getName() + " must be annotated with @EnableHotReload");
        }

        Instrumentation inst = HotReloadAgent.getInstrumentation();
        if (inst == null) {
            inst = ByteBuddyAgent.install();
        }

        new HotReloadEngine(inst, config).start();
    }
}
