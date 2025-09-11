package de.splatgames.aether.mixins.processor.emit;

import org.jetbrains.annotations.NotNull;

/**
 * Builds a minimal {@code mixins.yml} document that references a generated refmap.
 *
 * <p>The emitted YAML follows the current Aether Mixins schema (version {@code 1}) and contains a single
 * mixin set named after the provided {@code artifact}. The set includes one entry in {@code files}
 * pointing to the refmap file path supplied via {@code refmapPath}.</p>
 *
 * <h2>Output shape</h2>
 * <pre>
 * version: 1
 * mixins:
 *   - name: &lt;artifact&gt;
 *     files:
 *       - &lt;refmapPath&gt;
 * </pre>
 *
 * <h2>Line endings</h2>
 * <p>The builder uses {@link System#lineSeparator()} for portability. If you need a specific newline
 * convention (e.g., {@code \n} for archives), normalize the returned string in your caller.</p>
 *
 * <h2>Thread-safety</h2>
 * <p>This is a stateless utility. The class is thread-safe.</p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * String yaml = MixinsYamlBuilder.build("my-artifact", "mixins/my-artifact.refmap.json");
 * // write yaml to CLASS_OUTPUT as mixins.yml
 * }</pre>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class MixinsYamlBuilder {
    /**
     * Utility class should not be instantiated.
     */
    private MixinsYamlBuilder() {
        // utility class, prevent instantiation
    }

    /**
     * Produces a minimal {@code mixins.yml} that references a single refmap.
     *
     * <p>The returned document sets {@code version: 1} and defines one mixin set:</p>
     * <ul>
     *   <li>{@code name} → the provided {@code artifact}</li>
     *   <li>{@code files} → a single entry containing {@code refmapPath}</li>
     * </ul>
     *
     * @param artifact   logical identifier for the mixin set (also used as display name), must not be {@code null}
     * @param refmapPath path to the generated refmap file (relative or absolute as you need), must not be {@code null}
     * @return YAML text using the platform line separator, never {@code null}
     */
    @NotNull
    public static String build(@NotNull final String artifact,
                               @NotNull final String refmapPath) {
        final String nl = System.lineSeparator();
        final String indent = "  ";
        return "version: 1" + nl
                + "mixins:" + nl
                + indent + "- name: " + artifact + nl
                + indent + "  files:" + nl
                + indent + "    - " + refmapPath + nl;
    }
}
