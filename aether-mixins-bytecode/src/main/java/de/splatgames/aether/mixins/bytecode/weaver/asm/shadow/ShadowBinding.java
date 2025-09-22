package de.splatgames.aether.mixins.bytecode.weaver.asm.shadow;

import org.jetbrains.annotations.NotNull;

public final class ShadowBinding {
    public enum Kind {
        FIELD,
        METHOD
    }

    @NotNull
    private final Kind kind;

    private final boolean isStatic;

    @NotNull
    private final String strippedName;

    @NotNull
    private final String desc;

    private final boolean optional;

    private final boolean resolved;

    public ShadowBinding(@NotNull final Kind kind,
                         final boolean isStatic,
                         @NotNull final String strippedName,
                         @NotNull final String desc,
                         final boolean optional,
                         final boolean resolved) {
        this.kind = kind;
        this.isStatic = isStatic;
        this.strippedName = strippedName;
        this.desc = desc;
        this.optional = optional;
        this.resolved = resolved;
    }

    @NotNull
    public Kind getKind() {
        return this.kind;
    }

    public boolean isStatic() {
        return this.isStatic;
    }

    @NotNull
    public String getStrippedName() {
        return this.strippedName;
    }

    @NotNull
    public String getDesc() {
        return this.desc;
    }

    public boolean isOptional() {
        return this.optional;
    }

    public boolean isResolved() {
        return this.resolved;
    }
}
