package de.splatgames.aether.mixins.core.config.refmap;

import de.splatgames.aether.mixins.core.config.problems.ConfigProblems;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Loads a {@link Refmap} from a given file path.
 *
 * <p>Implementations read and parse a refmap document (typically JSON) and
 * materialize it into a {@link Refmap} model. Non-fatal issues (unknown fields,
 * missing optional values, recoverable schema mismatches) should be recorded in
 * the provided {@link ConfigProblems} collector. I/O failures should be surfaced
 * via {@link IOException}.</p>
 *
 * <h2>Error Handling</h2>
 * <ul>
 *   <li><b>I/O errors</b> (e.g., file not found, unreadable): throw {@link IOException}.</li>
 *   <li><b>Content issues</b> (e.g., unrecognized schema version, missing required fields):
 *       record via {@link ConfigProblems} (warnings and/or errors). Implementations may return
 *       a partially populated {@link Refmap} if appropriate.</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <blockquote><pre>{@code
 * Path file = Path.of("mixins/service.refmap.json");
 * ConfigProblems problems = new ConfigProblems(file.toString());
 * RefmapLoader loader = new JsonRefmapLoader(); // example implementation
 *
 * Refmap refmap = loader.load(file, problems);
 * if (problems.hasErrors()) {
 *     // decide whether to abort or continue in safe mode
 * }
 * }</pre></blockquote>
 *
 * @author Erik Pförtner
 * @see Refmap
 * @see ConfigProblems
 * @since 0.1.0
 */
public interface RefmapLoader {

    /**
     * Loads and parses a refmap document from the given file.
     *
     * <p>Implementations should not throw for recoverable content issues; instead they
     * should record them in {@code problems}. Only I/O failures should result in an exception.</p>
     *
     * @param file     the refmap file to read, must not be {@code null}
     * @param problems diagnostics collector to record warnings/errors, must not be {@code null}
     * @return a parsed {@link Refmap} instance, never {@code null}
     * @throws IOException if the file cannot be read or an I/O error occurs
     */
    @NotNull
    Refmap load(@NotNull Path file, @NotNull ConfigProblems problems) throws IOException;
}
