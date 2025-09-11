package de.splatgames.aether.mixins.core.weaver.spi;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Source of class bytecode used by the weaver to resolve classes by internal name.
 *
 * <p>Implementations abstract where classfiles are obtained from: a {@code ClassLoader},
 * the filesystem, an in-memory store, or a composite of several sources.</p>
 *
 * <h2>Naming</h2>
 * <p>The {@code internalName} uses the JVM's internal form (slash-separated),
 * for example {@code com/example/Foo}. Implementations may normalize dotted names,
 * but callers SHOULD provide the internal form for deterministic behavior.</p>
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>Return {@code null} if the class cannot be found.</li>
 *   <li>The returned array SHOULD contain a complete, valid classfile (per JVMS). Implementations
 *       MAY defer validation to downstream consumers.</li>
 *   <li>No caching is required. If caching is employed, implementations SHOULD document its behavior
 *       (e.g., visibility of updates during a weaving session).</li>
 *   <li><strong>Thread-safety:</strong> Unless documented otherwise, callers MUST assume the source is
 *       not thread-safe and serialize lookups per session.</li>
 * </ul>
 *
 * <h2>Error handling</h2>
 * <p>I/O failures MUST be signaled via {@link IOException}. Contract violations (e.g., illegal name format)
 * MAY be reported via {@link IllegalArgumentException}, though this interface does not mandate it.</p>
 *
 * <h2>Examples</h2>
 * <ul>
 *   <li>ClassLoader-backed source: uses {@code getResourceAsStream(internalName + ".class")}.</li>
 *   <li>Filesystem source: resolves {@code classesDir/internalName + ".class"}.</li>
 *   <li>Composite source: tries multiple delegates in order.</li>
 * </ul>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@FunctionalInterface
public interface ClassSource {

    /**
     * Returns the bytecode for the given class or {@code null} if it is not available.
     *
     * @param internalName internal JVM name (slash-separated), must not be {@code null}
     * @return classfile bytes, or {@code null} if the class cannot be found
     * @throws IOException if an I/O error occurs while reading the class
     */
    byte[] getClassBytes(@NotNull final String internalName) throws IOException;
}
