package de.splatgames.aether.mixins.bytecode.weaver.asm.shadow;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.DCONST_0;
import static org.objectweb.asm.Opcodes.FCONST_0;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.LCONST_0;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.POP2;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.PUTSTATIC;

public final class ShadowRewriter {
    private ShadowRewriter() {
        // utility class, not instantiable
    }

    public static void rewriteMethodBody(@NotNull final MethodNode method,
                                         @NotNull final String mixinOwner,
                                         @NotNull final String targetOwner,
                                         @NotNull final ShadowMap map) {

        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; ) {
            final AbstractInsnNode next = insn.getNext();

            if (insn instanceof FieldInsnNode fin && fin.owner.equals(mixinOwner)) {
                final ShadowBinding b = map.field(fin.name, fin.desc);
                if (b != null) {
                    if (!b.isResolved() && b.isOptional()) {
                        // optional & missing => neutralize
                        if (fin.getOpcode() == GETFIELD) {
                            // drop objectref, push default of field type
                            method.instructions.insertBefore(fin, new InsnNode(POP));
                            method.instructions.insertBefore(fin, defaultValueForType(fin.desc));
                            method.instructions.remove(fin);
                        } else if (fin.getOpcode() == GETSTATIC) {
                            method.instructions.insertBefore(fin, defaultValueForType(fin.desc));
                            method.instructions.remove(fin);
                        } else if (fin.getOpcode() == PUTFIELD) {
                            // drop value (size-aware) + objectref
                            dropValueBefore(method, fin, fin.desc);
                            method.instructions.insertBefore(fin, new InsnNode(POP)); // drop objectref
                            method.instructions.remove(fin);
                        } else if (fin.getOpcode() == PUTSTATIC) {
                            dropValueBefore(method, fin, fin.desc);
                            method.instructions.remove(fin);
                        }
                        insn = next;
                        continue;
                    }

                    if (b.isResolved()) {
                        // normal rewrite owner+name to target
                        fin.owner = targetOwner;
                        fin.name  = b.getStrippedName();
                    }
                }
            }

            if (insn instanceof MethodInsnNode min && min.owner.equals(mixinOwner)) {
                final ShadowBinding b = map.method(min.name, min.desc);
                if (b != null) {
                    if (!b.isResolved() && b.isOptional()) {
                        // optional & missing => neutralize invocation
                        // drop receiver for non-static and all args, then push default return (if any)
                        final int argSlots = slotsOfArgs(min.desc);
                        if (min.getOpcode() != INVOKESTATIC) {
                            method.instructions.insertBefore(min, new InsnNode(POP)); // drop 'this'
                        }
                        dropSlotsBefore(method, min, argSlots);
                        final AbstractInsnNode retDefault = defaultReturnForDescriptor(min.desc);
                        if (retDefault != null) {
                            method.instructions.insertBefore(min, retDefault);
                        }
                        method.instructions.remove(min);
                        insn = next;
                        continue;
                    }

                    if (b.isResolved()) {
                        min.owner = targetOwner;
                        min.name  = b.getStrippedName();
                    }
                }
            }

            insn = next;
        }
    }

    private static void dropValueBefore(@NotNull final MethodNode method,
                                        @NotNull final AbstractInsnNode at,
                                        @NotNull final String fieldDesc) {
        if (fieldDesc.charAt(0) == 'J' || fieldDesc.charAt(0) == 'D') {
            method.instructions.insertBefore(at, new InsnNode(POP2));
        } else {
            method.instructions.insertBefore(at, new InsnNode(POP));
        }
    }

    private static int slotsOfArgs(@NotNull final String methodDesc) {
        int i = 1, slots = 0; // skip '('
        while (methodDesc.charAt(i) != ')') {
            char c = methodDesc.charAt(i);
            if (c == 'J' || c == 'D') {
                slots += 2; i++;
            } else if (c == 'L') {
                slots++;
                while (methodDesc.charAt(i++) != ';') { /* skip */ }
            } else if (c == '[') {
                slots++;
                while ((c = methodDesc.charAt(++i)) == '[') { /* skip array dims */ }
                if (c == 'L') {
                    while (methodDesc.charAt(i++) != ';') { /* skip type */ }
                }
            } else {
                slots++; i++;
            }
        }
        return slots;
    }

    private static void dropSlotsBefore(@NotNull final MethodNode method,
                                        @NotNull final AbstractInsnNode at,
                                        int slots) {
        while (slots > 0) {
            if (slots >= 2) {
                method.instructions.insertBefore(at, new InsnNode(POP2));
                slots -= 2;
            } else {
                method.instructions.insertBefore(at, new InsnNode(POP));
                slots -= 1;
            }
        }
    }

    @Nullable
    private static AbstractInsnNode defaultReturnForDescriptor(@NotNull final String desc) {
        final int i = desc.indexOf(')') + 1;
        final char r = desc.charAt(i);
        return switch (r) {
            case 'V' -> null; // nothing
            case 'Z', 'B', 'C', 'S', 'I' -> new InsnNode(ICONST_0);
            case 'J' -> new InsnNode(LCONST_0);
            case 'F' -> new InsnNode(FCONST_0);
            case 'D' -> new InsnNode(DCONST_0);
            default -> new InsnNode(ACONST_NULL); // L...; or [...
        };
    }

    @NotNull
    private static AbstractInsnNode defaultValueForType(@NotNull final String fieldDesc) {
        return switch (fieldDesc.charAt(0)) {
            case 'Z', 'B', 'C', 'S', 'I' -> new InsnNode(ICONST_0);
            case 'J' -> new InsnNode(LCONST_0);
            case 'F' -> new InsnNode(FCONST_0);
            case 'D' -> new InsnNode(DCONST_0);
            default -> new InsnNode(ACONST_NULL); // L...; or [...
        };
    }
}
