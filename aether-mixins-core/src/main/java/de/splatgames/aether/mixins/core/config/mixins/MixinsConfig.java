package de.splatgames.aether.mixins.core.config.mixins;

import de.splatgames.aether.mixins.core.config.runtime.RuntimeConfig;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * Represents the top-level configuration for the Aether Mixins runtime.
 *
 * <p>This model is typically populated by parsing an external configuration file,
 * such as YAML or JSON. The configuration defines both global runtime options
 * and one or more {@linkplain MixinSet mixin sets} containing mixin definitions
 * and their associated refmap files.</p>
 *
 * <h2>Usage</h2>
 * The configuration is usually loaded from a file referenced by the system property
 * {@code aether.mixins.config}. Example YAML:
 *
 * <blockquote><pre>{@code
 * schema: 1
 *
 * mixins:
 *   - name: core-service
 *     files:
 *       - mixins/service.refmap.json
 *
 * runtime:
 *   safe_mode: true
 *   verify_frames: strict
 *   dump_classes_on_error: true
 * }</pre></blockquote>
 *
 * <p>The {@code schema} field indicates the format version of the configuration file.
 * The default version is {@code 1}.</p>
 *
 * @author Erik Pförtner
 * @see MixinSet
 * @see RuntimeConfig
 * @since 0.1.0
 */
public final class MixinsConfig {

    /**
     * Configuration schema version.
     *
     * <p>This field is used to ensure compatibility between configuration files
     * and the runtime parser. A value of {@code 1} indicates the initial format.</p>
     */
    private final int version;

    /**
     * List of declared mixin sets.
     *
     * <p>Each {@link MixinSet} entry contributes one or more refmap files that
     * define how mixins are applied to target classes.</p>
     */
    private List<MixinSet> mixins;

    /**
     * Global runtime configuration.
     *
     * <p>Controls safety, diagnostics, and verification behavior for the runtime.</p>
     */
    private RuntimeConfig runtime;

    /**
     * Creates a new {@code MixinsConfig} instance with the specified configuration version.
     *
     * @param version the configuration version, must be &ge; 1
     */
    public MixinsConfig(final int version) {
        this.version = version;
    }

    /**
     * Returns the configuration schema version.
     *
     * @return configuration version, always &ge; 1
     */
    public int getVersion() {
        return this.version;
    }

    /**
     * Returns the configured sets of mixins.
     *
     * <p>Each set may contribute one or more refmap files that describe concrete
     * targets and hook bindings for the mixin system.</p>
     *
     * @return list of mixin sets, never {@code null} (may be empty)
     * @see MixinSet
     */
    @NotNull
    public List<MixinSet> getMixins() {
        return this.mixins;
    }

    /**
     * Sets the configured mixin sets.
     *
     * @param mixins list of mixin sets, must not be {@code null}
     */
    public void setMixins(@NotNull final List<MixinSet> mixins) {
        this.mixins = mixins;
    }

    /**
     * Returns the runtime configuration section.
     *
     * <p>This section defines safety rules, verification strategies,
     * and diagnostic behaviors for the runtime environment.</p>
     *
     * @return runtime configuration, never {@code null}
     * @see RuntimeConfig
     */
    @NotNull
    public RuntimeConfig getRuntime() {
        return this.runtime;
    }

    /**
     * Sets the runtime configuration section.
     *
     * @param runtime the runtime configuration, must not be {@code null}
     */
    public void setRuntime(@NotNull final RuntimeConfig runtime) {
        this.runtime = runtime;
    }

    /**
     * Returns a string representation of this configuration for debugging purposes.
     *
     * @return string representation of this configuration
     */
    @Override
    public String toString() {
        return "MixinsConfig{version=" + this.version +
                ", mixins=" + this.mixins +
                ", runtime=" + this.runtime + '}';
    }

    /**
     * Computes a hash code based on the mixin sets and runtime configuration.
     *
     * @return hash code for this configuration
     */
    @Override
    public int hashCode() {
        return Objects.hash(this.version, this.mixins, this.runtime);
    }

    /**
     * Compares this configuration to another for equality.
     *
     * @param o the object to compare with
     * @return {@code true} if both configurations are equal, {@code false} otherwise
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MixinsConfig that)) {
            return false;
        }
        return this.version == that.version &&
                Objects.equals(this.mixins, that.mixins) &&
                Objects.equals(this.runtime, that.runtime);
    }
}
