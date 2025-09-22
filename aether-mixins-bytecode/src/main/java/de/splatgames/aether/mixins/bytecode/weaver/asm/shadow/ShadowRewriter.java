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

/**
 * Bytecode rewriter for {@code @Shadow} usages inside a single method body.
 *
 * <p>This utility traverses a {@link MethodNode}'s instruction list and rewrites
 * field and method instructions whose owner is the mixin type to instead reference
 * the target type (using the <em>stripped</em> member names from {@link ShadowBinding}).
 * For optional shadows that are unresolved on the target, it replaces the instruction
 * with a semantics-preserving <em>neutralization</em>:
 * <ul>
 *   <li>Reads return a <em>default value</em> (e.g., {@code 0}, {@code 0.0}, or {@code null}).</li>
 *   <li>Writes drop the written value (and receiver for instance fields) from the stack.</li>
 *   <li>Invocations drop receiver (if any) and arguments, and push a default return value
 *       for non-void methods (void returns push nothing).</li>
 * </ul>
 *
 * <h2>Supported instructions</h2>
 * <ul>
 *   <li>Fields: {@code GETFIELD}, {@code PUTFIELD}, {@code GETSTATIC}, {@code PUTSTATIC}</li>
 *   <li>Methods: all invoke kinds; staticness is derived from the original opcode</li>
 * </ul>
 *
 * <h2>Stack correctness</h2>
 * <p>Neutralization logic precisely balances the operand stack for each opcode:
 * value categories 1 and 2 are handled using {@code POP}/{@code POP2}, and default
 * constants match the descriptor's return or field type.</p>
 *
 * <h2>Thread-safety</h2>
 * <p>This is a stateless utility operating on the provided {@code MethodNode}; it is
 * not thread-safe and should be used from a single weaving thread.</p>
 *
 * @author Erik Pförtner
 * @see ShadowMap
 * @see ShadowBinding
 * @see MethodNode
 * @since 0.2.0
 */
public final class ShadowRewriter {
    /**
     * Private constructor to prevent instantiation.
     */
    private ShadowRewriter() {
        // utility class, not instantiable
    }

    /**
     * Rewrites {@code @Shadow} field and method uses in a single method body.
     *
     * <p>This pass performs two kinds of transformations:</p>
     * <ol>
     *   <li><b>Resolution rewrite:</b> when a shadow is resolved, it updates the
     *       instruction's {@code owner} to {@code targetOwner} and the {@code name}
     *       to the binding's stripped name.</li>
     *   <li><b>Optional neutralization:</b> when a shadow is {@code optional} and unresolved,
     *       it replaces the instruction with stack-safe defaults:
     *       <ul>
     *         <li><b>Field read</b> → pushes the type-appropriate default value and removes
     *             the original instruction (also discarding the receiver for {@code GETFIELD}).</li>
     *         <li><b>Field write</b> → discards the value (size-aware) and, for {@code PUTFIELD},
     *             the receiver; the original instruction is removed.</li>
     *         <li><b>Method call</b> → discards receiver (if non-static) and arguments, and
     *             pushes a default return value for non-void calls.</li>
     *       </ul>
     *   </li>
     * </ol>
     *
     * @param method      the method whose instruction list is being rewritten; must not be {@code null}
     * @param mixinOwner  internal name (slash-separated) of the mixin class; must not be {@code null}
     * @param targetOwner internal name (slash-separated) of the target class; must not be {@code null}
     * @param map         bindings for shadowed fields and methods; must not be {@code null}
     */
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
                        fin.name = b.getStrippedName();
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
                        min.name = b.getStrippedName();
                    }
                }
            }

            insn = next;
        }
    }

    /**
     * Emits the appropriate {@code POP}/{@code POP2} sequence to drop a field value
     * before a write, based on the field's descriptor.
     *
     * <p>For category-2 values ({@code long}/{@code double}), a single {@code POP2} is inserted;
     * otherwise, a single {@code POP} is inserted.</p>
     *
     * @param method    the method into whose instruction list the pops will be inserted; must not be {@code null}
     * @param at        instruction before which the pops will be inserted; must not be {@code null}
     * @param fieldDesc JVM field descriptor (e.g., {@code J}, {@code D}, {@code Ljava/lang/String;}); must not be {@code null}
     */
    private static void dropValueBefore(@NotNull final MethodNode method,
                                        @NotNull final AbstractInsnNode at,
                                        @NotNull final String fieldDesc) {
        if (fieldDesc.charAt(0) == 'J' || fieldDesc.charAt(0) == 'D') {
            method.instructions.insertBefore(at, new InsnNode(POP2));
        } else {
            method.instructions.insertBefore(at, new InsnNode(POP));
        }
    }

    /**
     * Computes the total number of operand stack slots consumed by a method's parameter list.
     *
     * <p>The count includes category-2 types as two slots and accounts for array and object types
     * according to JVM descriptor grammar.</p>
     *
     * @param methodDesc JVM method descriptor (e.g., {@code (IJLjava/lang/String;)[I}); must not be {@code null}
     * @return the total number of slots consumed by the arguments
     */
    private static int slotsOfArgs(@NotNull final String methodDesc) {
        int i = 1, slots = 0; // skip '('
        while (methodDesc.charAt(i) != ')') {
            char c = methodDesc.charAt(i);
            if (c == 'J' || c == 'D') {
                slots += 2;
                i++;
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
                slots++;
                i++;
            }
        }
        return slots;
    }

    /**
     * Inserts the appropriate sequence of {@code POP}/{@code POP2} instructions before {@code at}
     * to drop {@code slots} operand stack slots.
     *
     * <p>For each two slots, a {@code POP2} is inserted; for a single slot, a {@code POP} is inserted.
     * The instructions are inserted in order so that the stack is balanced immediately before the
     * instruction {@code at}.</p>
     *
     * @param method the method whose instruction list is mutated; must not be {@code null}
     * @param at     the anchor instruction to insert before; must not be {@code null}
     * @param slots  number of slots to drop (non-negative)
     */
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

    /**
     * Produces the default return value instruction for a given method descriptor.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>{@code (I)I} → {@code ICONST_0}</li>
     *   <li>{@code ()J} → {@code LCONST_0}</li>
     *   <li>{@code ([B)Ljava/lang/String;} → {@code ACONST_NULL}</li>
     *   <li>{@code ()V} → {@code null} (no instruction; caller should emit nothing)</li>
     * </ul>
     *
     * @param desc JVM method descriptor whose return type determines the default; must not be {@code null}
     * @return an {@link InsnNode} pushing the default value, or {@code null} for {@code void}
     */
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

    /**
     * Produces an instruction that pushes the default value for a given field (or stack) type.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>{@code I}, {@code Z}, {@code B}, {@code C}, {@code S} → {@code ICONST_0}</li>
     *   <li>{@code J} → {@code LCONST_0}</li>
     *   <li>{@code F} → {@code FCONST_0}</li>
     *   <li>{@code D} → {@code DCONST_0}</li>
     *   <li>Reference and array types → {@code ACONST_NULL}</li>
     * </ul>
     *
     * @param fieldDesc JVM field descriptor whose type determines the default; must not be {@code null}
     * @return an {@link InsnNode} that pushes the type-appropriate default value
     */
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
