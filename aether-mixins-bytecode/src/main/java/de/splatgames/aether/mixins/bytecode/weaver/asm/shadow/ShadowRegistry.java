package de.splatgames.aether.mixins.bytecode.weaver.asm.shadow;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

public final class ShadowRegistry {

    @NotNull
    private final Map<String, ShadowMeta> methods;
    @NotNull
    private final Map<String, ShadowMeta> fields;

    public ShadowRegistry(@NotNull final Map<String, ShadowMeta> methods,
                          @NotNull final Map<String, ShadowMeta> fields) {
        this.methods = methods;
        this.fields = fields;
    }

    @NotNull
    public Map<String, ShadowMeta> methods() {
        return this.methods;
    }

    @NotNull
    public Map<String, ShadowMeta> fields() {
        return this.fields;
    }
}
