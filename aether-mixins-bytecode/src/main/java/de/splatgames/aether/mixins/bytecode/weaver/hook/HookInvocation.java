package de.splatgames.aether.mixins.bytecode.weaver.hook;

import org.jetbrains.annotations.NotNull;

/**
 * Defines how a resolved hook method is invoked at weave-time: either as a
 * {@linkplain #STATIC static} invocation or as an {@linkplain #INSTANCE instance}
 * invocation on a specific receiver.
 *
 * <p>In Aether Mixins, hooks can be compiled and woven in different ways. This enum
 * communicates to weaving adapters whether the call site should use {@code INVOKESTATIC}
 * (no implicit receiver) or an instance opcode (e.g., {@code INVOKEVIRTUAL} /
 * {@code INVOKESPECIAL}) with the receiver on the operand stack. Adapters may also
 * rely on this information to marshal operands (e.g., pushing {@code this} if required)
 * or to select the correct call opcode when merged instance hooks are copied into
 * the target class.</p>
 *
 * <h2>Usage</h2>
 * <ul>
 *   <li>{@link #STATIC}: The hook does not consume a receiver from the stack and must be
 *       invoked using {@code INVOKESTATIC}. Parameters and return semantics are governed
 *       solely by the hook descriptor.</li>
 *   <li>{@link #INSTANCE}: The hook consumes a receiver from the stack (or otherwise expects
 *       a receiver to be provided), and is invoked using an instance opcode (typically
 *       {@code INVOKESPECIAL} when calling the merged copy inside the target class).</li>
 * </ul>
 *
 * <p>Callers should not infer visibility or ownership from this enum; it describes only the
 * invocation <em>shape</em>. Resolution, accessibility bridging, and potential method name
 * remapping (e.g., due to {@code @Unique}) are performed elsewhere in the pipeline.</p>
 *
 * @author Erik Pförtner
 * @see de.splatgames.aether.mixins.bytecode.weaver.hook.ResolvedHook#invocation()
 * @since 0.2.0
 */
public enum HookInvocation {
    /**
     * The hook must be invoked as a static method call
     * (i.e., using the {@code INVOKESTATIC} bytecode instruction).
     */
    STATIC,

    /**
     * The hook must be invoked as an instance method call, consuming a receiver
     * (i.e., using an instance bytecode instruction such as {@code INVOKESPECIAL}
     * or {@code INVOKEVIRTUAL}, depending on context).
     */
    INSTANCE;

    /**
     * Returns the corresponding {@link HookInvocation} for a boolean flag indicating
     * whether the invoked member is static.
     *
     * <p>This is a small convenience for sites that only track a binary “is static”
     * property, avoiding conditional logic at the call site.</p>
     *
     * @param isStatic {@code true} if the hook is invoked statically; {@code false} for an instance invocation
     * @return {@link #STATIC} when {@code isStatic} is {@code true}; otherwise {@link #INSTANCE}
     * @implNote This method performs no validation of ownership or accessibility; those concerns are
     * handled by the resolution layer (e.g., when merging instance hooks into the target class).
     */
    @NotNull
    public static HookInvocation isStatic(final boolean isStatic) {
        return isStatic ? STATIC : INSTANCE;
    }

    /**
     * Returns {@code true} if this invocation mode represents a static call.
     *
     * @return {@code true} if {@link #STATIC}; {@code false} otherwise
     * @implNote This is a constant-time identity check and does not consult any external state.
     */
    public boolean isStatic() {
        return this == STATIC;
    }

    /**
     * Returns {@code true} if this invocation mode represents an instance call.
     *
     * @return {@code true} if {@link #INSTANCE}; {@code false} otherwise
     * @implNote This is a constant-time identity check and does not consult any external state.
     */
    public boolean isInstance() {
        return this == INSTANCE;
    }
}
