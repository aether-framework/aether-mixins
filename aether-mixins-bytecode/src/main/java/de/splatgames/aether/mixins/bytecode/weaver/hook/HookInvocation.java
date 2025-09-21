package de.splatgames.aether.mixins.bytecode.weaver.hook;

import org.jetbrains.annotations.NotNull;

public enum HookInvocation {
    STATIC,
    INSTANCE;

    @NotNull
    public static HookInvocation isStatic(final boolean isStatic) {
        return isStatic ? STATIC : INSTANCE;
    }
}
