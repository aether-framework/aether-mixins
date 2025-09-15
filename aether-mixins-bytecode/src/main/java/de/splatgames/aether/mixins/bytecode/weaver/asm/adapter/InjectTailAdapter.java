package de.splatgames.aether.mixins.bytecode.weaver.asm.adapter;

import de.splatgames.aether.mixins.bytecode.weaver.asm.util.HookShape;
import de.splatgames.aether.mixins.bytecode.weaver.hook.ResolvedHook;
import de.splatgames.aether.mixins.core.config.problems.ConfigProblems;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.LocalVariablesSorter;

import static org.objectweb.asm.Opcodes.ALOAD;
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
public final class InjectTailAdapter extends LocalVariablesSorter {

    /**
     * Internal JVM class name of {@link de.splatgames.aether.mixins.core.api.CallbackInfo CallbackInfo}.
     */
    private static final String CI_INTERNAL = "de/splatgames/aether/mixins/core/api/CallbackInfo";

    /**
     * Internal JVM class name of {@link de.splatgames.aether.mixins.core.api.CallbackInfoReturnable CallbackInfoReturnable}.
     */
    private static final String CIR_INTERNAL = "de/splatgames/aether/mixins/core/api/CallbackInfoReturnable";

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
        super(api, targetAccess, targetDesc, mv);
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
     * Emits the hook call immediately before return instructions and then delegates the
     * original return opcode downstream.
     *
     * @param opcode the opcode currently being visited
     */
    @Override
    public void visitInsn(final int opcode) {
        if (isReturn(opcode)) {
            final boolean instance = HookShape.isInstance(this.targetAccess);

            @Nullable final HookShape.Kind kind = HookShape.match(
                    instance, this.ownerInternal, this.targetDesc, this.hook.desc(), CI_INTERNAL, CIR_INTERNAL
            );

            if (kind == null) {
                if (!this.optional) {
                    throw new IllegalStateException(
                            "TAIL inject: incompatible hook signature for id=" + this.id +
                                    " hook=" + this.hook.owner() + "." + this.hook.name() + this.hook.desc()
                    );
                }
                super.visitInsn(opcode);
                return;
            }

            final Type ret = Type.getReturnType(this.targetDesc);
            final boolean isVoid = Type.VOID_TYPE.equals(ret);
            final boolean usesCI = kind.usesCallbackInfo();
            final boolean usesCIR = kind.usesCallbackInfoReturnable();

            if (usesCI && !isVoid) {
                if (!this.optional) {
                    throw new IllegalStateException(
                            "TAIL inject: CallbackInfo requires void target (id=" + this.id + ")."
                    );
                }
                super.visitInsn(opcode);
                return;
            }

            if (usesCIR && isVoid) {
                if (!this.optional) {
                    throw new IllegalStateException(
                            "TAIL inject: CallbackInfoReturnable requires non-void target (id=" + this.id + ")."
                    );
                }
                super.visitInsn(opcode);
                return;
            }

            int retLocal = -1;
            if (!isVoid) {
                retLocal = newLocal(ret);
                HookShape.storeReturnValueBeforeTail(this.mv, this.targetDesc, retLocal);
            }

            if (HookShape.requiresThis(kind)) {
                HookShape.emitThisIfNeeded(this.mv, kind);
            }
            int local = instance ? 1 : 0;
            if (HookShape.passesArgs(kind)) {
                local = HookShape.emitArgs(this.mv, this.targetDesc, local);
            }

            int cbLocal = -1;
            if (usesCI) {
                cbLocal = newLocal(Type.getObjectType(CI_INTERNAL));
                HookShape.newCallbackInfoIfNeeded(this.mv, kind, CI_INTERNAL, cbLocal);
                HookShape.emitLoadCallbackInfoIfNeeded(this.mv, kind, cbLocal);
            } else if (usesCIR) {
                cbLocal = newLocal(Type.getObjectType(CIR_INTERNAL));
                HookShape.newCallbackInfoReturnableIfNeeded(this.mv, kind, CIR_INTERNAL, cbLocal);

                this.mv.visitVarInsn(ALOAD, cbLocal);
                HookShape.loadReturnValueFromLocal(this.mv, this.targetDesc, retLocal);
                HookShape.emitCirSetReturn(this.mv, CIR_INTERNAL, ret);

                HookShape.emitLoadCallbackInfoReturnableIfNeeded(this.mv, kind, cbLocal);
            }

            super.visitMethodInsn(INVOKESTATIC, this.hook.owner(), this.hook.name(), this.hook.desc(), false);
            this.markChanged.run();
            this.applied = true;

            if (!isVoid) {
                if (usesCIR) {
                    this.mv.visitVarInsn(ALOAD, cbLocal);
                    HookShape.emitCirGetReturn(this.mv, CIR_INTERNAL, ret);
                } else {
                    HookShape.loadReturnValueFromLocal(this.mv, this.targetDesc, retLocal);
                }
            }

            super.visitInsn(opcode);
            return;
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
