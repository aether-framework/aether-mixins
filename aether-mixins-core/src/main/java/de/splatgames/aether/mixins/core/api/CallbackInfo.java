package de.splatgames.aether.mixins.core.api;

import de.splatgames.aether.mixins.core.api.cancel.Cancellable;
import de.splatgames.aether.mixins.core.api.cancel.exception.CancellationException;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Represents invocation metadata for an injected callback and provides
 * a standardized cancellation mechanism.
 *
 * <p>The weaver passes an instance of this class into injected hook methods
 * (e.g., HEAD/TAIL/REDIRECT).
 * If the callback is {@linkplain #isCancellable() cancellable},
 * the injected code may call {@link #cancel()} to prevent the original target method
 * from continuing its execution.</p>
 *
 * <h2>Usage</h2>
 * <blockquote><pre>
 * {@code
 * @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
 * private static void onTick(final CallbackInfo ci) {
 *     // Guard: do not proceed if some condition is met
 *     if (System.getSecurityManager() == null) {
 *         ci.cancel(); // stop the original method early
 *     }
 * }}
 * </pre></blockquote>
 *
 * <h2>Thread-safety</h2>
 * <p>This type is <em>not</em> thread-safe. The {@linkplain #cancelled cancellation state}
 * is mutable and intended to be used within the single-threaded execution of the target
 * method being woven.</p>
 *
 * @author Erik Pförtner
 * @implNote The {@code cancellable} property is immutable by design to guarantee
 * consistent behavior throughout the lifetime of a callback instance.
 * @see Cancellable
 * @see CancellationException
 * @since 0.2.0
 */
public sealed class CallbackInfo implements Cancellable permits CallbackInfoReturnable {

    /**
     * Human-readable identifier of the intercepted method (usually its simple name).
     * <p>Used for diagnostics and error messages. Never {@code null}.</p>
     */
    private final String methodName;

    /**
     * Whether this callback supports cancellation. Immutable for the lifetime of this instance.
     */
    private final boolean cancellable;

    /**
     * Current cancellation state. {@code true} once {@link #cancel()} has been successfully invoked.
     */
    private boolean cancelled;

    /**
     * Creates a new {@code CallbackInfo}.
     *
     * @param methodName  the (simple) name of the method being intercepted; used for diagnostics
     * @param cancellable whether this callback can be cancelled via {@link #cancel()}
     * @throws NullPointerException if {@code methodName} is {@code null}
     */
    public CallbackInfo(@NotNull final String methodName, final boolean cancellable) {
        this.methodName = Objects.requireNonNull(methodName, "methodName");
        this.cancellable = cancellable;
    }

    /**
     * Returns whether this callback has been cancelled.
     *
     * <p>Once set to {@code true} (by invoking {@link #cancel()} on a cancellable callback),
     * the weaver will prevent further execution of the original target method.</p>
     *
     * @return {@code true} if the callback has been cancelled; {@code false} otherwise
     */
    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    /**
     * Returns whether this callback supports cancellation.
     *
     * <p>If this method returns {@code false}, calling {@link #cancel()} will throw
     * a {@link CancellationException}.</p>
     *
     * @return {@code true} if cancellation is supported; {@code false} otherwise
     */
    @Override
    public boolean isCancellable() {
        return this.cancellable;
    }

    /**
     * Returns the (simple) name of the method being intercepted.
     *
     * <p>This is used for diagnostics and error messages.</p>
     *
     * @return the method name; never {@code null}
     */
    @NotNull
    public String getMethodName() {
        return this.methodName;
    }

    /**
     * Marks this callback as cancelled, if supported.
     *
     * <p>Cancellation is a controlled signal to the weaver to stop executing
     * the original target method. This method is idempotent when cancellation
     * is supported (calling it multiple times keeps the state {@code true}).</p>
     *
     * @throws CancellationException if this callback is not {@linkplain #isCancellable() cancellable}
     */
    @Override
    public void cancel() throws CancellationException {
        if (!this.cancellable) {
            throw new CancellationException(
                    "Callback for method '" + this.methodName + "' is not cancellable"
            );
        }
        this.cancelled = true;
    }

    /**
     * Returns a concise debug representation containing method identifier and state flags.
     *
     * <p>The format is not part of the public API and may change between versions.</p>
     *
     * @return a string representation for debugging purposes
     */
    @Override
    public String toString() {
        return "CallbackInfo{" +
                "methodName='" + this.methodName + '\'' +
                ", cancellable=" + this.cancellable +
                ", cancelled=" + this.cancelled +
                '}';
    }
}
