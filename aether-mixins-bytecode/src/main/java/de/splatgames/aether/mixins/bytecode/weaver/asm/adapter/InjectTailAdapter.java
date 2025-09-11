package de.splatgames.aether.mixins.bytecode.weaver.asm.adapter;

import de.splatgames.aether.mixins.bytecode.weaver.hook.ResolvedHook;
import de.splatgames.aether.mixins.core.config.problems.ConfigProblems;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.DRETURN;
import static org.objectweb.asm.Opcodes.FRETURN;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.LRETURN;
import static org.objectweb.asm.Opcodes.RETURN;

/**
 * Method visitor that injects a single static hook call immediately before
 * every return instruction of the visited method (TAIL injection).
 *
 * <p>Behavior:</p>
 * <ul>
 *   <li>On each return opcode, emits an {@code INVOKESTATIC} to the configured {@link #hook}.</li>
 *   <li>Supports four hook descriptor shapes:
 *       {@code ()V}, {@code (OWNER;)V}, {@code (args)V}, {@code (OWNER;args)V}.</li>
 *   <li>Marks the enclosing weaving operation as changed via {@link #markChanged}.</li>
 *   <li>If no return is ever visited, a non-optional injection fails fast in {@link #visitEnd()}.</li>
 * </ul>
 *
 * <p>Contract:</p>
 * <ul>
 *   <li>{@link #hook} must refer to a <b>static</b> method with descriptor {@code ()V}.</li>
 *   <li>This adapter does not perform descriptor verification; callers should validate prior to use.</li>
 *   <li>The injected call does not affect operand stack depth at return sites, since it has no parameters
 *       and returns {@code void}.</li>
 * </ul>
 *
 * <p>Thread-safety: instances are not thread-safe and must be used by a single ASM visitation thread.</p>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class InjectTailAdapter extends MethodVisitor {

    private enum HookShape {NONE, THIS, ARGS, THIS_ARGS}

    /**
     * The resolved hook (owner/name/desc) to invoke before each return.
     */
    @NotNull
    private final ResolvedHook hook;

    /**
     * Whether the injection may be missing without raising an exception.
     */
    private final boolean optional;

    /**
     * Developer-defined identifier used in diagnostics and error messages.
     */
    @NotNull
    private final String id;

    /**
     * Callback invoked once a bytecode modification is applied.
     */
    @NotNull
    private final Runnable markChanged;

    /**
     * Diagnostics sink (currently unused by this adapter, reserved for future reporting).
     */
    @NotNull
    @SuppressWarnings("unused, FieldCanBeLocal")
    private final ConfigProblems problems;

    /**
     * Human-readable context (class and method signature) for diagnostics.
     */
    @NotNull
    @SuppressWarnings("unused, FieldCanBeLocal")
    private final String ctx;

    /**
     * Target method context (owner/access/descriptor) used to validate and marshal operands.
     */
    @NotNull
    private final String ownerInternal;

    /**
     * Access flags of the target method (e.g., {@code ACC_PUBLIC | ACC_STATIC}).
     */
    private final int targetAccess;

    /**
     * Method descriptor of the target method (e.g., {@code (I)V}).
     */
    @NotNull
    private final String targetDesc;

    /**
     * Tracks whether the injection has been applied at least once.
     */
    private boolean applied = false;

    /**
     * Constructs a new adapter that injects a hook call at method entry.
     *
     * @param api         ASM API level to use
     * @param mv          downstream method visitor to delegate to; must not be {@code null}
     * @param ownerInternal internal JVM class name of the target method (e.g., {@code com/example/Foo}); must not be {@code null}
     * @param targetAccess access flags of the target method (e.g., {@code ACC_PUBLIC | ACC_STATIC})
     * @param targetDesc  method descriptor of the target method (e.g., {@code (I)V}); must not be {@code null}
     * @param hook        resolved hook to invoke; must not be {@code null} and must be {@code ()V}
     * @param optional    whether to tolerate the absence of return opcodes without failing
     * @param id          developer-defined identifier used in diagnostics; must not be {@code null}
     * @param markChanged callback invoked when the injection is applied; must not be {@code null}
     * @param problems    diagnostics sink for potential future reporting; must not be {@code null}
     * @param cls         internal JVM class name for diagnostics (e.g., {@code com/example/Foo}); must not be {@code null}
     * @param sig         method signature {@code name+desc} for diagnostics (e.g., {@code bar(I)V}); must not be {@code null}
     */
    public InjectTailAdapter(final int api,
                             @NotNull final MethodVisitor mv,
                             @NotNull final String ownerInternal,
                             final int targetAccess,
                             @NotNull final String targetDesc,
                             @NotNull final ResolvedHook hook,
                             final boolean optional,
                             @NotNull final String id,
                             @NotNull final Runnable markChanged,
                             @NotNull final ConfigProblems problems,
                             @NotNull final String cls,
                             @NotNull final String sig) {
        super(api, mv);
        this.ownerInternal = ownerInternal;
        this.targetAccess = targetAccess;
        this.targetDesc = targetDesc;
        this.hook = hook;
        this.optional = optional;
        this.id = id;
        this.markChanged = markChanged;
        this.problems = problems;
        this.ctx = "inject/TAIL " + cls + "." + sig + " id=" + id;
    }

    /**
     * Determines whether the given opcode is any return instruction.
     *
     * @param opcode the opcode to check
     * @return {@code true} if {@code opcode} represents a return instruction ({@code RETURN}, {@code ARETURN},
     * {@code IRETURN}, {@code LRETURN}, {@code FRETURN}, or {@code DRETURN}); otherwise {@code false}
     */
    private static boolean isReturn(final int opcode) {
        return opcode == RETURN || opcode == ARETURN || opcode == IRETURN
                || opcode == LRETURN || opcode == FRETURN || opcode == DRETURN;
    }

    /**
     * Determines whether the method with the given access flags is an instance method.
     *
     * @param access the access flags to check
     * @return {@code true} if the method is an instance method (i.e., not {@code static}); otherwise {@code false}
     */
    private static boolean isInstance(final int access) {
        return (access & Opcodes.ACC_STATIC) == 0;
    }

    /**
     * Extracts the argument types from the given method descriptor.
     *
     * @param desc the method descriptor to analyze; must not be {@code null}
     * @return an array of {@link Type} representing the argument types in order
     */
    @NotNull
    private static Type[] argTypes(@NotNull final String desc) {
        return Type.getArgumentTypes(desc);
    }

    /**
     * Constructs an object type from the given internal class name.
     *
     * @param internal the internal JVM class name (e.g., {@code com/example/Foo}); must not be {@code null}
     * @return a {@link Type} representing the object type
     */
    @NotNull
    private static Type ownerType(@NotNull final String internal) {
        return Type.getObjectType(internal);
    }

    /**
     * Determines the size of a local variable slot for the given type.
     *
     * @param t the type to analyze; must not be {@code null}
     * @return {@code 2} if {@code t} is {@code long} or {@code double}; otherwise {@code 1}
     */
    private static int localSize(@NotNull final Type t) {
        return (t == Type.LONG_TYPE || t == Type.DOUBLE_TYPE) ? 2 : 1;
    }

    /**
     * Emits a load instruction for the given type from the specified local variable index.
     *
     * @param mv  the method visitor to emit to; must not be {@code null}
     * @param t   the type to load; must not be {@code null}
     * @param idx the local variable index to load from
     */
    private static void loadLocal(@NotNull final MethodVisitor mv, @NotNull final Type t, final int idx) {
        switch (t.getSort()) {
            case Type.BOOLEAN, Type.BYTE, Type.SHORT, Type.CHAR, Type.INT -> mv.visitVarInsn(Opcodes.ILOAD, idx);
            case Type.FLOAT -> mv.visitVarInsn(Opcodes.FLOAD, idx);
            case Type.LONG -> mv.visitVarInsn(Opcodes.LLOAD, idx);
            case Type.DOUBLE -> mv.visitVarInsn(Opcodes.DLOAD, idx);
            case Type.ARRAY, Type.OBJECT -> mv.visitVarInsn(Opcodes.ALOAD, idx);
            default -> throw new IllegalArgumentException("Unsupported type: " + t);
        }
    }

    /**
     * Matches the hook descriptor against the target method descriptor and owner to determine
     * the expected shape of the hook parameters.
     *
     * @param instance      whether the target method is an instance method
     * @param targetDesc    the descriptor of the target method; must not be {@code null}
     * @param ownerInternal the internal JVM class name of the target method's owner; must not be {@code null}
     * @param hookDesc      the descriptor of the hook method; must not be {@code null}
     * @return the matched {@link HookShape} if compatible; otherwise {@code null
     */
    @Nullable
    private static HookShape matchShape(final boolean instance, @NotNull final String targetDesc,
                                        @NotNull final String ownerInternal, @NotNull final String hookDesc) {
        final Type[] tArgs = argTypes(targetDesc);
        final Type[] hArgs = argTypes(hookDesc);
        final Type hRet = Type.getReturnType(hookDesc);
        if (!Type.VOID_TYPE.equals(hRet)) {
            return null;
        }
        if (hArgs.length == 0) {
            return HookShape.NONE;
        }
        final Type ownerT = ownerType(ownerInternal);
        if (hArgs.length == 1 && hArgs[0].equals(ownerT)) {
            return instance ? HookShape.THIS : null;
        }
        if (hArgs.length == tArgs.length) {
            for (int i = 0; i < hArgs.length; i++)
                if (!hArgs[i].equals(tArgs[i])) {
                    return null;
                }
            return HookShape.ARGS;
        }
        if (hArgs.length == tArgs.length + 1 && hArgs[0].equals(ownerT)) {
            if (!instance) {
                return null;
            }
            for (int i = 0; i < tArgs.length; i++)
                if (!hArgs[i + 1].equals(tArgs[i])) {
                    return null;
                }
            return HookShape.THIS_ARGS;
        }
        return null;
    }

    /**
     * Emits the hook call immediately before return instructions and then delegates the
     * original return opcode downstream.
     *
     * @param opcode the opcode currently being visited
     */
    @Override
    public void visitInsn(final int opcode) {
        if (isReturn(opcode)) {
            final boolean instance = isInstance(this.targetAccess);
            final HookShape shape = matchShape(instance, this.targetDesc, this.ownerInternal, this.hook.desc());
            if (shape != null) {
                int local = instance ? 1 : 0;
                if (shape == HookShape.THIS || shape == HookShape.THIS_ARGS) {
                    super.visitVarInsn(Opcodes.ALOAD, 0);
                }
                if (shape == HookShape.ARGS || shape == HookShape.THIS_ARGS) {
                    for (final Type t : argTypes(this.targetDesc)) {
                        loadLocal(this.mv, t, local);
                        local += localSize(t);
                    }
                }
                super.visitMethodInsn(INVOKESTATIC, this.hook.owner(), this.hook.name(), this.hook.desc(), false);
                this.markChanged.run();
                this.applied = true;
            } else if (!this.optional) {
                throw new IllegalStateException("TAIL inject: incompatible hook signature for id=" + this.id +
                        " hook=" + this.hook.owner() + "." + this.hook.name() + this.hook.desc());
            }
        }
        super.visitInsn(opcode);
    }

    /**
     * Verifies that at least one return site was instrumented, unless the injection is marked optional.
     *
     * @throws IllegalStateException if no return was instrumented and {@link #optional} is {@code false}
     */
    @Override
    public void visitEnd() {
        if (!this.applied && !this.optional) {
            throw new IllegalStateException("TAIL inject not applied (no return visited): id=" + this.id);
        }
        super.visitEnd();
    }
}
