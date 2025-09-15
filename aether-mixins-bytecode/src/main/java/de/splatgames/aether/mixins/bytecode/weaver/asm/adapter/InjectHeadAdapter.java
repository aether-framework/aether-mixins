package de.splatgames.aether.mixins.bytecode.weaver.asm.adapter;

import de.splatgames.aether.mixins.bytecode.weaver.asm.util.HookShape;
import de.splatgames.aether.mixins.bytecode.weaver.hook.ResolvedHook;
import de.splatgames.aether.mixins.core.config.problems.ConfigProblems;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.LocalVariablesSorter;

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
public final class InjectHeadAdapter extends LocalVariablesSorter {

    /**
     * Internal JVM class name of {@link de.splatgames.aether.mixins.core.api.CallbackInfo CallbackInfo}.
     */
    private static final String CI_INTERNAL = "de/splatgames/aether/mixins/core/api/CallbackInfo";

    /**
     * Internal JVM class name of {@link de.splatgames.aether.mixins.core.api.CallbackInfoReturnable CallbackInfoReturnable}.
     */
    private static final String CIR_INTERNAL = "de/splatgames/aether/mixins/core/api/CallbackInfoReturnable";

    /**
     * The resolved hook (owner/name/desc) to invoke at method entry.
     */
    @NotNull
    private final ResolvedHook hook;

    /**
     * Name of the target method (for diagnostics).
     */
    @NotNull
    private final String methodName;

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
     * Tracks whether we have already injected after the constructor call in a &lt;init&gt; method.
     */
    private boolean injectedAfterCtor = false;

    /**
     * Constructs a new adapter that injects a hook call at method entry.
     *
     * @param methodName  name of the target method (for diagnostics); must not be {@code null}
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
                             @NotNull final String methodName,
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
        super(api, targetAccess, targetDesc, mv);
        this.methodName = methodName;
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
     * Injects the hook call at method entry.
     *
     * <p>If the hook descriptor is incompatible with the target method, a non-optional injection
     * fails fast with an {@link IllegalStateException}.</p>
     */
    @Override
    public void visitCode() {
        super.visitCode();
        if ("<init>".equals(this.methodName)) {
            // Constructor: do nothing here; we’ll inject right after the super/this-ctor call.
            return;
        }
        final boolean instance = HookShape.isInstance(this.targetAccess);
        @Nullable final HookShape.Kind kind = HookShape.match(
                instance, this.ownerInternal, this.targetDesc, this.hook.desc(), CI_INTERNAL, CIR_INTERNAL
        );

        if (kind == null) {
            if (!this.optional) {
                throw new IllegalStateException("HEAD inject: incompatible hook signature for id=" + this.id +
                        " hook=" + this.hook.owner() + "." + this.hook.name() + this.hook.desc());
            }
            return; // optional skip
        }

        final Type targetRet = Type.getReturnType(this.targetDesc);
        final boolean targetIsVoid = Type.VOID_TYPE.equals(targetRet);
        final boolean usesCI = kind.usesCallbackInfo();
        final boolean usesCIR = kind.usesCallbackInfoReturnable();

        // Enforce CI for void, CIR for non-void
        if (usesCI && !targetIsVoid) {
            if (!this.optional) {
                throw new IllegalStateException("HEAD inject: CallbackInfo requires void target (id=" + this.id + ").");
            }
            return;
        }
        if (usesCIR && targetIsVoid) {
            if (!this.optional) {
                throw new IllegalStateException("HEAD inject: CallbackInfoReturnable requires non-void target (id=" + this.id + ").");
            }
            return;
        }

        // Marshal operands
        if (HookShape.requiresThis(kind)) {
            HookShape.emitThisIfNeeded(this.mv, kind);
        }
        int local = instance ? 1 : 0;
        if (HookShape.passesArgs(kind)) {
            local = HookShape.emitArgs(this.mv, this.targetDesc, local);
        }

        // Create and load CI/CIR if needed
        int cbLocal = -1;
        if (usesCI) {
            cbLocal = newLocal(Type.getObjectType(CI_INTERNAL));
            HookShape.newCallbackInfoIfNeeded(this.mv, kind, CI_INTERNAL, cbLocal);
            HookShape.emitLoadCallbackInfoIfNeeded(this.mv, kind, cbLocal);
        } else if (usesCIR) {
            cbLocal = newLocal(Type.getObjectType(CIR_INTERNAL));
            HookShape.newCallbackInfoReturnableIfNeeded(this.mv, kind, CIR_INTERNAL, cbLocal);
            HookShape.emitLoadCallbackInfoReturnableIfNeeded(this.mv, kind, cbLocal);
        }

        // Call hook
        super.visitMethodInsn(INVOKESTATIC, this.hook.owner(), this.hook.name(), this.hook.desc(), false);
        this.markChanged.run();
        this.applied = true;

        // Early return branches
        if (usesCI) {
            // if (ci.isCancelled()) return;
            super.visitVarInsn(Opcodes.ALOAD, cbLocal);
            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CI_INTERNAL, "isCancelled", "()Z", false);
            final Label Lskip = new Label();
            super.visitJumpInsn(Opcodes.IFEQ, Lskip);
            super.visitInsn(Opcodes.RETURN);
            super.visitLabel(Lskip);
        } else if (usesCIR) {
            // if (cir.isCancelled()) return cir.getReturn();
            super.visitVarInsn(Opcodes.ALOAD, cbLocal);
            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CIR_INTERNAL, "isCancelled", "()Z", false);
            final Label LskipCir = new Label();
            super.visitJumpInsn(Opcodes.IFEQ, LskipCir);

            super.visitVarInsn(Opcodes.ALOAD, cbLocal);
            HookShape.emitCirGetReturn(this.mv, CIR_INTERNAL, targetRet);
            HookShape.emitReturnFor(this.mv, targetRet);

            super.visitLabel(LskipCir);
        }
    }

    @Override
    public void visitMethodInsn(final int opcode,
                                final String owner,
                                final String name,
                                final String desc,
                                final boolean itf) {
        super.visitMethodInsn(opcode, owner, name, desc, itf);

        if (!"<init>".equals(this.methodName)) {
            return; // only care in constructors
        }
        if (this.injectedAfterCtor) {
            return; // only once
        }
        if (opcode == Opcodes.INVOKESPECIAL && "<init>".equals(name)) {
            final boolean instance = HookShape.isInstance(this.targetAccess);
            @Nullable final HookShape.Kind kind = HookShape.match(
                    instance, this.ownerInternal, this.targetDesc, this.hook.desc(), CI_INTERNAL, CIR_INTERNAL
            );
            if (kind == null) {
                if (!this.optional) {
                    throw new IllegalStateException(
                            "HEAD inject (ctor): incompatible hook signature for id=" + this.id +
                                    " hook=" + this.hook.owner() + "." + this.hook.name() + this.hook.desc()
                    );
                }
                this.injectedAfterCtor = true; // don’t try again
                return;
            }

            final Type targetRet = Type.getReturnType(this.targetDesc);
            final boolean targetIsVoid = Type.VOID_TYPE.equals(targetRet);
            final boolean usesCI = HookShape.usesCallbackInfo(kind);
            if (usesCI && !targetIsVoid) {
                if (!this.optional) {
                    throw new IllegalStateException(
                            "HEAD inject (ctor): CallbackInfo requires void target (id=" + this.id + ")."
                    );
                }
                this.injectedAfterCtor = true;
                return;
            }

            if (HookShape.requiresThis(kind)) {
                HookShape.emitThisIfNeeded(this.mv, kind);
            }
            int local = 1; // constructor is always instance
            if (HookShape.passesArgs(kind)) {
                local = HookShape.emitArgs(this.mv, this.targetDesc, local);
            }

            int ciLocal = -1;
            if (usesCI) {
                ciLocal = newLocal(Type.getObjectType(CI_INTERNAL));
                HookShape.newCallbackInfoIfNeeded(this.mv, kind, CI_INTERNAL, ciLocal);
                HookShape.emitLoadCallbackInfoIfNeeded(this.mv, kind, ciLocal);
            }

            super.visitMethodInsn(INVOKESTATIC, this.hook.owner(), this.hook.name(), this.hook.desc(), false);
            this.markChanged.run();
            this.applied = true;

            if (usesCI) {
                // if (ci.isCancelled()) return;
                super.visitVarInsn(Opcodes.ALOAD, ciLocal);
                super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CI_INTERNAL, "isCancelled", "()Z", false);
                final org.objectweb.asm.Label Lskip = new org.objectweb.asm.Label();
                super.visitJumpInsn(Opcodes.IFEQ, Lskip);
                super.visitInsn(Opcodes.RETURN);
                super.visitLabel(Lskip);
            }

            this.injectedAfterCtor = true;
        }
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
