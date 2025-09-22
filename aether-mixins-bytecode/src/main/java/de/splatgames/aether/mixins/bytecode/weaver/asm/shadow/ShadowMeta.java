package de.splatgames.aether.mixins.bytecode.weaver.asm.shadow;

import org.jetbrains.annotations.NotNull;

public record ShadowMeta(boolean staticMember, @NotNull String strippedName, boolean optional, boolean resolved,
                         boolean targetFinal, boolean mutable) {
}