package de.splatgames.aether.mixins.bytecode.weaver.asm.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Utilities for classifying and marshalling hook method signatures relative to a target method.
 *
 * <h2>Purpose</h2>
 * <p>
 * Adapters need to:
 * </p>
 * <ol>
 *   <li>Validate that a hook method descriptor is compatible with a given target method
 *       (instance/static semantics and positional argument compatibility).</li>
 *   <li>Emit the correct operand loads (optionally {@code this}, target arguments, and a trailing
 *       {@code CallbackInfo}) before invoking the hook via {@code INVOKESTATIC}.</li>
 * </ol>
 *
 * <h2>Supported hook shapes</h2>
 * <p>
 * Let the target method descriptor be {@code (A B ... )R} and the target owner type be {@code OWNER}.
 * A hook method (invoked with {@code INVOKESTATIC}) may use one of:
 * </p>
 * <ul>
 *   <li>{@link Kind#NONE} – {@code ()V}</li>
 *   <li>{@link Kind#THIS} – {@code (OWNER;)V} (only for instance targets)</li>
 *   <li>{@link Kind#ARGS} – {@code (A B ...)V}</li>
 *   <li>{@link Kind#THIS_ARGS} – {@code (OWNER; A B ...)V} (only for instance targets)</li>
 *   <li>{@link Kind#NONE_CI} – {@code (CallbackInfo)V}</li>
 *   <li>{@link Kind#THIS_CI} – {@code (OWNER; CallbackInfo)V} (only for instance targets)</li>
 *   <li>{@link Kind#ARGS_CI} – {@code (A B ...; CallbackInfo)V}</li>
 *   <li>{@link Kind#THIS_ARGS_CI} – {@code (OWNER; A B ...; CallbackInfo)V} (only for instance targets)</li>
 *   <li>{@link Kind#NONE_CIR} – {@code (CallbackInfoReturnable)V}</li>
 *   <li>{@link Kind#THIS_CIR} – {@code (OWNER; CallbackInfoReturnable)V}</li>
 *   <li>{@link Kind#ARGS_CIR} – {@code (A B ...; CallbackInfoReturnable)V}</li>
 *   <li>{@link Kind#THIS_ARGS_CIR} – {@code (OWNER; A B ...; CallbackInfoReturnable)V}</li>
 * </ul>
 *
 * <p><b>Constraints:</b></p>
 * <ul>
 *   <li>Hook return type must be {@code void}.</li>
 *   <li>Hooks are invoked via {@code INVOKESTATIC}.</li>
 *   <li>If present, {@code CallbackInfo} must be the last parameter.</li>
 * </ul>
 *
 * <h2>Typical adapter flow</h2>
 * <pre>{@code
 * final boolean instance = HookShape.isInstance(targetAccess);
 * final HookShape.Kind kind =
 *      HookShape.match(instance, ownerInternal, targetDesc, hookDesc, CI_INTERNAL, CIR_INTERNAL);
 * if (kind == null) { * incompatible signature * }
 *
 * int local = instance ? 1 : 0;
 * HookShape.emitThisIfNeeded(mv, kind);
 *
 * if (HookShape.passesArgs(kind)) {
 *     local = HookShape.emitArgs(mv, targetDesc, local);
 * }
 *
 * int cbLocal = -1;
 * if (kind.usesCallbackInfo()) {
 *     cbLocal = newLocal(Type.getObjectType(CI_INTERNAL));
 *     HookShape.newCallbackInfoIfNeeded(mv, kind, CI_INTERNAL, cbLocal);
 *     HookShape.emitLoadCallbackInfoIfNeeded(mv, kind, cbLocal);
 * } else if (kind.usesCallbackInfoReturnable()) {
 *     cbLocal = newLocal(Type.getObjectType(CIR_INTERNAL));
 *     HookShape.newCallbackInfoReturnableIfNeeded(mv, kind, CIR_INTERNAL, cbLocal);
 *    HookShape.emitLoadCallbackInfoReturnableIfNeeded(mv, kind, cbLocal);
 * }
 *
 * }</pre>
 *
 * <p><b>Thread-safety:</b> This utility class is stateless and thread-safe.</p>
 *
 * @author Erik Pförtner
 * @since 0.2.0
 */
public final class HookShape {

    /**
     * Utility class; not instantiable.
     */
    private HookShape() {
    }

    /**
     * Supported hook shapes and their behavioral flags.
     */
    public enum Kind {
        /**
         * Hook descriptor: {@code ()V}.
         */
        NONE(false, false, false),

        /**
         * Hook descriptor: {@code (OWNER;)V}. Requires instance targets.
         */
        THIS(true, false, false),

        /**
         * Hook descriptor: {@code (args...)V}.
         */
        ARGS(false, true, false),

        /**
         * Hook descriptor: {@code (OWNER; args...)V}. Requires instance targets.
         */
        THIS_ARGS(true, true, false),

        /**
         * Hook descriptor: {@code (CallbackInfo)V}.
         */
        NONE_CI(false, false, true),

        /**
         * Hook descriptor: {@code (OWNER; CallbackInfo)V}. Requires instance targets.
         */
        THIS_CI(true, false, true),

        /**
         * Hook descriptor: {@code (args..., CallbackInfo)V}.
         */
        ARGS_CI(false, true, true),

        /**
         * Hook descriptor: {@code (OWNER; args..., CallbackInfo)V}. Requires instance targets.
         */
        THIS_ARGS_CI(true, true, true),

        /**
         * Hook descriptor: {@code (CallbackInfoReturnable)V}.
         */
        NONE_CIR(false, false, false, true),

        /**
         * Hook descriptor: {@code (OWNER; CallbackInfoReturnable)V}. Requires instance targets.
         */
        THIS_CIR(true, false, false, true),

        /**
         * Hook descriptor: {@code (args..., CallbackInfoReturnable)V}.
         */
        ARGS_CIR(false, true, false, true),

        /**
         * Hook descriptor: {@code (OWNER; args..., CallbackInfoReturnable)V}. Requires instance targets.
         */
        THIS_ARGS_CIR(true, true, false, true);

        /**
         * Whether this shape requires loading {@code this} (local slot 0) before invoking the hook.
         */
        private final boolean requiresThis;
        /**
         * Whether this shape requires loading all target arguments (in declaration order) before invoking the hook.
         */
        private final boolean passesArgs;
        /**
         * Whether this shape includes a trailing {@code CallbackInfo} parameter.
         */
        private final boolean usesCallbackInfo;
        /**
         * Whether this shape includes a trailing {@code CallbackInfoReturnable} parameter.
         */
        private final boolean usesCallbackInfoReturnable;

        /**
         * Constructs a new shape with the given flags.
         *
         * @param requiresThis     whether {@code this} must be loaded before invoking the hook
         * @param passesArgs       whether all target arguments must be loaded before invoking the hook
         * @param usesCallbackInfo whether a trailing {@code CallbackInfo} parameter is present
         */
        Kind(final boolean requiresThis, final boolean passesArgs, final boolean usesCallbackInfo) {
            this(requiresThis, passesArgs, usesCallbackInfo, false);
        }

        /**
         * Constructs a new shape with the given flags.
         *
         * @param requiresThis               whether {@code this} must be loaded before invoking the hook
         * @param passesArgs                 whether all target arguments must be loaded before invoking the hook
         * @param usesCallbackInfo           whether a trailing {@code CallbackInfo} parameter is present
         * @param usesCallbackInfoReturnable whether a trailing {@code CallbackInfoReturnable} parameter is present
         */
        Kind(final boolean requiresThis, final boolean passesArgs, final boolean usesCallbackInfo, final boolean usesCallbackInfoReturnable) {
            this.requiresThis = requiresThis;
            this.passesArgs = passesArgs;
            this.usesCallbackInfo = usesCallbackInfo;
            this.usesCallbackInfoReturnable = usesCallbackInfoReturnable;
        }

        /**
         * Whether this shape requires loading {@code this} (local slot 0) before invoking the hook.
         *
         * @return {@code true} if {@code this} must be loaded, otherwise {@code false}
         */
        public boolean requiresThis() {
            return this.requiresThis;
        }

        /**
         * Whether this shape requires loading all target method arguments (in declaration order).
         *
         * @return {@code true} if target arguments must be loaded, otherwise {@code false}
         */
        public boolean passesArgs() {
            return this.passesArgs;
        }

        /**
         * Whether this shape includes a trailing {@code CallbackInfo} parameter.
         *
         * @return {@code true} if a {@code CallbackInfo} argument is present, otherwise {@code false}
         */
        public boolean usesCallbackInfo() {
            return this.usesCallbackInfo;
        }

        /**
         * Whether this shape includes a trailing {@code CallbackInfoReturnable} parameter.
         *
         * @return {@code true} if a {@code CallbackInfoReturnable} argument is present, otherwise {@code false}
         */
        public boolean usesCallbackInfoReturnable() {
            return this.usesCallbackInfoReturnable;
        }
    }

    /**
     * Matches a hook descriptor against a target method and returns the {@link Kind}.
     *
     * <p>Checks:</p>
     * <ul>
     *   <li>Hook return type must always be {@code void}.</li>
     *   <li>Positional compatibility of optional {@code this}, target arguments, and optional trailing {@code CallbackInfo}/{@code CallbackInfoReturnable}.</li>
     *   <li>{@code CallbackInfo} is only valid for target methods with a void return type.</li>
     *   <li>{@code CallbackInfoReturnable} is only valid for target methods with a non-void return type.</li>
     *   <li>Only one trailing callback parameter is allowed.</li>
     * </ul>
     *
     * @param instance          {@code true} if the target method is an instance method (not {@code ACC_STATIC})
     * @param ownerInternal     internal JVM name of the target owner class (e.g., {@code com/example/Foo}); must not be {@code null}
     * @param targetDesc        descriptor of the target method (e.g., {@code (I)Ljava/lang/String;}); must not be {@code null}
     * @param hookDesc          descriptor of the hook method to validate; must not be {@code null}
     * @param ciInternalName    internal JVM name of the {@code CallbackInfo} class; may be {@code null} if CI is not supported
     * @param cirInternalName   internal JVM name of the {@code CallbackInfoReturnable} class; may be {@code null} if CIR is not supported
     * @return the matched {@link Kind}, or {@code null} if incompatible
     */
    @Nullable
    public static Kind match(final boolean instance,
                             @NotNull final String ownerInternal,
                             @NotNull final String targetDesc,
                             @NotNull final String hookDesc,
                             @Nullable final String ciInternalName,
                             @Nullable final String cirInternalName) {

        final Type[] tArgs = Type.getArgumentTypes(targetDesc);  // Target arguments
        final Type[] hArgs = Type.getArgumentTypes(hookDesc);    // Hook arguments
        final Type targetRet = Type.getReturnType(targetDesc);   // Target return type
        final Type hookRet = Type.getReturnType(hookDesc);       // Hook return type

        // Hook must always return void
        if (!Type.VOID_TYPE.equals(hookRet)) {
            return null;
        }

        final boolean targetIsVoid = Type.VOID_TYPE.equals(targetRet);
        final int hLen = hArgs.length;
        final Type ownerT = Type.getObjectType(ownerInternal);

        // -------------------------------------
        // Basic forms without CI or CIR
        // -------------------------------------

        // ()V
        if (hLen == 0) {
            return Kind.NONE;
        }

        // (OWNER;)V
        if (hLen == 1 && hArgs[0].equals(ownerT)) {
            return instance ? Kind.THIS : null;
        }

        // (args...)V
        if (hLen == tArgs.length && allEqual(hArgs, 0, tArgs, 0, tArgs.length)) {
            return Kind.ARGS;
        }

        // (OWNER; args...)V
        if (hLen == tArgs.length + 1 && hArgs[0].equals(ownerT)) {
            if (!instance) {
                return null;
            }
            if (allEqual(hArgs, 1, tArgs, 0, tArgs.length)) {
                return Kind.THIS_ARGS;
            }
        }

        // -------------------------------------
        // CI Handling (void target only)
        // -------------------------------------
        if (ciInternalName != null && targetIsVoid && isLastObjectType(hArgs, ciInternalName)) {
            final int coreLen = hLen - 1;

            // (CallbackInfo)V
            if (coreLen == 0) {
                return Kind.NONE_CI;
            }

            // (OWNER; CallbackInfo)V
            if (coreLen == 1 && hArgs[0].equals(ownerT)) {
                return instance ? Kind.THIS_CI : null;
            }

            // (args..., CallbackInfo)V
            if (coreLen == tArgs.length && allEqual(hArgs, 0, tArgs, 0, tArgs.length)) {
                return Kind.ARGS_CI;
            }

            // (OWNER; args..., CallbackInfo)V
            if (coreLen == tArgs.length + 1 && hArgs[0].equals(ownerT)) {
                if (!instance) {
                    return null;
                }
                if (allEqual(hArgs, 1, tArgs, 0, tArgs.length)) {
                    return Kind.THIS_ARGS_CI;
                }
            }
        }

        // -------------------------------------
        // CIR Handling (non-void target only)
        // -------------------------------------
        if (cirInternalName != null && !targetIsVoid && isLastObjectType(hArgs, cirInternalName)) {
            final int coreLen = hLen - 1;

            // (CallbackInfoReturnable)V
            if (coreLen == 0) {
                return Kind.NONE_CIR;
            }

            // (OWNER; CallbackInfoReturnable)V
            if (coreLen == 1 && hArgs[0].equals(ownerT)) {
                return instance ? Kind.THIS_CIR : null;
            }

            // (args..., CallbackInfoReturnable)V
            if (coreLen == tArgs.length && allEqual(hArgs, 0, tArgs, 0, tArgs.length)) {
                return Kind.ARGS_CIR;
            }

            // (OWNER; args..., CallbackInfoReturnable)V
            if (coreLen == tArgs.length + 1 && hArgs[0].equals(ownerT)) {
                if (!instance) {
                    return null;
                }
                if (allEqual(hArgs, 1, tArgs, 0, tArgs.length)) {
                    return Kind.THIS_ARGS_CIR;
                }
            }
        }

        // No valid match
        return null;
    }


    /**
     * Compares two slices of {@link Type} arrays for equality.
     *
     * @param a    left array; must not be {@code null}
     * @param aOff starting offset in {@code a}
     * @param b    right array; must not be {@code null}
     * @param bOff starting offset in {@code b}
     * @param len  number of elements to compare
     * @return {@code true} if all compared elements are equal; otherwise {@code false}
     */
    private static boolean allEqual(@NotNull final Type[] a,
                                    final int aOff,
                                    @NotNull final Type[] b,
                                    final int bOff,
                                    final int len) {
        for (int i = 0; i < len; i++) {
            if (!a[aOff + i].equals(b[bOff + i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Determines whether the last element of the given {@link Type} array is an object type with the given internal name.
     *
     * @param hArgs        array of types; must not be {@code null} and must have at least one element
     * @param internalName expected internal name of the last element (e.g., {@code de/splatgames/.../CallbackInfo}); must not be {@code null}
     * @return {@code true} if the last element is an object type with the given internal name; otherwise {@code false}
     */
    private static boolean isLastObjectType(@NotNull final Type[] hArgs, @NotNull final String internalName) {
        final Type last = hArgs[hArgs.length - 1];
        return last.getSort() == Type.OBJECT && internalName.equals(last.getInternalName());
    }


    /**
     * Determines whether the target access flags denote an instance method.
     *
     * @param access raw access flags of the target method (ASM {@code ACC_*} bitset)
     * @return {@code true} if the target is an instance method (not {@code ACC_STATIC}); otherwise {@code false}
     */
    public static boolean isInstance(final int access) {
        return (access & Opcodes.ACC_STATIC) == 0;
    }

    /**
     * Emits {@code ALOAD 0} if the given {@link Kind} requires loading {@code this}.
     *
     * @param mv   downstream method visitor; must not be {@code null}
     * @param kind matched hook shape; must not be {@code null}
     */
    public static void emitThisIfNeeded(@NotNull final MethodVisitor mv,
                                        @NotNull final Kind kind) {
        if (kind.requiresThis()) {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
        }
    }

    /**
     * Emits load instructions for all target arguments from the local variable table, starting at {@code localStart}.
     *
     * @param mv         downstream method visitor; must not be {@code null}
     * @param targetDesc descriptor of the target method; must not be {@code null}
     * @param localStart starting local index for the first target argument (typically {@code 1} for instance targets, {@code 0} for static)
     * @return the next free local index after the last loaded argument
     * @throws IllegalArgumentException if a parameter type is unsupported
     */
    public static int emitArgs(@NotNull final MethodVisitor mv,
                               @NotNull final String targetDesc,
                               final int localStart) {
        int local = localStart;
        for (final Type t : Type.getArgumentTypes(targetDesc)) {
            switch (t.getSort()) {
                case Type.BOOLEAN, Type.BYTE, Type.SHORT, Type.CHAR, Type.INT -> mv.visitVarInsn(Opcodes.ILOAD, local);
                case Type.FLOAT -> mv.visitVarInsn(Opcodes.FLOAD, local);
                case Type.LONG -> mv.visitVarInsn(Opcodes.LLOAD, local);
                case Type.DOUBLE -> mv.visitVarInsn(Opcodes.DLOAD, local);
                case Type.ARRAY, Type.OBJECT -> mv.visitVarInsn(Opcodes.ALOAD, local);
                default -> throw new IllegalArgumentException("Unsupported argument type in descriptor: " + t);
            }
            local += (t == Type.LONG_TYPE || t == Type.DOUBLE_TYPE) ? 2 : 1;
        }
        return local;
    }

    /**
     * Allocates and initializes a new {@code CallbackInfo} instance and stores it in local slot {@code ciLocal}
     * if the given {@link Kind} uses {@code CallbackInfo}. Otherwise, does nothing and returns {@code -1}.
     *
     * @param mv             downstream method visitor; must not be {@code null}
     * @param kind           matched hook shape; must not be {@code null}
     * @param ciInternalName internal JVM name of {@code CallbackInfo}; must not be {@code null} if {@code kind} uses CI
     * @param ciLocal        local slot index to store the new instance (must be a free local)
     * @return {@code ciLocal} if a new instance was created; {@code -1} if the shape does not use {@code CallbackInfo}
     * @throws IllegalArgumentException if the shape uses CI but {@code ciInternalName} is {@code null}
     */
    public static int newCallbackInfoIfNeeded(@NotNull final MethodVisitor mv,
                                              @NotNull final Kind kind,
                                              @Nullable final String ciInternalName,
                                              final int ciLocal) {
        if (!kind.usesCallbackInfo()) {
            return -1;
        }
        if (ciInternalName == null) {
            throw new IllegalArgumentException("CallbackInfo internal name required for shape: " + kind);
        }
        mv.visitTypeInsn(Opcodes.NEW, ciInternalName);
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, ciInternalName, "<init>", "()V", false);
        mv.visitVarInsn(Opcodes.ASTORE, ciLocal);
        return ciLocal;
    }

    /**
     * Stores the return value (top of stack) into the given local slot, using the appropriate store instruction
     * for the return type of the target method.
     *
     * @param mv         downstream method visitor; must not be {@code null}
     * @param targetDesc descriptor of the target method; must not be {@code null}
     * @param retLocal   local slot index to store the return value (must be a free local)
     * @return {@code retLocal} for convenience
     * @throws IllegalArgumentException if the return type is unsupported
     */
    public static int storeReturnValueBeforeTail(@NotNull final MethodVisitor mv, @NotNull final String targetDesc, final int retLocal) {
        final Type ret = Type.getReturnType(targetDesc);
        switch (ret.getSort()) {
            case Type.BOOLEAN, Type.BYTE, Type.SHORT, Type.CHAR, Type.INT -> mv.visitVarInsn(Opcodes.ISTORE, retLocal);
            case Type.FLOAT -> mv.visitVarInsn(Opcodes.FSTORE, retLocal);
            case Type.LONG -> mv.visitVarInsn(Opcodes.LSTORE, retLocal);
            case Type.DOUBLE -> mv.visitVarInsn(Opcodes.DSTORE, retLocal);
            case Type.ARRAY, Type.OBJECT -> mv.visitVarInsn(Opcodes.ASTORE, retLocal);
            default -> throw new IllegalArgumentException("Unsupported return type: " + ret);
        }
        return retLocal;
    }

    /**
     * Loads the return value from the given local slot onto the stack, using the appropriate load instruction
     * for the return type of the target method.
     *
     * @param mv         downstream method visitor; must not be {@code null}
     * @param targetDesc descriptor of the target method; must not be {@code null}
     * @param retLocal   local slot index where the return value was previously stored
     * @throws IllegalArgumentException if the return type is unsupported
     */
    public static void loadReturnValueFromLocal(@NotNull final MethodVisitor mv, @NotNull final String targetDesc, final int retLocal) {
        final Type ret = Type.getReturnType(targetDesc);
        switch (ret.getSort()) {
            case Type.BOOLEAN, Type.BYTE, Type.SHORT, Type.CHAR, Type.INT -> mv.visitVarInsn(Opcodes.ILOAD, retLocal);
            case Type.FLOAT -> mv.visitVarInsn(Opcodes.FLOAD, retLocal);
            case Type.LONG -> mv.visitVarInsn(Opcodes.LLOAD, retLocal);
            case Type.DOUBLE -> mv.visitVarInsn(Opcodes.DLOAD, retLocal);
            case Type.ARRAY, Type.OBJECT -> mv.visitVarInsn(Opcodes.ALOAD, retLocal);
            default -> throw new IllegalArgumentException("Unsupported return type: " + ret);
        }
    }

    /**
     * Emits the appropriate return instruction for the given return type.
     *
     * @param mv  downstream method visitor; must not be {@code null}
     * @param ret the return type of the target method; must not be {@code null}
     * @throws IllegalArgumentException if the return type is unsupported
     */
    public static void emitReturnFor(@NotNull final MethodVisitor mv, @NotNull final Type ret) {
        switch (ret.getSort()) {
            case Type.VOID -> mv.visitInsn(Opcodes.RETURN);
            case Type.BOOLEAN, Type.BYTE, Type.SHORT, Type.CHAR, Type.INT -> mv.visitInsn(Opcodes.IRETURN);
            case Type.FLOAT -> mv.visitInsn(Opcodes.FRETURN);
            case Type.LONG -> mv.visitInsn(Opcodes.LRETURN);
            case Type.DOUBLE -> mv.visitInsn(Opcodes.DRETURN);
            case Type.ARRAY, Type.OBJECT -> mv.visitInsn(Opcodes.ARETURN);
            default -> throw new IllegalArgumentException("Unsupported return type for return opcode: " + ret);
        }
    }

    /**
     * Allocates and initializes a new {@code CallbackInfoReturnable} instance and stores it in local slot {@code cirLocal}
     * if the given {@link Kind} uses {@code CallbackInfoReturnable}. Otherwise, does nothing and returns {@code -1}.
     *
     * @param mv              downstream method visitor; must not be {@code null}
     * @param kind            matched hook shape; must not be {@code null}
     * @param cirInternalName internal JVM name of {@code CallbackInfoReturnable}; must not be {@code null} if {@code kind} uses CIR
     * @param cirLocal        local slot index to store the new instance (must be a free local)
     * @return {@code cirLocal} if a new instance was created; {@code -1} if the shape does not use {@code CallbackInfoReturnable}
     * @throws IllegalArgumentException if the shape uses CIR but {@code cirInternalName} is {@code null}
     */
    public static int newCallbackInfoReturnableIfNeeded(@NotNull final MethodVisitor mv, @NotNull final Kind kind, @Nullable final String cirInternalName, final int cirLocal) {
        if (!kind.usesCallbackInfoReturnable()) {
            return -1;
        }
        if (cirInternalName == null) {
            throw new IllegalArgumentException("CIR internal name required: " + kind);
        }
        mv.visitTypeInsn(Opcodes.NEW, cirInternalName);
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, cirInternalName, "<init>", "()V", false);
        mv.visitVarInsn(Opcodes.ASTORE, cirLocal);
        return cirLocal;
    }

    /**
     * Emits {@code ALOAD cirLocal} if the given {@link Kind} includes a trailing {@code CallbackInfoReturnable} parameter.
     *
     * @param mv       downstream method visitor; must not be {@code null}
     * @param kind     matched hook shape; must not be {@code null}
     * @param cirLocal local slot where a previously created {@code CallbackInfoReturnable} instance is stored
     */
    public static void emitLoadCallbackInfoReturnableIfNeeded(@NotNull final MethodVisitor mv, @NotNull final Kind kind, final int cirLocal) {
        if (kind.usesCallbackInfoReturnable()) {
            mv.visitVarInsn(Opcodes.ALOAD, cirLocal);
        }
    }

    /**
     * Emits {@code ALOAD ciLocal} if the given {@link Kind} includes a trailing {@code CallbackInfo} parameter.
     *
     * @param mv      downstream method visitor; must not be {@code null}
     * @param kind    matched hook shape; must not be {@code null}
     * @param ciLocal local slot where a previously created {@code CallbackInfo} instance is stored
     */
    public static void emitLoadCallbackInfoIfNeeded(@NotNull final MethodVisitor mv,
                                                    @NotNull final Kind kind,
                                                    final int ciLocal) {
        if (kind.usesCallbackInfo()) {
            mv.visitVarInsn(Opcodes.ALOAD, ciLocal);
        }
    }

    /**
     * Constructs the descriptor for {@code CallbackInfo.getReturn()} or {@code CallbackInfoReturnable.getReturn()}.
     *
     * @param ret the return type of the target method; must not be {@code null}
     * @return the descriptor string (e.g., {@code ()I} for {@code int})
     */
    @NotNull
    public static String cirGetterDescFor(@NotNull final Type ret) {
        return "()" + ret.getDescriptor();
    }

    /**
     * Constructs the descriptor for {@code CallbackInfoReturnable.setReturn(R)}.
     *
     * @param ret the return type of the target method; must not be {@code null}
     * @return the descriptor string (e.g., {@code (I)V} for {@code int})
     */
    @NotNull
    public static String cirSetterDescFor(@NotNull final Type ret) {
        return "(" + ret.getDescriptor() + ")V";
    }

    /**
     * Emits a call to {@code CallbackInfoReturnable.getReturn()}.
     *
     * @param mv          downstream method visitor; must not be {@code null}
     * @param cirInternal internal JVM name of {@code CallbackInfoReturnable}; must not be {@code null}
     * @param ret         the return type of the target method; must not be {@code null}
     */
    public static void emitCirGetReturn(@NotNull final MethodVisitor mv, @NotNull final String cirInternal, @NotNull final Type ret) {
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, cirInternal, "getReturn", cirGetterDescFor(ret), false);
    }

    /**
     * Emits a call to {@code CallbackInfoReturnable.setReturn(R)}.
     *
     * @param mv          downstream method visitor; must not be {@code null}
     * @param cirInternal internal JVM name of {@code CallbackInfoReturnable}; must not be {@code null}
     * @param ret         the return type of the target method; must not be {@code null}
     */
    public static void emitCirSetReturn(@NotNull final MethodVisitor mv, @NotNull final String cirInternal, @NotNull final Type ret) {
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, cirInternal, "setReturn", cirSetterDescFor(ret), false);
    }

    /**
     * Convenience wrapper for {@link Kind#usesCallbackInfo()} and {@link Kind#usesCallbackInfoReturnable()}.
     *
     * @param kind matched hook shape; must not be {@code null}
     * @return {@code true} if the shape uses either form of callback info; otherwise {@code false}
     */
    public static boolean usesAnyCallback(@NotNull final Kind kind) {
        return kind.usesCallbackInfo() || kind.usesCallbackInfoReturnable();
    }

    /**
     * Convenience wrapper for {@link Kind#usesCallbackInfo()}.
     *
     * @param kind matched hook shape; must not be {@code null}
     * @return {@code true} if the shape uses a trailing {@code CallbackInfo} parameter; otherwise {@code false}
     */
    public static boolean usesCallbackInfo(@NotNull final Kind kind) {
        return kind.usesCallbackInfo();
    }

    /**
     * Convenience wrapper for {@link Kind#requiresThis()}.
     *
     * @param kind matched hook shape; must not be {@code null}
     * @return {@code true} if {@code this} must be loaded; otherwise {@code false}
     */
    public static boolean requiresThis(@NotNull final Kind kind) {
        return kind.requiresThis();
    }

    /**
     * Convenience wrapper for {@link Kind#passesArgs()}.
     *
     * @param kind matched hook shape; must not be {@code null}
     * @return {@code true} if target arguments must be loaded; otherwise {@code false}
     */
    public static boolean passesArgs(@NotNull final Kind kind) {
        return kind.passesArgs();
    }

    /**
     * Determines if the return type of the given method descriptor is void.
     *
     * @param targetDesc the method descriptor to be analyzed, must not be null
     * @return true if the return type of the method descriptor is void, false otherwise
     */
    public static boolean isVoid(@NotNull final String targetDesc) {
        return Type.VOID_TYPE.equals(Type.getReturnType(targetDesc));
    }
}
