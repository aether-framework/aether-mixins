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
 *     HookShape.match(instance, ownerInternal, targetDesc, hookDesc, "de/splatgames/.../CallbackInfo");
 * if (kind == null) { * incompatible signature * }
 *
 * int local = instance ? 1 : 0;
 * HookShape.emitThisIfNeeded(mv, kind);
 * if (HookShape.passesArgs(kind)) {
 *   local = HookShape.emitArgs(mv, targetDesc, local);
 * }
 * final int ciLocal = HookShape.newCallbackInfoIfNeeded(mv, kind, "de/splatgames/.../CallbackInfo", local);
 * HookShape.emitLoadCallbackInfoIfNeeded(mv, kind, ciLocal);
 * // then: mv.visitMethodInsn(INVOKESTATIC, hookOwner, hookName, hookDesc, false);
 * }</pre>
 *
 * <p><b>Thread-safety:</b> This utility class is stateless and thread-safe.</p>
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
        THIS_ARGS_CI(true, true, true);

        private final boolean requiresThis;
        private final boolean passesArgs;
        private final boolean usesCallbackInfo;

        Kind(final boolean requiresThis, final boolean passesArgs, final boolean usesCallbackInfo) {
            this.requiresThis = requiresThis;
            this.passesArgs = passesArgs;
            this.usesCallbackInfo = usesCallbackInfo;
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
    }

    // --------------------------------------------------------------------------------------------
    // Descriptor matching
    // --------------------------------------------------------------------------------------------

    /**
     * Matches a hook descriptor against a target method and returns the {@link Kind}.
     *
     * <p>Checks:</p>
     * <ul>
     *   <li>Hook return type is {@code void}.</li>
     *   <li>Positional compatibility of optional {@code this}, target arguments, and optional trailing {@code CallbackInfo}.</li>
     *   <li>If {@code ciInternalName} is non-null, the last parameter must match that object type to be considered CI.</li>
     * </ul>
     *
     * @param instance       {@code true} if the target method is an instance method (not {@code ACC_STATIC})
     * @param ownerInternal  internal JVM name of the target owner class (e.g., {@code com/example/Foo}); must not be {@code null}
     * @param targetDesc     descriptor of the target method (e.g., {@code (I)Ljava/lang/String;}); must not be {@code null}
     * @param hookDesc       descriptor of the hook method to validate; must not be {@code null}
     * @param ciInternalName internal JVM name of the {@code CallbackInfo} class; if {@code null}, CI is not considered
     * @return the matched {@link Kind}, or {@code null} if incompatible
     */
    @Nullable
    public static Kind match(final boolean instance,
                             @NotNull final String ownerInternal,
                             @NotNull final String targetDesc,
                             @NotNull final String hookDesc,
                             @Nullable final String ciInternalName) {
        final Type[] tArgs = Type.getArgumentTypes(targetDesc);
        final Type[] hArgs = Type.getArgumentTypes(hookDesc);
        final Type hRet = Type.getReturnType(hookDesc);
        if (!Type.VOID_TYPE.equals(hRet)) {
            return null;
        }

        final int hLen = hArgs.length;
        final Type ownerT = Type.getObjectType(ownerInternal);

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

        // With trailing CallbackInfo
        if (ciInternalName != null && hLen >= 1 && isLastCallbackInfo(hArgs, ciInternalName)) {
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
     * Checks whether the last parameter type in {@code hArgs} is an object whose internal name equals {@code ciInternalName}.
     *
     * @param hArgs          hook argument types; must not be {@code null}
     * @param ciInternalName internal JVM name of {@code CallbackInfo}; must not be {@code null}
     * @return {@code true} if the last parameter matches the CI type; otherwise {@code false}
     */
    private static boolean isLastCallbackInfo(@NotNull final Type[] hArgs,
                                              @NotNull final String ciInternalName) {
        final Type last = hArgs[hArgs.length - 1];
        return last.getSort() == Type.OBJECT && ciInternalName.equals(last.getInternalName());
    }

    // --------------------------------------------------------------------------------------------
    // Operand emission helpers
    // --------------------------------------------------------------------------------------------

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

    // --------------------------------------------------------------------------------------------
    // Convenience checks
    // --------------------------------------------------------------------------------------------

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
}
