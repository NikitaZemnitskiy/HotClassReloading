package com.hotreload.agent;

import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.Test;

import java.lang.instrument.Instrumentation;

import static org.junit.jupiter.api.Assertions.*;

class HotReloadAgentTest {

    @Test
    void premain_storesInstrumentation() {
        Instrumentation inst = ByteBuddyAgent.install();

        // Simulate what JVM does when loading the agent
        HotReloadAgent.premain(null, inst);

        assertNotNull(HotReloadAgent.getInstrumentation(),
            "After premain, getInstrumentation() should return the saved instance");
        assertSame(inst, HotReloadAgent.getInstrumentation(),
            "Should return the exact same Instrumentation instance");
    }

    @Test
    void getInstrumentation_returnsNullOrValidInstance() {
        Instrumentation inst = HotReloadAgent.getInstrumentation();
        // The key contract: getInstrumentation() never throws, it either returns
        // the Instrumentation or null.
        // After premain has been called (possibly in another test), it should be non-null.
        assertDoesNotThrow(HotReloadAgent::getInstrumentation,
            "getInstrumentation() should never throw");
    }
}
