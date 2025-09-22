package de.splatgames.aether.mixins.bytecode.weaver.asm.util;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight container for mixin declaration metadata discovered during class scanning.
 *
 * <p>This type aggregates the essential attributes commonly carried by a mixin declaration,
 * such as whether a {@code @Mixin}-style annotation is present, the list of declared targets,
 * arbitrary string {@code values} (for frameworks that support keyed mixin variants), and the
 * effective mixin {@linkplain #getPriority() priority}.</p>
 *
 * <h2>Semantics</h2>
 * <ul>
 *   <li><b>Presence flag:</b> {@link #hasMixin()} indicates whether the scanned class was
 *       recognized as a mixin (e.g., annotated appropriately) versus a plain class.</li>
 *   <li><b>Targets:</b> {@link #getTargets()} returns a <em>live</em> list of target class
 *       identifiers (typically internal JVM names or canonical names, depending on the scanner).
 *       Callers may append to this list to accumulate discovered targets.</li>
 *   <li><b>Values:</b> {@link #getValues()} returns a <em>live</em> list of free-form strings
 *       associated with the mixin (for feature flags, profiles, or multi-target hints).</li>
 *   <li><b>Priority:</b> {@link #getPriority()} returns the deterministic ordering weight used
 *       when multiple mixins contribute to the same join point; lower numbers run first. The
 *       default is {@code 1000}, mirroring common mixin conventions.</li>
 * </ul>
 *
 * <h2>Mutability &amp; Thread-safety</h2>
 * <p>The lists returned by {@link #getTargets()} and {@link #getValues()} are owned by this
 * instance and are <em>mutable</em>. They are not synchronized. If multiple threads mutate the
 * same {@code MixinMeta} instance, external synchronization is required.</p>
 *
 * @author Erik Pförtner
 * @apiNote This class intentionally performs no validation on the semantics of targets or values;
 * validation is expected to occur in higher-level planning or configuration layers.
 * @implNote The default priority is {@code 1000}. No upper or lower bound is enforced here, but
 * consumers should define and document their accepted ranges.
 * @since 0.2.0
 */
public final class MixinMeta {
    private final boolean hasMixin;
    private final List<String> targets = new ArrayList<>();
    private final List<String> values = new ArrayList<>();
    private int priority = 1000;

    /**
     * Creates a new metadata container.
     *
     * @param hasMixin {@code true} if the scanned class was recognized as a mixin; {@code false} otherwise
     */
    public MixinMeta(final boolean hasMixin) {
        this.hasMixin = hasMixin;
    }

    /**
     * Indicates whether the scanned class was recognized as a mixin declaration.
     *
     * @return {@code true} if a mixin annotation or equivalent marker was found; {@code false} otherwise
     */
    public boolean hasMixin() {
        return this.hasMixin;
    }

    /**
     * Returns the <em>live</em> list of declared mixin targets.
     *
     * <p>The list is mutable and owned by this {@code MixinMeta} instance; callers may add or
     * remove entries to reflect discovered target classes. The expected identifier format (internal
     * JVM name vs. canonical name) depends on the upstream scanner that populates this object.</p>
     *
     * @return a non-{@code null} mutable list of target identifiers
     */
    @NotNull
    public List<String> getTargets() {
        return this.targets;
    }

    /**
     * Returns the <em>live</em> list of arbitrary string values associated with the mixin.
     *
     * <p>This list can be used to carry framework-specific knobs such as feature flags, group
     * names, environment qualifiers, or selector keys.</p>
     *
     * @return a non-{@code null} mutable list of values
     */
    @NotNull
    public List<String> getValues() {
        return this.values;
    }

    /**
     * Returns the effective priority of this mixin, where lower numbers take precedence
     * during conflict resolution or ordered application.
     *
     * <p>The default is {@code 1000}.</p>
     *
     * @return the current priority
     */
    public int getPriority() {
        return this.priority;
    }

    /**
     * Sets the priority for this mixin, where lower numbers take precedence during application.
     *
     * <p>No bounds checking is performed here; callers should apply any required policy externally.</p>
     *
     * @param priority the priority to assign
     */
    public void setPriority(final int priority) {
        this.priority = priority;
    }
}
