package de.splatgames.aether.mixins.core.api.cancel;

import de.splatgames.aether.mixins.core.api.cancel.exception.CancellationException;

/**
 * Represents a standardized cancellation mechanism for callbacks and other
 * weaver-related operations.
 *
 * <p>This interface defines the core contract for objects that can be
 * cancelled during runtime. It is typically implemented by
 * {@link de.splatgames.aether.mixins.core.api.CallbackInfo CallbackInfo}
 * and similar classes within the weaving process.</p>
 *
 * <h2>Usage Example</h2>
 * <blockquote><pre>
 * {@code
 * public void onEvent(final Cancellable callback) {
 *     if (shouldCancel()) {
 *         callback.cancel(); // signals cancellation
 *     }
 *
 *     if (callback.isCancelled()) {
 *         System.out.println("Execution stopped due to cancellation");
 *     }
 * }
 * }
 * </pre></blockquote>
 *
 * <h2>Thread-safety</h2>
 * <p>Implementations of this interface are generally not thread-safe unless
 * explicitly documented otherwise. The cancellation state is typically
 * manipulated within the same thread that invoked the callback.</p>
 *
 * @author Erik Pförtner
 * @implNote Implementations should clearly define whether the cancellation
 * flag can be reset or is permanent once set to {@code true}.
 * @since 0.2.0
 */
public interface Cancellable {

    /**
     * Returns whether this instance has been cancelled.
     *
     * <p>A cancelled instance signals to the weaver or caller that further
     * processing should be skipped. The exact effect of cancellation depends
     * on the context in which it is used.</p>
     *
     * @return {@code true} if this instance has been cancelled; {@code false} otherwise
     */
    boolean isCancelled();

    /**
     * Returns whether this instance supports cancellation.
     *
     * <p>If this method returns {@code false}, invoking {@link #cancel()} will
     * throw a {@link CancellationException}. This allows for callbacks that
     * are purely informational and cannot stop the original execution.</p>
     *
     * @return {@code true} if this instance can be cancelled; {@code false} otherwise
     */
    boolean isCancellable();

    /**
     * Attempts to cancel this instance.
     *
     * <p>When successful, the cancellation state will be set to {@code true}
     * and the weaver or caller will stop processing the original target method
     * or operation.</p>
     *
     * <p>If this instance is not {@linkplain #isCancellable() cancellable},
     * a {@link CancellationException} will be thrown instead.</p>
     *
     * <blockquote><pre>
     * {@code
     * if (callback.isCancellable()) {
     *     callback.cancel();
     * }
     * }
     * </pre></blockquote>
     *
     * @throws CancellationException if cancellation is not supported
     */
    void cancel() throws CancellationException;
}
