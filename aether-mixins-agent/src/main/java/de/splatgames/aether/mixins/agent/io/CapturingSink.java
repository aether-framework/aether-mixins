package de.splatgames.aether.mixins.agent.io;

import de.splatgames.aether.mixins.core.weaver.spi.ClassSink;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;

/**
 * {@link ClassSink} implementation that captures the transformed bytes for exactly
 * one target class and exposes them to the caller.
 *
 * <h2>Purpose</h2>
 * <p>
 * In an agent context, a new {@code CapturingSink} is created per class being transformed.
 * While a weaver could, in theory, emit multiple classes, this sink is only interested
 * in the <em>current</em> class (identified by the internal name provided to the constructor).
 * Any bytes for other classes are intentionally ignored.
 * </p>
 *
 * <h2>Naming</h2>
 * <p>
 * Class names are expected to be <strong>internal JVM names</strong> (slash-separated),
 * e.g. {@code com/example/MyClass}.
 * </p>
 *
 * <h2>Thread safety</h2>
 * <p>
 * Not thread-safe. Each instance is intended for a single transform invocation,
 * which is typically invoked per class by the instrumentation API.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // className comes from ClassFileTransformer#transform(...)
 * ClassSink sink = new CapturingSink(className);
 * WeaveRequest req = WeaveRequest.of(plan, source, sink, runtime);
 * WeaveResult res = weaver.weave(req, problems);
 *
 * byte[] out = ((CapturingSink) sink).getCaptured(); // null => no change
 * return out;
 * }</pre>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class CapturingSink implements ClassSink {

    /**
     * Internal JVM name of the class whose output bytes should be captured.
     */
    @NotNull
    private final String className;

    /**
     * Captured bytes of the target class, or {@code null} if the weaver produced no output
     * (e.g., no transformation applied).
     */
    @Nullable
    private byte[] captured;

    /**
     * Creates a {@code CapturingSink} for the given target class.
     *
     * @param className internal JVM class name (e.g., {@code com/example/MyClass}); must not be {@code null}
     * @throws NullPointerException if {@code className} is {@code null}
     */
    public CapturingSink(@NotNull final String className) {
        this.className = Objects.requireNonNull(className, "className");
    }

    /**
     * Accepts (potentially transformed) class bytes.
     *
     * <p>
     * If {@code internalName} matches the class name passed to the constructor, the bytes are stored.
     * If called multiple times for the same target class, the <em>last</em> call wins (last-write-wins).
     * Bytes for any other class names are ignored by design.
     * </p>
     *
     * @param internalName internal JVM class name of the emitted class, must not be {@code null}
     * @param bytes        class bytes, must not be {@code null}
     * @throws IOException never thrown by this implementation; present to satisfy the interface
     * @throws NullPointerException if {@code internalName} or {@code bytes} is {@code null}
     */
    @Override
    public void accept(@NotNull final String internalName, final byte[] bytes) throws IOException {
        Objects.requireNonNull(internalName, "internalName");
        Objects.requireNonNull(bytes, "bytes");
        if (this.className.equals(internalName)) {
            this.captured = bytes;
        }
        // Intentionally ignore emissions for other classes.
    }

    /**
     * Returns the captured bytes for the target class, if any.
     *
     * @return the captured class bytes, or {@code null} if no output was captured
     */
    @Nullable
    public byte[] getCaptured() {
        return this.captured;
    }
}
