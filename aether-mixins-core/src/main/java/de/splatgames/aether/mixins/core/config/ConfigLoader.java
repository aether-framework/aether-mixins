package de.splatgames.aether.mixins.core.config;

import de.splatgames.aether.mixins.core.config.mixins.MixinsConfig;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Defines the contract for loading a {@link MixinsConfig} from an external source.
 *
 * <p>Implementations of this interface handle the process of reading and parsing
 * a mixins configuration file from a specified location. This allows the runtime
 * to remain decoupled from specific file formats or parsing libraries.</p>
 *
 * <h2>Supported Sources</h2>
 * <ul>
 *   <li><b>Filesystem:</b> Load from a regular file path using standard I/O.</li>
 *   <li><b>Classpath Resource:</b> Optionally support loading from resources bundled
 *       in the application's classpath.</li>
 *   <li><b>Different Formats:</b> Implementations may support various formats such
 *       as YAML or JSON without affecting runtime consumers.</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <blockquote><pre>{@code
 * Path configPath = Path.of("config/mixins.yml");
 * ConfigLoader loader = new YamlConfigLoader();
 *
 * try {
 *     MixinsConfig config = loader.load(configPath);
 *     System.out.println("Loaded mixins: " + config.getMixins().size());
 * } catch (IOException e) {
 *     System.err.println("Failed to read configuration file: " + e.getMessage());
 * } catch (IllegalArgumentException e) {
 *     System.err.println("Invalid configuration content: " + e.getMessage());
 * }
 * }</pre></blockquote>
 *
 * @author Erik Pförtner
 * @see MixinsConfig
 * @since 0.1.0
 */
public interface ConfigLoader {

    /**
     * Loads and parses the mixins configuration from the specified location.
     *
     * <p>The returned configuration must be fully initialized and ready for use.
     * Implementations may apply default values for missing optional fields.</p>
     *
     * @param location the path to the configuration file, must not be {@code null}
     * @return a fully parsed {@link MixinsConfig} instance, never {@code null}
     *
     * @throws IOException
     *         if the file cannot be read due to I/O issues, such as missing files or
     *         insufficient permissions
     * @throws IllegalArgumentException
     *         if the file content is syntactically invalid or cannot be parsed into
     *         a valid configuration
     */
    @NotNull
    MixinsConfig load(@NotNull final Path location)
            throws IOException, IllegalArgumentException;
}
