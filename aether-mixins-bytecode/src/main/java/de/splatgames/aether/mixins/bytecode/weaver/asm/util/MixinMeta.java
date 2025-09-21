package de.splatgames.aether.mixins.bytecode.weaver.asm.util;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class MixinMeta {
    private final boolean hasMixin;
    private final List<String> targets = new ArrayList<>();
    private final List<String> values = new ArrayList<>();
    private int priority = 1000;

    public MixinMeta(final boolean hasMixin) {
        this.hasMixin = hasMixin;
    }

    public boolean hasMixin() {
        return this.hasMixin;
    }

    @NotNull
    public List<String> getTargets() {
        return this.targets;
    }

    @NotNull
    public List<String> getValues() {
        return this.values;
    }

    public int getPriority() {
        return this.priority;
    }

    public void setPriority(final int priority) {
        this.priority = priority;
    }
}