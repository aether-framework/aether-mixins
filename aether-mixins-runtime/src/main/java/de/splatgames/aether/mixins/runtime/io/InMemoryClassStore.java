package de.splatgames.aether.mixins.runtime.io;

import de.splatgames.aether.mixins.core.weaver.spi.ClassSink;
import de.splatgames.aether.mixins.core.weaver.spi.ClassSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of both {@link ClassSink} and {@link ClassSource}.
 *
 * <h2>Purpose</h2>
 * <p>
 * Captures woven classes in memory and serves them back as a {@link ClassSource}. This is useful for:
 * </p>
 * <ul>
 *   <li><b>Unit/Integration tests:</b> Verify weaving output without filesystem I/O.</li>
 *   <li><b>Pipeline chaining:</b> Feed the output of one weaving phase directly into another.</li>
 *   <li><b>Diagnostics:</b> Inspect or debug generated classes at runtime.</li>
 * </ul>
 *
 * <h2>Naming</h2>
 * <p><b>Internal JVM names only.</b> Keys must use slash separators:</p>
 * <pre>{@code
 * com/example/MyClass   // internal JVM name (required)
 * com.example.MyClass   // binary name (not accepted)
 * }</pre>
 *
 * <h2>Storage semantics</h2>
 * <ul>
 *   <li>On write ({@link #accept(String, byte[])}), the byte array is <b>cloned</b> before storage to prevent
 *       external code from mutating internal state after the call returns.</li>
 *   <li>On read via {@link #getClassBytes(String)}, a fresh <b>clone</b> is returned to the caller.</li>
 *   <li>{@link #entriesView()} exposes an <em>unmodifiable view</em> of the backing map (live view of keys/values);
 *       note that the byte array values in the returned map are the internally stored arrays (no deep copy).</li>
 * </ul>
 *
 * <h2>Thread-safety</h2>
 * <p>
 * Backed by a {@link ConcurrentHashMap}, allowing concurrent reads/writes. Operations are atomic per key; if you need
 * a consistent snapshot across multiple keys, establish external synchronization around multi-step workflows.
 * </p>
 *
 * <h2>Performance</h2>
 * <ul>
 *   <li>Each write incurs an O(n) clone of the provided class bytes.</li>
 *   <li>{@link #getClassBytes(String)} clones the stored array (O(n)).</li>
 *   <li>{@link #entriesView()} is O(1) for the wrapper; no deep copy is performed.</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * InMemoryClassStore store = new InMemoryClassStore();
 *
 * // Store class bytes
 * store.accept("com/example/MyClass", classBytes);
 *
 * // Read back
 * byte[] loaded = store.getClassBytes("com/example/MyClass"); // clone returned
 *
 * // Inspect all current entries (unmodifiable view)
 * for (Map.Entry<String, byte[]> e : store.entriesView().entrySet()) {
 *     System.out.println(e.getKey() + " -> " + e.getValue().length + " bytes");
 * }
 *
 * // Clear everything
 * store.clear();
 * }</pre>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class InMemoryClassStore implements ClassSink, ClassSource {

    /**
     * Internal backing store. Keys are internal JVM class names (e.g., {@code com/example/MyClass});
     * values are stored class byte arrays.
     */
    @NotNull
    private final ConcurrentHashMap<String, byte[]> store = new ConcurrentHashMap<>();

    /**
     * Stores the provided class bytes in memory, keyed by the internal JVM name.
     *
     * <p>The provided {@code bytes} are <b>cloned</b> before storage to protect the internal state
     * from subsequent external mutations.</p>
     *
     * <p>If an entry already exists for {@code internalName}, it is overwritten.</p>
     *
     * @param internalName the class name in internal JVM format (slash-separated); must not be {@code null}
     * @param bytes        the class byte array; must not be {@code null}
     * @throws NullPointerException if {@code internalName} or {@code bytes} is {@code null}
     */
    @Override
    public void accept(@NotNull final String internalName, @NotNull final byte[] bytes) {
        Objects.requireNonNull(internalName, "internalName");
        Objects.requireNonNull(bytes, "bytes");
        this.store.put(internalName, bytes.clone());
    }

    /**
     * Retrieves the stored bytecode for the given internal JVM class name.
     *
     * <p>Returns a <b>clone</b> of the stored array to prevent external mutation of the internal store.</p>
     *
     * @param internalName the class name in internal JVM format (slash-separated); must not be {@code null}
     * @return a cloned copy of the stored class bytes, or {@code null} if no entry exists
     * @throws IOException          never thrown by this implementation (declared for interface compatibility)
     * @throws NullPointerException if {@code internalName} is {@code null}
     */
    @Nullable
    @Override
    public byte[] getClassBytes(@NotNull final String internalName) throws IOException {
        Objects.requireNonNull(internalName, "internalName");
        final byte[] data = this.store.get(internalName);
        return (data != null) ? data.clone() : null;
    }

    /**
     * Returns an <em>unmodifiable</em> live view of the current store.
     *
     * <p>The returned map reflects the current state and may change as the store is mutated.
     * The byte arrays in the returned map are the internally stored arrays (no deep copy).</p>
     *
     * @return an unmodifiable view of the internal map (internal name → stored bytes)
     */
    @NotNull
    public Map<String, byte[]> entriesView() {
        return Collections.unmodifiableMap(this.store);
    }

    /**
     * Removes all stored classes from memory.
     */
    public void clear() {
        this.store.clear();
    }
}
