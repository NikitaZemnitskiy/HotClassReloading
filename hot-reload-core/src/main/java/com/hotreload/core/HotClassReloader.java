package com.hotreload.core;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;

public class HotClassReloader {

    private final Instrumentation instrumentation;

    public HotClassReloader(Instrumentation instrumentation) {
        this.instrumentation = instrumentation;
    }

    public void reload(Class<?> clazz, byte[] newBytecode)
            throws ClassNotFoundException, UnmodifiableClassException {
        instrumentation.redefineClasses(new ClassDefinition(clazz, newBytecode));
    }
}
