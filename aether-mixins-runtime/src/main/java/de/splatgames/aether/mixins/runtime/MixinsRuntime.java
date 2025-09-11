package de.splatgames.aether.mixins.runtime;

import de.splatgames.aether.mixins.core.config.ConfigLoader;
import de.splatgames.aether.mixins.core.config.problems.ConfigProblems;
import de.splatgames.aether.mixins.core.config.refmap.RefmapLoader;
import de.splatgames.aether.mixins.core.plan.SelectionOptions;
import de.splatgames.aether.mixins.core.weaver.spi.ClassSink;
import de.splatgames.aether.mixins.core.weaver.spi.ClassSource;
import de.splatgames.aether.mixins.core.weaver.spi.WeaveResult;
import de.splatgames.aether.mixins.core.weaver.spi.Weaver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * High-level runtime facade that wires configuration/loaders/sources/sinks and executes a full
 * Aether Mixins session in one call.
 *
 * <h2>Purpose</h2>
 * <p>
 * {@code MixinsRuntime} coordinates reading the YAML mixin config and refmaps, selecting mixins
 * per {@link SelectionOptions}, resolving classes via {@link ClassSource}, applying transformations
 * through a {@link Weaver}, and publishing resulting bytes to a {@link ClassSink}. It provides
 * sensible defaults while allowing customization via {@link #builder(ClassLoader)}.
 * </p>
 *
 * <h2>Typical usage</h2>
 * <pre>{@code
 * // Use defaults bound to your application ClassLoader
 * MixinsRuntime rt = MixinsRuntime.createDefault(MyApp.class.getClassLoader());
 * WeaveResult result = rt.run(); // auto-discovers mixins.yml
 *
 * // Or configure explicitly
 * MixinsRuntime rt2 = MixinsRuntime.builder(MyApp.class.getClassLoader())
 *     .classSink(new DirectoryClassSink(Path.of("build/mixins-out")))
 *     .options(SelectionOptions.empty())
 *     .build();
 * WeaveResult result2 = rt2.run(Path.of("config/mixins.yml"));
 * }</pre>
 *
 * <h2>Configuration discovery (when using {@link #run})</h2>
 * <ol>
 *   <li>System property {@code -D}{@link #SYSPROP_CONFIG}{@code =&lt;path&gt;}</li>
 *   <li>Working directory: {@code ./mixins.yml} or {@code ./mixins.yaml}</li>
 *   <li>Classpath resource {@code /mixins.yml} (extracted to a temporary file)</li>
 * </ol>
 * <p>Use {@link #run(Path)} to bypass discovery with an explicit path.</p>
 *
 * <h2>Diagnostics</h2>
 * <p>
 * Problems encountered during configuration and planning are collected in a {@link ConfigProblems}
 * instance. This runtime prints a minimal console summary (to {@code System.err}) after execution.
 * Your application can also inspect the returned {@link WeaveResult} for outcomes.
 * </p>
 *
 * <h2>Thread-safety</h2>
 * <p>
 * Instances are immutable and thread-safe for reuse. However, {@link #run} performs I/O and
 * should not be invoked concurrently against the same output location unless you coordinate
 * external synchronization for your {@link ClassSink}.
 * </p>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class MixinsRuntime {

    /**
     * System property key used during config discovery (see {@link #run}).
     * <p>Usage: {@code -Daether.mixins.config=/path/to/mixins.yml}</p>
     */
    public static final String SYSPROP_CONFIG = "aether.mixins.config";

    /**
     * Loader for YAML mixin configuration.
     */
    @NotNull
    private final ConfigLoader configLoader;

    /**
     * Loader for refmap metadata.
     */
    @NotNull
    private final RefmapLoader refmapLoader;

    /**
     * Bytecode source for reading original classes.
     */
    @NotNull
    private final ClassSource classSource;

    /**
     * Sink for publishing transformed classes.
     */
    @NotNull
    private final ClassSink classSink;

    /**
     * Weaver implementation that applies the plan.
     */
    @NotNull
    private final Weaver weaver;

    /**
     * Selection options controlling which mixins/groups apply.
     */
    @NotNull
    private final SelectionOptions options;

    /**
     * Creates a runtime with the provided components.
     *
     * @param configLoader loader for YAML mixin configuration; must not be {@code null}
     * @param refmapLoader loader for refmap metadata; must not be {@code null}
     * @param classSource  bytecode source for reading original classes; must not be {@code null}
     * @param classSink    sink for publishing transformed classes; must not be {@code null}
     * @param weaver       weaver implementation that applies the plan; must not be {@code null}
     * @param options      selection options controlling which mixins/groups apply; must not be {@code null}
     * @throws NullPointerException if any parameter is {@code null}
     */
    private MixinsRuntime(
            @NotNull final ConfigLoader configLoader,
            @NotNull final RefmapLoader refmapLoader,
            @NotNull final ClassSource classSource,
            @NotNull final ClassSink classSink,
            @NotNull final Weaver weaver,
            @NotNull final SelectionOptions options
    ) {
        this.configLoader = Objects.requireNonNull(configLoader, "configLoader");
        this.refmapLoader = Objects.requireNonNull(refmapLoader, "refmapLoader");
        this.classSource = Objects.requireNonNull(classSource, "classSource");
        this.classSink = Objects.requireNonNull(classSink, "classSink");
        this.weaver = Objects.requireNonNull(weaver, "weaver");
        this.options = Objects.requireNonNull(options, "options");
    }

    /**
     * Creates a {@link MixinsRuntime} configured with sensible defaults bound to the given
     * application {@link ClassLoader}.
     *
     * <p>Defaults include a YAML config loader, JSON refmap loader, classpath-based class source,
     * a filesystem sink writing to {@code ./aether-mixins-out}, and an ASM-based weaver with a
     * default hook resolver.</p>
     *
     * @param appClassLoader application {@link ClassLoader} used by default components; must not be {@code null}
     * @return a new runtime instance with defaults; never {@code null}
     * @throws NullPointerException if {@code appClassLoader} is {@code null}
     */
    @NotNull
    public static MixinsRuntime createDefault(@NotNull final ClassLoader appClassLoader) {
        return builder(appClassLoader).build();
    }

    /**
     * Returns a fluent builder that starts with default components but allows targeted overrides.
     *
     * @param appClassLoader application {@link ClassLoader} to bind defaults to; must not be {@code null}
     * @return a new {@link MixinsRuntimeBuilder}; never {@code null}
     * @throws NullPointerException if {@code appClassLoader} is {@code null}
     */
    @NotNull
    public static MixinsRuntimeBuilder builder(@NotNull final ClassLoader appClassLoader) {
        return new MixinsRuntimeBuilder(appClassLoader);
    }

    /**
     * Executes a full mixins session using configuration discovered by {@link #discoverConfigPath)}.
     *
     * <p>See the discovery order in the class-level documentation. Use {@link #run(Path)} to pass an
     * explicit configuration path.</p>
     *
     * @param additionalDiscoverableConfigs optional additional config paths to consider during discovery
     * @return a {@link WeaveResult} summarizing per-class outcomes and totals; never {@code null}
     * @throws Exception if YAML I/O/parse fails fatally or if the weaver throws in non-safe mode
     */
    @NotNull
    public WeaveResult run(@Nullable final String... additionalDiscoverableConfigs) throws Exception {
        final Path config = discoverConfigPath(additionalDiscoverableConfigs);
        return this.run(config);
    }

    /**
     * Executes a full mixins session with an explicit YAML configuration path.
     *
     * <p>This method loads and validates the configuration/refmaps, plans the weaving based on the
     * supplied {@link SelectionOptions}, and invokes the {@link Weaver}. Diagnostics are collected
     * in a {@link ConfigProblems} instance and summarized to {@code System.err}.</p>
     *
     * @param yamlConfig path to the YAML configuration file; must not be {@code null}
     * @return a {@link WeaveResult} summarizing per-class outcomes and totals; never {@code null}
     * @throws Exception if YAML I/O/parse fails fatally or if the weaver throws in non-safe mode
     * @throws NullPointerException if {@code yamlConfig} is {@code null}
     */
    @NotNull
    public WeaveResult run(@NotNull final Path yamlConfig) throws Exception {
        final var driver = new RuntimeDriver(this.configLoader, this.refmapLoader);
        final var problems = new ConfigProblems("MixinsRuntime");

        final var session = driver.execute(
                yamlConfig,
                this.options,
                this.classSource,
                this.classSink,
                this.weaver,
                problems
        );

        // Minimal console diagnostics (optional)
        if (!problems.all().isEmpty()) {
            for (final var p : problems.all()) {
                System.err.println("[Aether Mixins] " + p.severity() + " @ " + p.path() + " — " + p.message());
            }
        }

        return session.result();
    }

    /**
     * Discovers the YAML configuration path from system properties, working directory, or classpath.
     *
     * <p>Order:</p>
     * <ol>
     *   <li>{@code -D}{@link #SYSPROP_CONFIG}{@code =&lt;path&gt;}</li>
     *   <li>{@code ./mixins.yml}, then {@code ./mixins.yaml}</li>
     *   <li>Classpath resource {@code /mixins.yml} (extracted to a temporary file)</li>
     *   <li>Any additional paths passed to {@link #run(String...)}</li>
     *   <li>Otherwise, throws {@link IllegalStateException}</li>
     * </ol>
     *
     * @param additionalDiscoverableConfigs optional additional config paths to consider during discovery
     * @return the resolved config {@link Path}; never {@code null}
     * @throws IllegalStateException if no configuration can be located
     */
    @NotNull
    private static Path discoverConfigPath(@Nullable final String... additionalDiscoverableConfigs) {
        final String prop = System.getProperty(SYSPROP_CONFIG);
        if (prop != null && !prop.isBlank()) {
            return Path.of(prop.trim());
        }
        final Path cwdYml = Path.of("mixins.yml");
        if (Files.isRegularFile(cwdYml)) {
            return cwdYml;
        }
        final Path cwdYaml = Path.of("mixins.yaml");
        if (Files.isRegularFile(cwdYaml)) {
            return cwdYaml;
        }

        final Path tmp = extractClasspathResource("/mixins.yml");
        if (tmp != null) return tmp;

        if (additionalDiscoverableConfigs != null) {
            for (final String p : additionalDiscoverableConfigs) {
                if (p != null && !p.isBlank()) {
                    final Path path = Path.of(p.trim());
                    if (Files.isRegularFile(path)) {
                        return path;
                    }

                    final Path alt = extractClasspathResource(p.trim());
                    if (alt != null) {
                        return alt;
                    }
                }
            }
        }

        throw new IllegalStateException(
                "No mixins config found. Provide -D" + SYSPROP_CONFIG + "=<path> or place mixins.yml in the working directory."
        );
    }

    /**
     * Extracts a classpath resource to a temporary file for loaders that operate on {@link Path}.
     *
     * <p>The {@code resource} string must be absolute (e.g., {@code /mixins.yml}). If the resource
     * does not exist, {@code null} is returned. Otherwise, a temp file is created and the resource
     * is copied into it (overwriting if needed) and the path is returned.</p>
     *
     * @param resource absolute resource path; must not be {@code null}
     * @return a {@link Path} to the extracted temp file or {@code null} if not found
     * @throws UncheckedIOException if the resource exists but cannot be copied
     * @throws NullPointerException if {@code resource} is {@code null}
     */
    @Nullable
    private static Path extractClasspathResource(@NotNull final String resource) {
        try (InputStream in = MixinsRuntime.class.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            final Path tmp = Files.createTempFile("aether-mixins-", "-config.yml");
            Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return tmp;
        } catch (final IOException ioe) {
            throw new UncheckedIOException("Failed to extract classpath resource " + resource, ioe);
        }
    }

    /**
     * Package-private factory used by {@link MixinsRuntimeBuilder} to create immutable runtime instances.
     *
     * @param configLoader loader for YAML mixin configuration; must not be {@code null}
     * @param refmapLoader loader for refmap metadata; must not be {@code null}
     * @param classSource  bytecode source for reading original classes; must not be {@code null}
     * @param classSink    sink for publishing transformed classes; must not be {@code null}
     * @param weaver       weaver implementation that applies the plan; must not be {@code null}
     * @param options      selection options controlling which mixins/groups apply; must not be {@code null}
     * @return a new immutable runtime; never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    @NotNull
    static MixinsRuntime of(
            @NotNull final ConfigLoader configLoader,
            @NotNull final RefmapLoader refmapLoader,
            @NotNull final ClassSource classSource,
            @NotNull final ClassSink classSink,
            @NotNull final Weaver weaver,
            @NotNull final SelectionOptions options
    ) {
        return new MixinsRuntime(configLoader, refmapLoader, classSource, classSink, weaver, options);
    }
}
