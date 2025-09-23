package de.splatgames.aether.mixins.bytecode.weaver.asm.adapter;

import de.splatgames.aether.mixins.bytecode.weaver.asm.shadow.FieldSig;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Set;

public final class UnfinalizeFieldsAdapter extends ClassVisitor {
    private final Set<FieldSig> unfinalize;

    public UnfinalizeFieldsAdapter(final int api, @NotNull ClassVisitor cv, @NotNull final Set<FieldSig> unfinalize) {
        super(api, cv);
        this.unfinalize = unfinalize;
    }

    @Override
    public FieldVisitor visitField(int access, final String name, final String descriptor, final String signature, Object value) {
        if (unfinalize.contains(new FieldSig(name, descriptor))) {
            access &= ~Opcodes.ACC_FINAL; // strip FINAL right here
            value = null; // also strip constant value, this will stop the compiler from inlining again
        }
        return super.visitField(access, name, descriptor, signature, value);
    }
}
