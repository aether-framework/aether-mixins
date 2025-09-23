package de.splatgames.aether.mixins.bytecode.weaver.asm.shadow;

import org.jetbrains.annotations.NotNull;

public class FieldSig {
    private final String name;
    private final String desc;

    public FieldSig(@NotNull final String name, @NotNull final String desc) {
        this.name = name;
        this.desc = desc;
    }

    @NotNull
    public String getName() {
        return this.name;
    }

    @NotNull
    public String getDesc() {
        return this.desc;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        final FieldSig fieldSig = (FieldSig) obj;

        if (!this.name.equals(fieldSig.name)) {
            return false;
        }
        return this.desc.equals(fieldSig.desc);
    }

    @Override
    public int hashCode() {
        int result = this.name.hashCode();
        result = 31 * result + this.desc.hashCode();
        return result;
    }
}
