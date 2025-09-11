package de.splatgames.aether.mixins.agent.io;

import de.splatgames.aether.mixins.core.weaver.spi.ClassSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Objects;

/**
 * {@link ClassSource} implementation tailored for use inside a Java agent's
 * {@code ClassFileTransformer}.
 *
 * <h2>Purpose</h2>
 * <p>
 * During class transformation the JVM hands you the <em>current</em> class name and its
 * original byte array. This source:
 * </p>
 * <ul>
 *   <li>Returns the provided <em>current</em> class bytes verbatim when the queried
 *       {@code internalName} matches the class being transformed.</li>
 *   <li>For <em>any other</em> class name, attempts to load bytes via a {@link ClassLoader}
 *       by resolving {@code &lt;internalName&gt;.class} as a resource.</li>
 * </ul>
 *
 * <h2>Naming</h2>
 * <p>
 * Class names must be in <strong>internal JVM format</strong> (slash-separated), e.g.
 * {@code com/example/MyClass}.
 * </p>
 *
 * <h2>Loader resolution</h2>
 * <ul>
 *   <li>If a {@link ClassLoader} was provided to the constructor, it is used first.</li>
 *   <li>Otherwise this falls back to the system class loader (or {@code ClassLoader#getSystemResource}).</li>
 *   <li>If the resource cannot be found, {@code null} is returned.</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * <p>
 * Instances are immutable and safe to use from a single transform invocation. No caching is
 * performed; each non-current lookup opens the resource stream afresh.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // inside ClassFileTransformer#transform(...)
 * ClassSource source = new AgentSource(loader, className, classfileBuffer);
 * WeaveRequest req = WeaveRequest.of(plan, source, sink, runtime);
 * }</pre>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class AgentSource implements ClassSource {

    /**
     * Preferred class loader for lookups (may be {@code null}, in which case the system loader is used).
     */
    @Nullable
    private final ClassLoader loader;

    /**
     * Internal JVM name (slash-separated) of the class currently being transformed.
     */
    @NotNull
    private final String currentName;

    /**
     * Original bytes of the class currently being transformed.
     */
    @NotNull
    private final byte[] currentBytes;

    /**
     * Creates a new {@code AgentSource}.
     *
     * @param loader       class loader used for non-current lookups (may be {@code null} to use the system loader)
     * @param currentName  internal JVM name of the class currently being transformed, must not be {@code null}
     * @param currentBytes original bytes of the class currently being transformed, must not be {@code null}
     * @throws NullPointerException if {@code currentName} or {@code currentBytes} is {@code null}
     */
    public AgentSource(@Nullable final ClassLoader loader,
                       @NotNull final String currentName,
                       @NotNull final byte[] currentBytes) {
        this.loader = loader;
        this.currentName = Objects.requireNonNull(currentName, "currentName");
        this.currentBytes = Objects.requireNonNull(currentBytes, "currentBytes");
    }

    /**
     * Returns class bytes for the given internal name.
     *
     * <p>
     * If {@code internalName} equals the {@linkplain #currentName current class}, the
     * original {@linkplain #currentBytes provided bytes} are returned. Otherwise this method
     * attempts to load {@code internalName + ".class"} via the configured class loader (or
     * the system loader if none was provided). If the resource cannot be found, {@code null}
     * is returned.
     * </p>
     *
     * @param internalName the internal JVM name (slash-separated), must not be {@code null}
     * @return the class bytes, or {@code null} if the resource does not exist
     * @throws IOException          if an I/O error occurs while opening/reading the resource stream
     * @throws NullPointerException if {@code internalName} is {@code null}
     */
    @Override
    public byte[] getClassBytes(@NotNull final String internalName) throws IOException {
        if (internalName.equals(this.currentName)) {
            return this.currentBytes;
        }
        final String res = internalName + ".class";
        final ClassLoader cl = (this.loader != null) ? this.loader : ClassLoader.getSystemClassLoader();
        final URL u = (cl != null) ? cl.getResource(res) : ClassLoader.getSystemResource(res);
        try (InputStream in = (u != null) ? u.openStream() : null) {
            return (in != null) ? in.readAllBytes() : null;
        }
    }
}
