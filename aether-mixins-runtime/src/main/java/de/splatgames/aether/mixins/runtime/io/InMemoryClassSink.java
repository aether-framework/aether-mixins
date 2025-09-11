package de.splatgames.aether.mixins.runtime.io;

import de.splatgames.aether.mixins.core.weaver.spi.ClassSink;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link ClassSink} that stores transformed class bytes entirely in memory.
 *
 * <h2>Purpose</h2>
 * <p>
 * Captures woven class outputs without touching the filesystem. Useful for unit/integration tests,
 * multi-phase weaving pipelines (where one pass feeds the next), and ad-hoc diagnostics during
 * development.
 * </p>
 *
 * <h2>Naming</h2>
 * <p><b>Internal JVM names only.</b> Keys use slash-separated class names:</p>
 * <pre>{@code
 * com/example/MyClass   // internal JVM name (required)
 * com.example.MyClass   // binary name (not accepted)
 * }</pre>
 *
 * <h2>Behavior</h2>
 * <ul>
 *   <li>On {@link #accept(String, byte[])}, the byte array is <b>cloned</b> before storage to prevent
 *       callers from mutating internal state inadvertently.</li>
 *   <li>Writes are idempotent per key: a subsequent write for the same internal name overwrites the previous bytes.</li>
 *   <li>{@link #snapshot()} returns an <em>immutable copy</em> of the current map (keys to stored arrays).</li>
 *   <li>{@link #get(String)} returns a direct reference to the stored array (no clone); callers must avoid mutating it
 *       unless they intentionally want to alter the in-memory store.</li>
 * </ul>
 *
 * <h2>Thread-safety</h2>
 * <p>
 * Backed by a {@link ConcurrentHashMap}, allowing safe concurrent writes and reads. Map operations are atomic
 * per-key; there is no compound-operation transactional guarantee across multiple keys.
 * </p>
 *
 * <h2>Performance</h2>
 * <ul>
 *   <li>Write path incurs a full array clone (O(n) per class) to preserve store integrity.</li>
 *   <li>{@link #snapshot()} copies the current map structure (O(m) entries) but does not deep-copy values;
 *       it references the same byte arrays stored internally.</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * ClassSink sink = new InMemoryClassSink();
 *
 * sink.accept("com/example/MyClass", classBytes);
 *
 * byte[] direct = ((InMemoryClassSink) sink).get("com/example/MyClass");   // direct reference
 * Map<String, byte[]> snap = ((InMemoryClassSink) sink).snapshot();        // immutable view
 * }</pre>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class InMemoryClassSink implements ClassSink {

    /**
     * Internal store keyed by internal JVM class name (e.g., {@code com/example/MyClass}).
     */
    @NotNull
    private final Map<String, byte[]> store = new ConcurrentHashMap<>();

    /**
     * Stores the provided class bytes in memory under the given internal name.
     *
     * <p>The provided {@code bytes} are <b>cloned</b> prior to storage to prevent callers
     * from mutating the sink's internal state after this method returns.</p>
     *
     * <p>If an entry with the same {@code internalName} already exists, it is overwritten.</p>
     *
     * @param internalName the class name in internal JVM format (slash-separated); must not be {@code null}
     * @param bytes        the class byte array; must not be {@code null}
     * @throws IOException          never thrown by this implementation (declared by interface)
     * @throws NullPointerException if {@code internalName} or {@code bytes} is {@code null}
     */
    @Override
    public void accept(@NotNull final String internalName, @NotNull final byte[] bytes) throws IOException {
        Objects.requireNonNull(internalName, "internalName");
        Objects.requireNonNull(bytes, "bytes");
        this.store.put(internalName, bytes.clone());
    }

    /**
     * Returns an immutable snapshot of the current store (mapping internal names to stored arrays).
     *
     * <p>The returned map is a structural copy and will not reflect subsequent writes to this sink.
     * The byte arrays in the returned map are the same references as stored internally
     * (no deep copy is performed).</p>
     *
     * @return an immutable map view of the current store; never {@code null}
     */
    @NotNull
    public Map<String, byte[]> snapshot() {
        return Map.copyOf(this.store);
    }

    /**
     * Retrieves the stored class bytes for the given internal name.
     *
     * <p><b>Important:</b> This method returns the <em>actual stored array</em> (no clone).
     * Modifying the returned array will directly mutate the sink's internal state.</p>
     *
     * <p>If you need an isolated buffer, clone the result (e.g., {@code get(name).clone()})
     * or obtain a snapshot and clone from there.</p>
     *
     * @param internalName the class name in internal JVM format (slash-separated); must not be {@code null}
     * @return the stored byte array, or {@code null} if no entry exists for {@code internalName}
     * @throws NullPointerException if {@code internalName} is {@code null}
     */
    @Nullable
    public byte[] get(@NotNull final String internalName) {
        Objects.requireNonNull(internalName, "internalName");
        return this.store.get(internalName);
    }
}
