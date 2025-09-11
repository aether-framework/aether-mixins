package de.splatgames.aether.mixins.runtime.io;

import de.splatgames.aether.mixins.core.weaver.spi.ClassSink;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * {@link ClassSink} that writes transformed class bytes to a directory on the filesystem.
 *
 * <h2>Purpose</h2>
 * <p>
 * This sink materializes woven classes as {@code .class} files under a configured root directory.
 * The input class name must be an <em>internal JVM name</em> (slash-separated). The output path is
 * constructed as {@code &lt;root&gt;/&lt;internalName&gt;.class}, creating any missing parent
 * directories along the way.
 * </p>
 *
 * <h2>Naming</h2>
 * <pre>{@code
 * com/example/MyClass   // internal JVM name (required)
 * com.example.MyClass   // binary name (not accepted)
 *
 * // Output:
 * <root>/com/example/MyClass.class
 * }</pre>
 *
 * <h2>Directory handling</h2>
 * <ul>
 *   <li>Parent directories are created automatically via {@link Files#createDirectories)}.</li>
 *   <li>The root directory need not exist beforehand.</li>
 *   <li>Existing files at the target path are overwritten.</li>
 * </ul>
 *
 * <h2>Thread-safety</h2>
 * <p>
 * Instances are not inherently thread-safe. If multiple threads write to the same sink/root,
 * synchronize externally to avoid interleaving writes or partial files.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * Path outDir = Paths.get("build/output-classes");
 * ClassSink sink = new DirectoryClassSink(outDir);
 *
 * sink.accept("com/example/MyClass", classBytes);
 * // => build/output-classes/com/example/MyClass.class
 * }</pre>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class DirectoryClassSink implements ClassSink {

    /**
     * Root directory under which all class files are written; never {@code null}.
     */
    @NotNull
    private final Path root;

    /**
     * Creates a new sink writing to the given root directory.
     *
     * <p>The directory (and any required parents for written files) will be created on demand.</p>
     *
     * @param root the root output directory; must not be {@code null}
     * @throws NullPointerException if {@code root} is {@code null}
     */
    public DirectoryClassSink(@NotNull final Path root) {
        this.root = Objects.requireNonNull(root, "root");
    }

    /**
     * Writes the given class bytes to {@code <root>/<internalName>.class}, creating parent
     * directories as needed and overwriting any existing file.
     *
     * <p>The {@code internalName} must be an internal JVM name (slash-separated), e.g.
     * {@code com/example/MyClass}.</p>
     *
     * @param internalName class name in internal JVM format; must not be {@code null}
     * @param bytes        class byte array to write; must not be {@code null}
     * @throws IOException          if an I/O error occurs while creating directories or writing the file
     * @throws NullPointerException if {@code internalName} or {@code bytes} is {@code null}
     */
    @Override
    public void accept(@NotNull final String internalName, @NotNull final byte[] bytes) throws IOException {
        Objects.requireNonNull(internalName, "internalName");
        Objects.requireNonNull(bytes, "bytes");

        final Path out = this.root.resolve(internalName + ".class");

        final Path parent = out.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Files.write(out, bytes);
    }
}
