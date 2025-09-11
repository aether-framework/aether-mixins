package de.splatgames.aether.mixins.runtime.io;

import de.splatgames.aether.mixins.core.weaver.spi.ClassSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * {@link ClassSource} backed by a {@link ClassLoader}, reading class bytes directly
 * from the classpath.
 *
 * <h2>Purpose</h2>
 * <p>
 * This implementation resolves an internal JVM class name to a {@code .class} resource
 * on the configured {@link ClassLoader} and returns its bytes. It is intended for
 * runtime use by the weaver to fetch class definitions from the application or agent
 * classpath.
 * </p>
 *
 * <h2>Naming</h2>
 * <p><b>Internal JVM names only.</b> Input must use slash separators, not dots:
 * </p>
 * <pre>{@code
 * com/example/MyClass          // internal JVM name
 * com.example.MyClass          // binary name (not accepted)
 * }</pre>
 * <p>The resource path is computed as {@code <internalName> + ".class"}.</p>
 *
 * <h2>Class loader resolution</h2>
 * <ul>
 *   <li>If constructed with an explicit {@link ClassLoader}, that loader is always used.</li>
 *   <li>If constructed with the no-arg constructor, the <em>Thread Context ClassLoader</em> (TCCL)
 *       is used when non-null; otherwise this class's defining loader is used.</li>
 * </ul>
 *
 * <h2>Thread-safety</h2>
 * <p>
 * The class is stateless and the {@link ClassLoader} reference is {@code final}; instances are
 * safe to share across threads (subject to the thread-safety of the underlying class loader).
 * </p>
 *
 * <h2>Errors &amp; diagnostics</h2>
 * <ul>
 *   <li>When the resource is not found, {@code null} is returned.</li>
 *   <li>Any {@link IOException} from reading the stream is propagated as-is.</li>
 *   <li>Other unexpected failures are wrapped in an {@link IOException} with a descriptive message.</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // Explicit loader
 * ClassSource src = new ClasspathClassSource(MyApp.class.getClassLoader());
 * byte[] bytes = src.getClassBytes("com/example/MyClass");
 *
 * // Default to TCCL (or fallback to defining loader)
 * ClassSource src2 = new ClasspathClassSource();
 * }</pre>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class ClasspathClassSource implements ClassSource {

    /**
     * Class loader used to resolve {@code .class} resources; never {@code null}.
     */
    @NotNull
    private final ClassLoader loader;

    /**
     * Creates a {@code ClasspathClassSource} backed by the given class loader.
     *
     * @param loader the class loader used for resource lookups; must not be {@code null}
     * @throws NullPointerException if {@code loader} is {@code null}
     */
    public ClasspathClassSource(@NotNull final ClassLoader loader) {
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    /**
     * Creates a {@code ClasspathClassSource} using the current thread's context class loader.
     *
     * <p>If the thread context class loader is {@code null}, this class's defining loader is used.</p>
     */
    public ClasspathClassSource() {
        final ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        this.loader = (tccl != null) ? tccl : ClasspathClassSource.class.getClassLoader();
    }

    /**
     * Loads the bytecode for the given class from the configured class loader.
     *
     * <p>The {@code internalName} must be an internal JVM name (slash-separated), e.g.
     * {@code com/example/MyClass}. The resource path is computed as
     * {@code internalName + ".class"} and read fully.</p>
     *
     * <p>The returned array is a fresh buffer containing the exact class bytes.</p>
     *
     * @param internalName the class name in internal JVM format; must not be {@code null}
     * @return the class bytes if the resource exists; {@code null} if the resource is not found
     * @throws IOException if an I/O error occurs while reading the resource or if another
     *                     unexpected failure occurs during access (in which case it is wrapped)
     */
    @Nullable
    @Override
    public byte[] getClassBytes(@NotNull final String internalName) throws IOException {
        Objects.requireNonNull(internalName, "internalName");

        final String res = internalName + ".class";
        try (InputStream in = this.loader.getResourceAsStream(res)) {
            if (in == null) {
                return null; // class not found
            }
            return in.readAllBytes();
        } catch (IOException ioe) {
            throw ioe;
        } catch (Exception ex) {
            throw new IOException("Failed to read resource " + res + " from classpath", ex);
        }
    }
}
