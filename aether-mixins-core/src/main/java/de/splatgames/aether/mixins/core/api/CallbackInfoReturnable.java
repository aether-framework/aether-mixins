package de.splatgames.aether.mixins.core.api;

import de.splatgames.aether.mixins.core.api.cancel.exception.CancellationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Callback info that additionally carries a (potential) return value for non-void methods.
 *
 * <p>The weaver passes an instance of this class into injected hook methods for targets
 * that return a value. The injected code can set a replacement value via
 * {@link #setReturnValue(Object)} and (if {@linkplain #isCancellable() cancellable}) call
 * {@link #cancel()} to short-circuit the original method execution.</p>
 *
 * <h2>Usage</h2>
 * <blockquote><pre>
 * {@code
 * @Inject(method = "compute(I)I", at = Inject.At.HEAD, cancellable = true)
 * private static void onCompute(final int input, final CallbackInfoReturnable<Integer> cir) {
 *     if (input < 0) {
 *         cir.setReturnValue(0);
 *         cir.cancel(); // the original compute(...) will not run
 *     }
 * }}
 * </pre></blockquote>
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>If this callback is cancelled, the weaver must use the {@linkplain #getReturnValue() current return value}
 *       as the method's effective return.</li>
 *   <li>If no value was set, {@code null} may be returned for reference types
 *       (the weaver may box/unbox as needed for primitives).</li>
 *   <li>This type is not thread-safe. It is intended for single-threaded use during weaving.</li>
 * </ul>
 *
 * @author Erik Pförtner
 * @apiNote For primitive return types, the weaver is expected to handle boxing/unboxing.
 * Users should supply the boxed type for {@code T} (e.g., {@code Integer} for {@code int}).
 * @implNote The cancellation semantics are inherited from {@link CallbackInfo}.
 * This class does not alter {@link #cancel()} behavior.
 * @since 0.2.0
 */
public final class CallbackInfoReturnable<T> extends CallbackInfo {

    /**
     * The current (possibly replacement) return value. May be {@code null} for reference types.
     */
    @Nullable
    private T returnValue;

    /**
     * Flag indicating whether a return value has been explicitly set via {@link #setReturnValue(Object)}.
     * This is distinct from {@code returnValue != null} to allow distinguishing between "no value set"
     * and "value set to null" for reference types.
     */
    private boolean hasValueFlag = false;

    /**
     * Creates a new {@code CallbackInfoReturnable} with no initial value.
     *
     * @param methodName  the (simple) name of the intercepted method; never {@code null}
     * @param cancellable whether this callback supports cancellation
     * @throws NullPointerException     if {@code methodName} is {@code null} (e.g., plain javac, no IDEA instrumentation)
     * @throws IllegalArgumentException if {@code methodName} is {@code null} and JetBrains @NotNull
     *                                  runtime instrumentation is active (IDEA compiler/bytecode instrumentation)
     * @implNote Exception type depends on the toolchain: javac vs. IDEA-instrumented builds.
     * @apiNote This constructor initializes the return value to {@code null}
     * and marks {@link #hasReturnValue()} as {@code false}.
     */
    public CallbackInfoReturnable(@NotNull final String methodName, final boolean cancellable) {
        super(Objects.requireNonNull(methodName, "methodName"), cancellable);
        this.returnValue = null;
        this.hasValueFlag = false;
    }

    /**
     * Creates a new {@code CallbackInfoReturnable} with an initial value.
     *
     * @param methodName  the (simple) name of the intercepted method; never {@code null}
     * @param cancellable whether this callback supports cancellation
     * @throws NullPointerException     if {@code methodName} is {@code null} (e.g., plain javac, no IDEA instrumentation)
     * @throws IllegalArgumentException if {@code methodName} is {@code null} and JetBrains @NotNull
     *                                  runtime instrumentation is active (IDEA compiler/bytecode instrumentation)
     * @implNote Exception type depends on the toolchain: javac vs. IDEA-instrumented builds.
     * @apiNote This constructor marks {@link #hasReturnValue()} as {@code true}
     * regardless of whether {@code returnValue} is {@code null}.
     */
    public CallbackInfoReturnable(
            @NotNull final String methodName,
            final boolean cancellable,
            @Nullable final T returnValue
    ) {
        super(Objects.requireNonNull(methodName, "methodName"), cancellable);
        this.returnValue = returnValue;
        this.hasValueFlag = true;
    }

    /**
     * Creates a new {@code CallbackInfoReturnable} with an initial primitive value.
     *
     * <p>This constructor is provided for convenience to avoid boxing at the call site.
     * The weaver is expected to handle boxing/unboxing as needed.</p>
     *
     * @param methodName  the (simple) name of the intercepted method; never {@code null}
     * @param cancellable whether this callback supports cancellation
     * @param returnValue the initial return value
     * @throws NullPointerException     if {@code methodName} is {@code null} (e.g., plain javac, no IDEA instrumentation)
     * @throws IllegalArgumentException if {@code methodName} is {@code null} and JetBrains @NotNull
     *                                  runtime instrumentation is active (IDEA compiler/bytecode instrumentation)
     * @implNote Exception type depends on the toolchain: javac vs. IDEA-instrumented builds.
     * @apiNote This constructor marks {@link #hasReturnValue()} as {@code true}.
     */
    @SuppressWarnings("unchecked")
    public CallbackInfoReturnable(
            @NotNull final String methodName,
            final boolean cancellable,
            final int returnValue
    ) {
        super(Objects.requireNonNull(methodName, "methodName"), cancellable);
        // This cast is safe because T is expected to be the boxed type when used with primitives.
        this.returnValue = (T) Integer.valueOf(returnValue);
        this.hasValueFlag = true;
    }

    /**
     * Creates a new {@code CallbackInfoReturnable} with an initial primitive value.
     *
     * <p>This constructor is provided for convenience to avoid boxing at the call site.
     * The weaver is expected to handle boxing/unboxing as needed.</p>
     *
     * @param methodName  the (simple) name of the intercepted method; never {@code null}
     * @param cancellable whether this callback supports cancellation
     * @param returnValue the initial return value
     * @throws NullPointerException     if {@code methodName} is {@code null} (e.g., plain javac, no IDEA instrumentation)
     * @throws IllegalArgumentException if {@code methodName} is {@code null} and JetBrains @NotNull
     *                                  runtime instrumentation is active (IDEA compiler/bytecode instrumentation)
     * @implNote Exception type depends on the toolchain: javac vs. IDEA-instrumented builds.
     * @apiNote This constructor marks {@link #hasReturnValue()} as {@code true}.
     */
    @SuppressWarnings("unchecked")
    public CallbackInfoReturnable(
            @NotNull final String methodName,
            final boolean cancellable,
            final long returnValue
    ) {
        super(Objects.requireNonNull(methodName, "methodName"), cancellable);
        // This cast is safe because T is expected to be the boxed type when used with primitives.
        this.returnValue = (T) Long.valueOf(returnValue);
        this.hasValueFlag = true;
    }

    /**
     * Creates a new {@code CallbackInfoReturnable} with an initial primitive value.
     *
     * <p>This constructor is provided for convenience to avoid boxing at the call site.
     * The weaver is expected to handle boxing/unboxing as needed.</p>
     *
     * @param methodName  the (simple) name of the intercepted method; never {@code null}
     * @param cancellable whether this callback supports cancellation
     * @param returnValue the initial return value
     * @throws NullPointerException     if {@code methodName} is {@code null} (e.g., plain javac, no IDEA instrumentation)
     * @throws IllegalArgumentException if {@code methodName} is {@code null} and JetBrains @NotNull
     *                                  runtime instrumentation is active (IDEA compiler/bytecode instrumentation)
     * @implNote Exception type depends on the toolchain: javac vs. IDEA-instrumented builds.
     * @apiNote This constructor marks {@link #hasReturnValue()} as {@code true}.
     */
    @SuppressWarnings("unchecked")
    public CallbackInfoReturnable(
            @NotNull final String methodName,
            final boolean cancellable,
            final float returnValue
    ) {
        super(Objects.requireNonNull(methodName, "methodName"), cancellable);
        // This cast is safe because T is expected to be the boxed type when used with primitives.
        this.returnValue = (T) Float.valueOf(returnValue);
        this.hasValueFlag = true;
    }

    /**
     * Creates a new {@code CallbackInfoReturnable} with an initial primitive value.
     *
     * <p>This constructor is provided for convenience to avoid boxing at the call site.
     * The weaver is expected to handle boxing/unboxing as needed.</p>
     *
     * @param methodName  the (simple) name of the intercepted method; never {@code null}
     * @param cancellable whether this callback supports cancellation
     * @param returnValue the initial return value
     * @throws NullPointerException     if {@code methodName} is {@code null} (e.g., plain javac, no IDEA instrumentation)
     * @throws IllegalArgumentException if {@code methodName} is {@code null} and JetBrains @NotNull
     *                                  runtime instrumentation is active (IDEA compiler/bytecode instrumentation)
     * @implNote Exception type depends on the toolchain: javac vs. IDEA-instrumented builds.
     * @apiNote This constructor marks {@link #hasReturnValue()} as {@code true}.
     */
    @SuppressWarnings("unchecked")
    public CallbackInfoReturnable(
            @NotNull final String methodName,
            final boolean cancellable,
            final double returnValue
    ) {
        super(Objects.requireNonNull(methodName, "methodName"), cancellable);
        // This cast is safe because T is expected to be the boxed type when used with primitives.
        this.returnValue = (T) Double.valueOf(returnValue);
        this.hasValueFlag = true;
    }

    /**
     * Creates a new {@code CallbackInfoReturnable} with an initial primitive value.
     *
     * <p>This constructor is provided for convenience to avoid boxing at the call site.
     * The weaver is expected to handle boxing/unboxing as needed.</p>
     *
     * @param methodName  the (simple) name of the intercepted method; never {@code null}
     * @param cancellable whether this callback supports cancellation
     * @param returnValue the initial return value
     * @throws NullPointerException     if {@code methodName} is {@code null} (e.g., plain javac, no IDEA instrumentation)
     * @throws IllegalArgumentException if {@code methodName} is {@code null} and JetBrains @NotNull
     *                                  runtime instrumentation is active (IDEA compiler/bytecode instrumentation)
     * @implNote Exception type depends on the toolchain: javac vs. IDEA-instrumented builds.
     * @apiNote This constructor marks {@link #hasReturnValue()} as {@code true}.
     */
    @SuppressWarnings("unchecked")
    public CallbackInfoReturnable(
            @NotNull final String methodName,
            final boolean cancellable,
            final boolean returnValue
    ) {
        super(Objects.requireNonNull(methodName, "methodName"), cancellable);
        // This cast is safe because T is expected to be the boxed type when used with primitives.
        this.returnValue = (T) Boolean.valueOf(returnValue);
        this.hasValueFlag = true;
    }

    /**
     * Creates a new {@code CallbackInfoReturnable} with an initial primitive value.
     *
     * <p>This constructor is provided for convenience to avoid boxing at the call site.
     * The weaver is expected to handle boxing/unboxing as needed.</p>
     *
     * @param methodName  the (simple) name of the intercepted method; never {@code null}
     * @param cancellable whether this callback supports cancellation
     * @param returnValue the initial return value
     * @throws NullPointerException     if {@code methodName} is {@code null} (e.g., plain javac, no IDEA instrumentation)
     * @throws IllegalArgumentException if {@code methodName} is {@code null} and JetBrains @NotNull
     *                                  runtime instrumentation is active (IDEA compiler/bytecode instrumentation)
     * @implNote Exception type depends on the toolchain: javac vs. IDEA-instrumented builds.
     * @apiNote This constructor marks {@link #hasReturnValue()} as {@code true}.
     */
    @SuppressWarnings("unchecked")
    public CallbackInfoReturnable(
            @NotNull final String methodName,
            final boolean cancellable,
            final char returnValue
    ) {
        super(Objects.requireNonNull(methodName, "methodName"), cancellable);
        // This cast is safe because T is expected to be the boxed type when used with primitives.
        this.returnValue = (T) Character.valueOf(returnValue);
        this.hasValueFlag = true;
    }

    /**
     * Creates a new {@code CallbackInfoReturnable} with an initial primitive value.
     *
     * <p>This constructor is provided for convenience to avoid boxing at the call site.
     * The weaver is expected to handle boxing/unboxing as needed.</p>
     *
     * @param methodName  the (simple) name of the intercepted method; never {@code null}
     * @param cancellable whether this callback supports cancellation
     * @param returnValue the initial return value
     * @throws NullPointerException     if {@code methodName} is {@code null} (e.g., plain javac, no IDEA instrumentation)
     * @throws IllegalArgumentException if {@code methodName} is {@code null} and JetBrains @NotNull
     *                                  runtime instrumentation is active (IDEA compiler/bytecode instrumentation)
     * @implNote Exception type depends on the toolchain: javac vs. IDEA-instrumented builds.
     * @apiNote This constructor marks {@link #hasReturnValue()} as {@code true}.
     */
    @SuppressWarnings("unchecked")
    public CallbackInfoReturnable(
            @NotNull final String methodName,
            final boolean cancellable,
            final byte returnValue
    ) {
        super(Objects.requireNonNull(methodName, "methodName"), cancellable);
        // This cast is safe because T is expected to be the boxed type when used with primitives.
        this.returnValue = (T) Byte.valueOf(returnValue);
        this.hasValueFlag = true;
    }

    /**
     * Creates a new {@code CallbackInfoReturnable} with an initial primitive value.
     *
     * <p>This constructor is provided for convenience to avoid boxing at the call site.
     * The weaver is expected to handle boxing/unboxing as needed.</p>
     *
     * @param methodName  the (simple) name of the intercepted method; never {@code null}
     * @param cancellable whether this callback supports cancellation
     * @param returnValue the initial return value
     * @throws NullPointerException     if {@code methodName} is {@code null} (e.g., plain javac, no IDEA instrumentation)
     * @throws IllegalArgumentException if {@code methodName} is {@code null} and JetBrains @NotNull
     *                                  runtime instrumentation is active (IDEA compiler/bytecode instrumentation)
     * @implNote Exception type depends on the toolchain: javac vs. IDEA-instrumented builds.
     * @apiNote This constructor marks {@link #hasReturnValue()} as {@code true}.
     */
    @SuppressWarnings("unchecked")
    public CallbackInfoReturnable(
            @NotNull final String methodName,
            final boolean cancellable,
            final short returnValue
    ) {
        super(Objects.requireNonNull(methodName, "methodName"), cancellable);
        // This cast is safe because T is expected to be the boxed type when used with primitives.
        this.returnValue = (T) Short.valueOf(returnValue);
        this.hasValueFlag = true;
    }

    /**
     * Returns whether a return value has been explicitly set via {@link #setReturnValue(Object)} or via a constructor.
     *
     * <p>This is distinct from {@code getReturnValue() != null} to allow distinguishing between
     * "no value set" and "value set to null" for reference types.</p>
     * <p>To avoid confusion: this flag only indicates whether a value has been set. It does not imply
     * that the callback is cancelled. The weaver must check {@link #isCancelled()} separately to determine
     * whether to use the return value as the effective return from the target method.</p>
     * <p>Note that if you want to unset a previously set return value, you can call
     * {@link #clearReturnValue()} to reset both the value and this flag.</p>
     *
     * @return {@code true} if a return value has been set; {@code false} otherwise
     * @see #clearReturnValue()
     * @see #setReturnValue(Object)
     */
    public boolean hasReturnValue() {
        return this.hasValueFlag;
    }

    /**
     * Returns the current return value.
     *
     * <p>If the callback is cancelled, the weaver will use this value as the effective
     * return from the target method. If not cancelled, the value is advisory and
     * may be ignored depending on the hook type.</p>
     *
     * @return the current return value (may be {@code null} for reference types)
     */
    @Nullable
    public T getReturnValue() {
        return this.returnValue;
    }

    /**
     * The following methods return the current return value cast to the respective primitive type.
     *
     * <p>If the value is not of the expected type, a default value is returned instead
     * (e.g., {@code 0} for numeric types, {@code false} for {@code boolean}, and {@code '\u0000'} for {@code char}).
     * This avoids {@link ClassCastException} at the cost of potentially hiding type errors.
     * The weaver is expected to handle boxing/unboxing as needed.</p>
     *
     * @return the current return value as the respective primitive type, or a default if not applicable
     */
    public int getReturnValueAsInt() {
        if (this.returnValue instanceof Integer intValue) {
            return intValue;
        }

        return 0;
    }

    /**
     * The following methods return the current return value cast to the respective primitive type.
     *
     * <p>If the value is not of the expected type, a default value is returned instead
     * (e.g., {@code 0} for numeric types, {@code false} for {@code boolean}, and {@code '\u0000'} for {@code char}).
     * This avoids {@link ClassCastException} at the cost of potentially hiding type errors.
     * The weaver is expected to handle boxing/unboxing as needed.</p>
     *
     * @return the current return value as the respective primitive type, or a default if not applicable
     */
    public long getReturnValueAsLong() {
        if (this.returnValue instanceof Long longValue) {
            return longValue;
        }

        return 0L;
    }

    /**
     * The following methods return the current return value cast to the respective primitive type.
     *
     * <p>If the value is not of the expected type, a default value is returned instead
     * (e.g., {@code 0} for numeric types, {@code false} for {@code boolean}, and {@code '\u0000'} for {@code char}).
     * This avoids {@link ClassCastException} at the cost of potentially hiding type errors.
     * The weaver is expected to handle boxing/unboxing as needed.</p>
     *
     * @return the current return value as the respective primitive type, or a default if not applicable
     */
    public float getReturnValueAsFloat() {
        if (this.returnValue instanceof Float floatValue) {
            return floatValue;
        }

        return 0f;
    }

    /**
     * The following methods return the current return value cast to the respective primitive type.
     *
     * <p>If the value is not of the expected type, a default value is returned instead
     * (e.g., {@code 0} for numeric types, {@code false} for {@code boolean}, and {@code '\u0000'} for {@code char}).
     * This avoids {@link ClassCastException} at the cost of potentially hiding type errors.
     * The weaver is expected to handle boxing/unboxing as needed.</p>
     *
     * @return the current return value as the respective primitive type, or a default if not applicable
     */
    public double getReturnValueAsDouble() {
        if (this.returnValue instanceof Double doubleValue) {
            return doubleValue;
        }

        return 0d;
    }

    /**
     * The following methods return the current return value cast to the respective primitive type.
     *
     * <p>If the value is not of the expected type, a default value is returned instead
     * (e.g., {@code 0} for numeric types, {@code false} for {@code boolean}, and {@code '\u0000'} for {@code char}).
     * This avoids {@link ClassCastException} at the cost of potentially hiding type errors.
     * The weaver is expected to handle boxing/unboxing as needed.</p>
     *
     * @return the current return value as the respective primitive type, or a default if not applicable
     */
    public boolean getReturnValueAsBoolean() {
        if (this.returnValue instanceof Boolean booleanValue) {
            return booleanValue;
        }

        return false;
    }

    /**
     * The following methods return the current return value cast to the respective primitive type.
     *
     * <p>If the value is not of the expected type, a default value is returned instead
     * (e.g., {@code 0} for numeric types, {@code false} for {@code boolean}, and {@code '\u0000'} for {@code char}).
     * This avoids {@link ClassCastException} at the cost of potentially hiding type errors.
     * The weaver is expected to handle boxing/unboxing as needed.</p>
     *
     * @return the current return value as the respective primitive type, or a default if not applicable
     */
    public char getReturnValueAsChar() {
        if (this.returnValue instanceof Character charValue) {
            return charValue;
        }

        return '\u0000';
    }

    /**
     * The following methods return the current return value cast to the respective primitive type.
     *
     * <p>If the value is not of the expected type, a default value is returned instead
     * (e.g., {@code 0} for numeric types, {@code false} for {@code boolean}, and {@code '\u0000'} for {@code char}).
     * This avoids {@link ClassCastException} at the cost of potentially hiding type errors.
     * The weaver is expected to handle boxing/unboxing as needed.</p>
     *
     * @return the current return value as the respective primitive type, or a default if not applicable
     */
    public byte getReturnValueAsByte() {
        if (this.returnValue instanceof Byte byteValue) {
            return byteValue;
        }

        return 0;
    }

    /**
     * The following methods return the current return value cast to the respective primitive type.
     *
     * <p>If the value is not of the expected type, a default value is returned instead
     * (e.g., {@code 0} for numeric types, {@code false} for {@code boolean}, and {@code '\u0000'} for {@code char}).
     * This avoids {@link ClassCastException} at the cost of potentially hiding type errors.
     * The weaver is expected to handle boxing/unboxing as needed.</p>
     *
     * @return the current return value as the respective primitive type, or a default if not applicable
     */
    public short getReturnValueAsShort() {
        if (this.returnValue instanceof Short shortValue) {
            return shortValue;
        }

        return 0;
    }

    /**
     * Sets a new return value.
     *
     * <p>This updates the value returned by {@link #getReturnValue()} and marks
     * {@link #hasReturnValue()} as {@code true}. If the callback is cancelled,
     * the weaver will use this value as the effective return from the target method.</p>
     *
     * @param value the new return value (may be {@code null} for reference types)
     */
    public void setReturnValue(@Nullable final T value) {
        this.returnValue = value;
        this.hasValueFlag = true;
    }

    /**
     * Clears any previously set return value.
     *
     * <p>This sets the value returned by {@link #getReturnValue()} to {@code null}
     * and marks {@link #hasReturnValue()} as {@code false}.</p>
     */
    public void clearReturnValue() {
        this.returnValue = null;
        this.hasValueFlag = false;
    }

    /**
     * Returns the current return value if set; otherwise returns the provided default.
     *
     * <p>This is a convenience method to avoid checking {@link #hasReturnValue()} manually.</p>
     *
     * @param defaultValue the value to return if no return value has been set (may be {@code null} for reference types)
     * @return the current return value if set; otherwise {@code defaultValue}
     */
    @Nullable
    public T getOrDefault(final T defaultValue) {
        return hasReturnValue() ? this.returnValue : defaultValue;
    }

    /**
     * Convenience method that sets the return value and then attempts to cancel.
     *
     * <p>Equivalent to calling {@link #setReturnValue(Object)} followed by {@link #cancel()}.
     * If this callback is not cancellable, a {@link CancellationException} is thrown and
     * the return value remains updated.</p>
     *
     * @param value the new return value (may be {@code null} for reference types)
     * @throws CancellationException if this callback is not cancellable
     */
    public void setReturnValueAndCancel(@Nullable final T value) throws CancellationException {
        this.returnValue = value;
        this.cancel();
    }

    /**
     * Returns a concise debug representation including the current return value.
     *
     * <p>The exact format is not part of the public API and may change between versions.</p>
     *
     * @return a string representation for debugging purposes
     */
    @Override
    public String toString() {
        return "CallbackInfoReturnable{" +
                "cancellable=" + isCancellable() +
                ", cancelled=" + isCancelled() +
                ", returnValue=" + this.returnValue +
                '}';
    }
}
