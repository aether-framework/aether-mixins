package de.splatgames.aether.mixins.core.api.cancel.exception;

import org.jetbrains.annotations.Nullable;

import java.io.Serial;

/**
 * Exception thrown to indicate that a cancellation attempt was made
 * on a non-cancellable object or callback.
 *
 * <p>This exception is typically raised when code tries to invoke
 * {@link de.splatgames.aether.mixins.core.api.cancel.Cancellable#cancel()}
 * on an object that is not {@linkplain de.splatgames.aether.mixins.core.api.cancel.Cancellable#isCancellable() cancellable}.</p>
 *
 * <h2>Typical Usage</h2>
 * <p>Thrown in scenarios where cancellation is requested but the target
 * cannot support this behavior. Example:</p>
 *
 * <blockquote><pre>
 * {@code
 * public void handleCallback(final Cancellable callback) {
 *     try {
 *         callback.cancel();
 *     } catch (CancellationException ex) {
 *         System.err.println("Cancellation failed: " + ex.getMessage());
 *     }
 * }
 * }
 * </pre></blockquote>
 *
 * <h2>Design Considerations</h2>
 * <ul>
 *   <li>The exception contains only a message, as cancellation errors are usually
 *       context-specific and do not require additional structured data.</li>
 * </ul>
 *
 * @author Erik Pförtner
 * @implNote This exception is used exclusively by the Aether Mixins API
 * to indicate a misuse of cancellation operations, typically at runtime
 * during callback execution.
 * @since 0.2.0
 */
public class CancellationException extends RuntimeException {

    /**
     * Serialization identifier for binary compatibility.
     */
    @Serial
    private static final long serialVersionUID = 4669150090179254631L;

    /**
     * Constructs a new {@code CancellationException} with the specified detail message.
     *
     * <p>The message may be {@code null} if no additional information is required.</p>
     *
     * @param message the detail message, or {@code null} if not provided
     */
    public CancellationException(@Nullable final String message) {
        super(message);
    }
}
