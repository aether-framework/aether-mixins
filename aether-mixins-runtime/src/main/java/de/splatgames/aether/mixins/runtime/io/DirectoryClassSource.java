package de.splatgames.aether.mixins.runtime.io;

import de.splatgames.aether.mixins.core.weaver.spi.ClassSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * {@link ClassSource} that serves class files directly from a directory tree on disk.
 *
 * <h2>Purpose</h2>
 * <p>
 * Resolves an internal JVM class name to a {@code .class} file under a configured root directory
 * and returns its bytes. This is useful for piping already-compiled output into the weaver
 * (e.g., test fixtures, multi-phase weaving, diagnostics).
 * </p>
 *
 * <h2>Naming</h2>
 * <p><b>Internal JVM names only.</b> Use slash separators, not dots:</p>
 * <pre>{@code
 * com/example/MyClass           // internal JVM name (required)
 * com.example.MyClass           // binary name (not accepted)
 *
 * // Resolved file:
 * <root>/com/example/MyClass.class
 * }</pre>
 *
 * <h2>Thread-safety</h2>
 * <p>
 * Instances are immutable and safe to share for concurrent reads. However, if other processes
 * modify files concurrently, consumers may observe races or partial reads (as with any file I/O).
 * </p>
 *
 * <h2>Errors &amp; behavior</h2>
 * <ul>
 *   <li>Returns {@code null} when the class file does not exist.</li>
 *   <li>Propagates {@link IOException} on I/O failures.</li>
 *   <li>Reads the entire file into memory via {@link Files#readAllBytes(Path)}.</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * Path classesDir = Paths.get("build/classes");
 * ClassSource src = new DirectoryClassSource(classesDir);
 *
 * byte[] bytes = src.getClassBytes("com/example/MyClass");
 * if (bytes != null) {
 *     System.out.println("Loaded " + bytes.length + " bytes");
 * }
 * }</pre>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class DirectoryClassSource implements ClassSource {

    /**
     * Root directory containing compiled class files; all lookups are resolved relative to this path.
     */
    @NotNull
    private final Path root;

    /**
     * Creates a new {@code DirectoryClassSource} rooted at the given directory.
     *
     * @param root the directory containing compiled class files; must not be {@code null}
     * @throws NullPointerException if {@code root} is {@code null}
     */
    public DirectoryClassSource(@NotNull final Path root) {
        this.root = Objects.requireNonNull(root, "root");
    }

    /**
     * Loads the bytecode for the given class from the filesystem.
     *
     * <p>The {@code internalName} must be slash-separated (internal JVM name), e.g.
     * {@code com/example/MyClass}. The file path is computed as
     * {@code root.resolve(internalName + ".class")}.</p>
     *
     * <p>If the class file is absent, this method returns {@code null}.</p>
     *
     * @param internalName the class name in internal JVM format; must not be {@code null}
     * @return the class bytes if the file exists; {@code null} if the file does not exist
     * @throws IOException          if an I/O error occurs while reading the file
     * @throws NullPointerException if {@code internalName} is {@code null}
     */
    @Nullable
    @Override
    public byte[] getClassBytes(@NotNull final String internalName) throws IOException {
        Objects.requireNonNull(internalName, "internalName");
        final Path p = this.root.resolve(internalName + ".class");
        if (!Files.exists(p)) {
            return null;
        }
        return Files.readAllBytes(p);
    }
}
