package de.splatgames.aether.mixins.runtime;

import de.splatgames.aether.mixins.bytecode.weaver.asm.AsmWeaver;
import de.splatgames.aether.mixins.bytecode.weaver.hook.DefaultHookResolver;
import de.splatgames.aether.mixins.core.config.ConfigLoader;
import de.splatgames.aether.mixins.core.config.YamlConfigLoader;
import de.splatgames.aether.mixins.core.config.refmap.JsonRefmapLoader;
import de.splatgames.aether.mixins.core.config.refmap.RefmapLoader;
import de.splatgames.aether.mixins.core.plan.SelectionOptions;
import de.splatgames.aether.mixins.core.weaver.spi.ClassSink;
import de.splatgames.aether.mixins.core.weaver.spi.ClassSource;
import de.splatgames.aether.mixins.core.weaver.spi.Weaver;
import de.splatgames.aether.mixins.runtime.io.ClasspathClassSource;
import de.splatgames.aether.mixins.runtime.io.DirectoryClassSink;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Fluent builder for {@link MixinsRuntime}, wiring sensible defaults while allowing targeted overrides.
 *
 * <h2>Defaults</h2>
 * <ul>
 *   <li><b>Config loader:</b> {@link YamlConfigLoader}</li>
 *   <li><b>Refmap loader:</b> {@link JsonRefmapLoader}</li>
 *   <li><b>Class source:</b> {@link ClasspathClassSource} backed by the provided application {@link ClassLoader}</li>
 *   <li><b>Class sink:</b> {@link DirectoryClassSink} writing to {@code ./aether-mixins-out}</li>
 *   <li><b>Weaver:</b> {@link AsmWeaver} with {@link DefaultHookResolver} (initialized with the application loader)</li>
 *   <li><b>Selection options:</b> {@link SelectionOptions#empty()}</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * MixinsRuntime rt = MixinsRuntime.builder(appLoader)
 *     .classSink(new DirectoryClassSink(Path.of("build/mixins-out")))
 *     .options(SelectionOptions.empty())
 *     .build();
 * }</pre>
 *
 * <h2>Thread-safety</h2>
 * <p>
 * The builder is <em>not</em> thread-safe. Use it from a single thread, then discard it after {@link #build()}.
 * The resulting {@link MixinsRuntime} is immutable and suitable for reuse.
 * </p>
 *
 * <h2>Notes</h2>
 * <ul>
 *   <li>All setters validate non-null and return {@code this} for fluent chaining.</li>
 *   <li>If you override the {@link Weaver}, ensure it is compatible with your {@link ClassSource}/{@link ClassSink}.</li>
 * </ul>
 *
 * @author Erik Pförtner
 * @see MixinsRuntime
 * @see MixinsRuntime#createDefault(ClassLoader)
 * @since 0.1.0
 */
public final class MixinsRuntimeBuilder {

    /**
     * Application class loader used by default components (e.g., classpath source, hook resolver).
     */
    @NotNull
    private final ClassLoader appClassLoader;

    /**
     * Loader for YAML mixin configuration.
     */
    @NotNull
    private ConfigLoader configLoader = new YamlConfigLoader();

    /**
     * Loader for JSON refmaps.
     */
    @NotNull
    private RefmapLoader refmapLoader = new JsonRefmapLoader();

    /**
     * Source of original class bytes used by the weaver.
     */
    @NotNull
    private ClassSource classSource;

    /**
     * Sink to publish transformed class bytes.
     */
    @NotNull
    private ClassSink classSink = new DirectoryClassSink(Path.of("aether-mixins-out"));

    /**
     * Bytecode weaver implementation to apply the plan.
     */
    @NotNull
    private Weaver weaver;

    /**
     * Selection options controlling which mixins/groups are applied.
     */
    @NotNull
    private SelectionOptions options = SelectionOptions.empty();

    /**
     * Creates a builder with defaults bound to the given application {@link ClassLoader}.
     *
     * <p>Defaults initialized:</p>
     * <ul>
     *   <li>{@link #classSource} ← {@link ClasspathClassSource} using {@code appClassLoader}</li>
     *   <li>{@link #weaver} ← {@link AsmWeaver} with {@link DefaultHookResolver}{@code (appClassLoader)}</li>
     * </ul>
     *
     * @param appClassLoader application class loader used to resolve hooks and read class bytes; must not be {@code null}
     * @throws NullPointerException if {@code appClassLoader} is {@code null}
     */
    public MixinsRuntimeBuilder(@NotNull final ClassLoader appClassLoader) {
        this.appClassLoader = Objects.requireNonNull(appClassLoader, "appClassLoader");
        this.classSource = new ClasspathClassSource(appClassLoader);
        this.weaver = new AsmWeaver(new DefaultHookResolver(appClassLoader));
    }

    /**
     * Overrides the configuration loader used for parsing the mixin YAML.
     *
     * @param loader configuration loader instance; must not be {@code null}
     * @return this builder (for chaining)
     * @throws NullPointerException if {@code loader} is {@code null}
     */
    @NotNull
    public MixinsRuntimeBuilder configLoader(@NotNull final ConfigLoader loader) {
        this.configLoader = Objects.requireNonNull(loader, "configLoader");
        return this;
    }

    /**
     * Overrides the refmap loader used for parsing remapping metadata.
     *
     * @param loader refmap loader instance; must not be {@code null}
     * @return this builder (for chaining)
     * @throws NullPointerException if {@code loader} is {@code null}
     */
    @NotNull
    public MixinsRuntimeBuilder refmapLoader(@NotNull final RefmapLoader loader) {
        this.refmapLoader = Objects.requireNonNull(loader, "refmapLoader");
        return this;
    }

    /**
     * Sets the class source from which original bytecode is read.
     *
     * @param source class source; must not be {@code null}
     * @return this builder (for chaining)
     * @throws NullPointerException if {@code source} is {@code null}
     */
    @NotNull
    public MixinsRuntimeBuilder classSource(@NotNull final ClassSource source) {
        this.classSource = Objects.requireNonNull(source, "classSource");
        return this;
    }

    /**
     * Sets the class sink to which transformed bytecode is written.
     *
     * @param sink class sink; must not be {@code null}
     * @return this builder (for chaining)
     * @throws NullPointerException if {@code sink} is {@code null}
     */
    @NotNull
    public MixinsRuntimeBuilder classSink(@NotNull final ClassSink sink) {
        this.classSink = Objects.requireNonNull(sink, "classSink");
        return this;
    }

    /**
     * Sets the weaver implementation that applies the weave plan to class bytes.
     *
     * <p><b>Compatibility note:</b> Ensure the weaver is compatible with the configured
     * {@link ClassSource}/{@link ClassSink} and any runtime options you rely on.</p>
     *
     * @param weaver weaver instance; must not be {@code null}
     * @return this builder (for chaining)
     * @throws NullPointerException if {@code weaver} is {@code null}
     */
    @NotNull
    public MixinsRuntimeBuilder weaver(@NotNull final Weaver weaver) {
        this.weaver = Objects.requireNonNull(weaver, "weaver");
        return this;
    }

    /**
     * Sets the selection options controlling which mixin groups/entries are applied.
     *
     * @param options selection options; must not be {@code null}
     * @return this builder (for chaining)
     * @throws NullPointerException if {@code options} is {@code null}
     */
    @NotNull
    public MixinsRuntimeBuilder options(@NotNull final SelectionOptions options) {
        this.options = Objects.requireNonNull(options, "options");
        return this;
    }

    /**
     * Builds an immutable {@link MixinsRuntime} instance using the current builder state.
     *
     * <p>The resulting runtime can be reused across runs. It is safe to discard this builder
     * after construction.</p>
     *
     * @return a new immutable {@link MixinsRuntime}; never {@code null}
     */
    @NotNull
    public MixinsRuntime build() {
        return MixinsRuntime.of(
                this.configLoader,
                this.refmapLoader,
                this.classSource,
                this.classSink,
                this.weaver,
                this.options
        );
    }
}
