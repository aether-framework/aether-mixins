package de.splatgames.aether.mixins.bytecode.weaver.asm.adapter;

import de.splatgames.aether.mixins.bytecode.weaver.hook.ResolvedHook;
import de.splatgames.aether.mixins.core.config.problems.ConfigProblems;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import static org.objectweb.asm.Opcodes.INVOKESTATIC;

/**
 * Method visitor that injects a single static hook call at the very beginning
 * of the visited method (i.e., at the method prologue / {@linkplain #visitCode() code entry}).
 *
 * <p>Behavior:</p>
 * <ul>
 *   <li>On {@link #visitCode()}, emits an {@code INVOKESTATIC} to the configured {@link #hook}.</li>
 *   <li>Supports four hook descriptor shapes:
 *       {@code ()V}, {@code (OWNER;)V}, {@code (args)V}, {@code (OWNER;args)V}.</li>
 *   <li>Marks the enclosing weaving operation as changed via {@link #markChanged}.</li>
 *   <li>If no code is ever visited (edge cases), a non-optional injection fails fast in {@link #visitEnd()}.</li>
 * </ul>
 *
 * <p>Contract:</p>
 * <ul>
 *   <li>{@link #hook} must refer to a <b>static</b> method. Valid shapes: {@code ()V}, {@code (OWNER;)V}, {@code (args)V}, {@code (OWNER;args)V}.</li>
 *   <li>Descriptor compatibility is verified against the target method at weave-time.</li>
 * </ul>
 *
 * <p>Thread-safety: instances are not thread-safe and must be used by a single ASM visitation thread.</p>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class InjectHeadAdapter extends MethodVisitor {

    private enum HookShape {NONE, THIS, ARGS, THIS_ARGS}

    /**
     * The resolved hook (owner/name/desc) to invoke at method entry.
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
     * Target method context (owner/access/descriptor) used to validate and marshal operands.
     */
    private final int targetAccess;

    /**
     * Target method context (owner/access/descriptor) used to validate and marshal operands.
     */
    @NotNull
    private final String targetDesc;

    /**
     * Tracks whether the injection has been applied at least once.
     */
    private boolean applied = false;

    /**
     * Constructs a new adapter that injects a static {@code ()V} hook at method entry.
     *
     * @param api         ASM API level to use
     * @param mv          downstream method visitor to delegate to; must not be {@code null}
     * @param ownerInternal internal JVM class name of the target method (e.g., {@code com/example/Foo}); must not be {@code null}
     * @param targetAccess access flags of the target method (e.g., {@code ACC_PUBLIC | ACC_STATIC})
     * @param targetDesc  method descriptor of the target method (e.g., {@code (I)V}); must not be {@code null}
     * @param hook        resolved hook to invoke; must not be {@code null} and must be {@code ()V}
     * @param optional    whether to tolerate a missing injection (no code visited) without failing
     * @param id          developer-defined identifier used in diagnostics; must not be {@code null}
     * @param markChanged callback invoked when the injection is applied; must not be {@code null}
     * @param problems    diagnostics sink for potential future reporting; must not be {@code null}
     * @param cls         internal JVM class name for diagnostics (e.g., {@code com/example/Foo}); must not be {@code null}
     * @param sig         method signature {@code name+desc} for diagnostics (e.g., {@code bar(I)V}); must not be {@code null}
     */
    public InjectHeadAdapter(final int api,
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
        this.ctx = "inject/HEAD " + cls + "." + sig + " id=" + id;
    }

    /**
     * Determines whether the target method is an instance method (i.e., not static).
     *
     * @param access access flags of the target method
     * @return {@code true} if the target method is an instance method, {@code false} if it is static
     */
    private static boolean isInstance(final int access) {
        return (access & Opcodes.ACC_STATIC) == 0;
    }

    /**
     * Parses the argument types from a method descriptor.
     *
     * @param desc method descriptor (e.g., {@code (I)V}); must not be {@code null}
     * @return array of argument types; never {@code null}, may be empty
     */
    @NotNull
    private static Type[] argTypes(@NotNull final String desc) {
        return Type.getArgumentTypes(desc);
    }

    /**
     * Parses the owner type from an internal class name.
     *
     * @param internal internal JVM class name (e.g., {@code com/example/Foo}); must not be {@code null}
     * @return corresponding object type; never {@code null}
     */
    @NotNull
    private static Type ownerType(final String internal) {
        return Type.getObjectType(internal);
    }

    /**
     * Determines the size of a local variable slot for the given type.
     *
     * @param t type to check; must not be {@code null}
     * @return size of the local variable slot (1 or 2)
     */
    private static int localSize(@NotNull final Type t) {
        return (t == Type.LONG_TYPE || t == Type.DOUBLE_TYPE) ? 2 : 1;
    }

    /**
     * Emits the appropriate {@code xLOAD} instruction to load a local variable of the given type.
     *
     * @param mv  method visitor to emit to; must not be {@code null}
     * @param t   type of the local variable; must not be {@code null}
     * @param idx index of the local variable to load
     * @throws IllegalArgumentException if the type is unsupported
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
     * Matches the hook descriptor against the target method descriptor and owner type.
     *
     * @param instance      whether the target method is an instance method (i.e., not static)
     * @param targetDesc    method descriptor of the target method (e.g., {@code (I)V}); must not be {@code null}
     * @param ownerInternal internal JVM class name of the target method (e.g., {@code com/example/Foo}); must not be {@code null}
     * @param hookDesc      method descriptor of the hook method (e.g., {@code (Lcom/example/Foo;I)V}); must not be {@code null}
     * @return matched hook shape, or {@code null} if the hook descriptor is incompatible with the target method
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
     * Injects the hook call at method entry.
     *
     * <p>If the hook descriptor is incompatible with the target method, a non-optional injection
     * fails fast with an {@link IllegalStateException}.</p>
     */
    @Override
    public void visitCode() {
        super.visitCode();
        final boolean instance = isInstance(this.targetAccess);
        final HookShape shape = matchShape(instance, this.targetDesc, this.ownerInternal, this.hook.desc());
        if (shape == null) {
            if (!this.optional) {
                throw new IllegalStateException("HEAD inject: incompatible hook signature for id=" + this.id +
                        " hook=" + hook.owner() + "." + hook.name() + hook.desc());
            }
            return;
        }
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
    }

    /**
     * Verifies that the injection was applied at least once, unless it is marked as optional.
     *
     * @throws IllegalStateException if the injection did not occur and {@link #optional} is {@code false}
     */
    @Override
    public void visitEnd() {
        if (!this.applied && !this.optional) {
            throw new IllegalStateException("HEAD inject not applied: id=" + this.id);
        }
        super.visitEnd();
    }
}
