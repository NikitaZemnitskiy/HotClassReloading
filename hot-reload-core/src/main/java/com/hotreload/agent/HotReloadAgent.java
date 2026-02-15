package com.hotreload.agent;

import java.lang.instrument.Instrumentation;

public class HotReloadAgent {

    private static Instrumentation instrumentation;

    private HotReloadAgent() {}

    public static void premain(String args, Instrumentation inst) {
        instrumentation = inst;
        inst.addTransformer(new HotReloadTransformer(inst), false);
    }

    public static void agentmain(String args, Instrumentation inst) {
        premain(args, inst);
    }

    public static Instrumentation getInstrumentation() {
        return instrumentation;
    }
}
