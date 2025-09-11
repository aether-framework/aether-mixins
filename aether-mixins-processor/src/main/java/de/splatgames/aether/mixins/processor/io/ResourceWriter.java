package de.splatgames.aether.mixins.processor.io;

import org.jetbrains.annotations.NotNull;

import javax.annotation.processing.Filer;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Utility for writing generated resources to the annotation processor's
 * {@link StandardLocation#CLASS_OUTPUT} location.
 *
 * <h2>Purpose</h2>
 * <p>
 * During annotation processing, generated artifacts (e.g., {@code mixins.yml}, refmaps, metadata)
 * must be emitted into the class output so they end up on the runtime classpath. This helper
 * centralizes the creation and writing of such resources via the {@link Filer}.
 * </p>
 *
 * <h2>Path semantics</h2>
 * <ul>
 *   <li>{@code relativePath} is interpreted relative to {@code CLASS_OUTPUT} and may contain
 *       directory segments (e.g., {@code mixins/app.refmap.json}).</li>
 *   <li>Intermediate directories are handled by the underlying {@link Filer} implementation.</li>
 *   <li>If a resource with the same path was already created in the same compilation (round),
 *       the {@link Filer} may throw an {@link IOException}. Callers should ensure each resource
 *       is created at most once per compilation.</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * <p>
 * This class is stateless and thread-safe. The passed {@link Filer} governs actual I/O and may
 * impose its own threading constraints; adhere to the annotation processing contract when invoking
 * this method.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // inside an annotation processor
 * String yaml = "version: 1\nmixins:\n  - name: app\n  files:\n    - mixins/app.refmap.json\n";
 * ResourceWriter.write(processingEnv.getFiler(), "mixins.yml", yaml.getBytes(StandardCharsets.UTF_8));
 *
 * byte[] json = ...; // pretty-printed refmap
 * ResourceWriter.write(processingEnv.getFiler(), "mixins/app.refmap.json", json);
 * }</pre>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class ResourceWriter {

    /**
     * Non-instantiable utility class.
     */
    private ResourceWriter() {
        // utility class, prevent instantiation
    }

    /**
     * Writes a resource into {@link StandardLocation#CLASS_OUTPUT}.
     *
     * <p>The {@code relativePath} is interpreted relative to the root of {@code CLASS_OUTPUT}.
     * The method opens a new {@link OutputStream} for the created resource and writes the provided
     * {@code bytes} in a single shot.</p>
     *
     * <p><b>Idempotency:</b> In a single compilation, attempting to create the same resource more than
     * once may result in an {@link IOException}. Ensure you only emit each resource once (e.g., guard
     * with a flag across rounds).</p>
     *
     * @param filer        annotation-processing filer used to create resources, must not be {@code null}
     * @param relativePath path relative to {@code CLASS_OUTPUT} (e.g., {@code mixins/app.refmap.json}),
     *                     must not be {@code null}
     * @param bytes        content to write (use UTF-8 for text resources), must not be {@code null}
     * @throws IOException if the resource cannot be created or written
     * @throws NullPointerException if any argument is {@code null}
     */
    public static void write(@NotNull final Filer filer,
                             @NotNull final String relativePath,
                             @NotNull final byte[] bytes) throws IOException {
        final FileObject fo = filer.createResource(StandardLocation.CLASS_OUTPUT, "", relativePath);
        try (OutputStream os = fo.openOutputStream()) {
            os.write(bytes);
        }
    }
}
