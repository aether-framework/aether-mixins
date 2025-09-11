package de.splatgames.aether.mixins.core.weaver.spi;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Sink for publishing transformed (or newly generated) class definitions during a weaving session.
 *
 * <p>Implementations decide where and how class bytes are persisted or forwarded — e.g.,
 * to an in-memory map, the filesystem, a {@code java.lang.instrument.Instrumentation}-based
 * redefinition pipeline, or a custom class output store.</p>
 *
 * <h2>Naming</h2>
 * <p>The {@code internalName} uses the JVM's internal form (slash-separated),
 * for example {@code com/example/Foo}. Implementations may validate that this name
 * matches the {@code this_class} of the provided classfile and are encouraged to
 * reject mismatches.</p>
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>Calls are <em>idempotent</em> with respect to the target storage only by
 *       implementation choice. Unless documented otherwise, later calls for the same
 *       {@code internalName} SHOULD overwrite earlier ones (last-write-wins).</li>
 *   <li>The {@code bytes} array MUST contain a complete, valid classfile
 *       (per JVMS). Validation may be deferred or strict; invalid content SHOULD
 *       result in an exception.</li>
 *   <li>Weavers typically invoke this sink only for classes that were actually modified,
 *       but they MAY also use it for newly generated helper/synthetic classes.</li>
 *   <li><strong>Thread-safety:</strong> Implementations SHOULD document their
 *       thread-safety guarantees. Unless otherwise stated, callers MUST assume the sink
 *       is <em>not</em> thread-safe and serialize calls per weaving session.</li>
 * </ul>
 *
 * <h2>Error handling</h2>
 * <p>I/O related failures MUST be surfaced as {@link IOException}s.
 * Content/contract violations (e.g., illegal name, class/name mismatch) MAY be signaled
 * via {@link IllegalArgumentException}. Nullability violations are enforced by annotations
 * (and optional runtime instrumentation).</p>
 *
 * <h2>Examples</h2>
 * <ul>
 *   <li>Filesystem sink: writes {@code bytes} to {@code classesDir/internalName + ".class"}.</li>
 *   <li>In-memory sink: {@code Map&lt;String, byte[]&gt;} for later packaging or dynamic loading.</li>
 *   <li>Agent sink: forwards to {@code Instrumentation.redefineClasses(...)}.</li>
 * </ul>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@FunctionalInterface
public interface ClassSink {

    /**
     * Accepts a transformed (or newly generated) class definition.
     *
     * <p>Implementations MAY overwrite an existing artifact for {@code internalName}.
     * They SHOULD treat {@code internalName} as authoritative for the storage key and
     * SHOULD validate that it matches the classfile's declared name.</p>
     *
     * @param internalName internal JVM name (slash-separated), must not be {@code null}
     * @param bytes        complete classfile bytes, must not be {@code null}
     * @throws IOException              if an I/O error occurs while writing or forwarding the class
     * @throws IllegalArgumentException if {@code internalName} is malformed, {@code bytes} are invalid,
     *                                  or the name does not match the classfile contents
     */
    void accept(@NotNull final String internalName, final byte[] bytes) throws IOException;
}
