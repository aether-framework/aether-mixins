package de.splatgames.aether.mixins.bytecode.weaver.asm.shadow;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public record ShadowAttributes(String prefix, boolean remap, boolean optional) {
    public ShadowAttributes(@NotNull final String prefix, final boolean remap, final boolean optional) {
        this.prefix = Objects.requireNonNull(prefix, "prefix");
        this.remap = remap;
        this.optional = optional;
    }
}