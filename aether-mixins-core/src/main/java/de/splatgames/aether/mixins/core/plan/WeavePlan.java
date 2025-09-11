package de.splatgames.aether.mixins.core.plan;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * Immutable weave plan representing the set of mixins and their hooks to be applied.
 *
 * <p>This is a compilation artifact derived from refmaps and selection rules.
 * Runtime weavers consume this plan to drive bytecode transformations.</p>
 *
 * <p>The order of {@linkplain #getMixins() mixins} is preserved from input and
 * may be used by downstream components to ensure deterministic behavior.</p>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class WeavePlan {
    @NotNull
    private final List<@NotNull PlannedMixin> mixins;

    /**
     * Creates a new plan.
     *
     * <p>All parameters must be non-null; list elements must also be non-null.
     * A defensive, unmodifiable copy is taken.</p>
     *
     * @param mixins list of planned mixins, must not be {@code null} and must not contain {@code null} elements
     * @throws NullPointerException if {@code mixins} or any element is {@code null}
     */
    public WeavePlan(@NotNull final List<@NotNull PlannedMixin> mixins) {
        Objects.requireNonNull(mixins, "mixins");
        for (PlannedMixin pm : mixins) Objects.requireNonNull(pm, "mixins element");
        this.mixins = List.copyOf(mixins);
    }

    /**
     * Returns an empty weave plan.
     *
     * @return empty plan, never {@code null}
     */
    @NotNull
    public static WeavePlan empty() {
        return new WeavePlan(List.of());
    }

    /**
     * Returns the planned mixins.
     *
     * @return immutable list of mixins, never {@code null}
     */
    @NotNull
    public List<@NotNull PlannedMixin> getMixins() {
        return this.mixins;
    }

    @Override
    public String toString() {
        return "WeavePlan{mixins=" + this.mixins + '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof WeavePlan that)) return false;
        return this.mixins.equals(that.mixins);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.mixins);
    }
}
