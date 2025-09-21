package de.splatgames.aether.mixins.core.config;

/**
 * Defines YAML key constants used by the mixins configuration loader.
 *
 * <p>This utility class centralizes the field names used when parsing
 * {@code mixins.yml}, keeping loader logic typo-safe and refactor-friendly.</p>
 *
 * <h2>Example (YAML)</h2>
 * <blockquote><pre>{@code
 * version: 1
 *
 * mixins:
 *   - name: core-service
 *     files:
 *       - mixins/service.refmap.json
 *
 * runtime:
 *   safe_mode: true
 *   verify_frames: strict
 *   dump_classes_on_error: false
 * }</pre></blockquote>
 *
 * @author Erik Pförtner
 * @see YamlConfigLoader
 * @see de.splatgames.aether.mixins.core.config.mixins.MixinsConfig MixinsConfig
 * @see de.splatgames.aether.mixins.core.config.mixins.MixinSet MixinSet
 * @see de.splatgames.aether.mixins.core.config.runtime.RuntimeConfig RuntimeConfig
 * @since 0.1.0
 */
public final class YamlConfigurationConstants {

    /**
     * Top-level schema version key.
     *
     * <p>Used to determine how the document should be interpreted. The loader
     * currently supports {@code 1}.</p>
     */
    public static final String K_VERSION = "version";

    /**
     * Runtime flag enabling safe mode.
     *
     * <p>Path: {@code runtime.safe_mode}. When {@code true}, failed weave attempts
     * are skipped instead of failing startup.</p>
     *
     * @see de.splatgames.aether.mixins.core.config.runtime.RuntimeConfig#isSafeMode() RuntimeConfig.isSafeMode()
     */
    public static final String K_RUNTIME_SAFE = "runtime.safe_mode";

    /**
     * Verification strategy for stack map frames.
     *
     * <p>Path: {@code runtime.verify_frames}. Accepted values:
     * {@code none}, {@code basic}, {@code strict}.</p>
     *
     * @see de.splatgames.aether.mixins.core.config.runtime.VerifyFrames VerifyFrames
     * @see de.splatgames.aether.mixins.core.config.runtime.RuntimeConfig#getVerifyFrames() RuntimeConfig.getVerifyFrames()
     */
    public static final String K_RUNTIME_VERIFY = "runtime.verify_frames";

    /**
     * Flag to dump class bytes on transformation errors.
     *
     * <p>Path: {@code runtime.dump_classes_on_error}. When {@code true}, the
     * runtime may write original and transformed class bytes to aid debugging.</p>
     *
     * @see de.splatgames.aether.mixins.core.config.runtime.RuntimeConfig#isDumpClassesOnError() RuntimeConfig.isDumpClassesOnError()
     */
    public static final String K_RUNTIME_DUMP = "runtime.dump_classes_on_error";

    /**
     * Flag to allow weakening of final field semantics.
     *
     * <p>Path: {@code runtime.allow_final_field_weakening}. When {@code true},
     * fields annotated with {@code @Mutable} may have their {@code final}
     * semantics weakened by the mixin framework.</p>
     *
     * @see de.splatgames.aether.mixins.core.api.Mutable
     * @see de.splatgames.aether.mixins.core.config.runtime.RuntimeConfig#isAllowFinalFieldWeakening() RuntimeConfig.isAllowFinalFieldWeakening()
     * @since 0.2.0
     */
    public static final String K_RUNTIME_ALLOW_FINAL_FIELD_WEAKENING = "runtime.allow_final_field_weakening";

    /**
     * Top-level list of mixin set declarations.
     *
     * <p>Path: {@code mixins}. Each entry describes a named set of refmap files.</p>
     *
     * @see de.splatgames.aether.mixins.core.config.mixins.MixinsConfig#getMixins() MixinsConfig.getMixins()
     */
    public static final String K_MIXINS = "mixins";

    /**
     * Per-set display name.
     *
     * <p>Path: {@code mixins[].name}. Human-readable identifier used in diagnostics.</p>
     *
     * @see de.splatgames.aether.mixins.core.config.mixins.MixinSet#getName() MixinSet.getName()
     */
    public static final String K_NAME = "name";

    /**
     * Per-set list of refmap files.
     *
     * <p>Path: {@code mixins[].files}. Defines file locations (filesystem or classpath)
     * that contain resolved symbolic bindings for mixin hooks.</p>
     *
     * @see de.splatgames.aether.mixins.core.config.mixins.MixinSet#getFiles() MixinSet.getFiles()
     */
    public static final String K_FILES = "files";

    /**
     * Per-refmap list of target classes.
     *
     * <p>Path: {@code mixins[].classes}. Defines the set of target classes
     * that the refmap's mixins may apply to. If omitted or empty, all
     * classes are considered.</p>
     */
    public static final String K_CLASSES = "classes";

    private YamlConfigurationConstants() {
        // utility class
    }
}
