package de.splatgames.aether.mixins.core.plan;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Selection rules used by the planner to include/exclude mixins.
 *
 * <p><b>Semantics (MVP):</b></p>
 * <ul>
 *   <li><b>Groups:</b> If {@code onlyGroups} is non-empty, a mixin must intersect it to be included.
 *       Any group in {@code disabledGroups} excludes a mixin.</li>
 *   <li><b>Requires:</b> A mixin's {@code requires} must be a subset of {@code availableRequirements}.</li>
 * </ul>
 *
 * <p>Instances are immutable; returned sets are unmodifiable snapshots with insertion order preserved.</p>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class SelectionOptions {
    @NotNull
    private final Set<@NotNull String> onlyGroups;
    @NotNull
    private final Set<@NotNull String> disabledGroups;
    @NotNull
    private final Set<@NotNull String> availableRequirements;

    /**
     * Creates a new instance with the provided rule sets.
     *
     * <p>All parameters must be non-null; set elements must also be non-null. Defensive,
     * unmodifiable copies are taken.</p>
     *
     * @param onlyGroups            groups that must intersect for inclusion, must not be {@code null}
     * @param disabledGroups        groups that exclude a mixin when matched, must not be {@code null}
     * @param availableRequirements requirement flags available in the environment, must not be {@code null}
     * @throws NullPointerException if any parameter or element is {@code null}
     */
    private SelectionOptions(
            @NotNull final Set<@NotNull String> onlyGroups,
            @NotNull final Set<@NotNull String> disabledGroups,
            @NotNull final Set<@NotNull String> availableRequirements
    ) {
        Objects.requireNonNull(onlyGroups, "onlyGroups");
        Objects.requireNonNull(disabledGroups, "disabledGroups");
        Objects.requireNonNull(availableRequirements, "availableRequirements");

        for (String g : onlyGroups) Objects.requireNonNull(g, "onlyGroups element");
        for (String g : disabledGroups) Objects.requireNonNull(g, "disabledGroups element");
        for (String r : availableRequirements) Objects.requireNonNull(r, "availableRequirements element");

        this.onlyGroups = Collections.unmodifiableSet(new LinkedHashSet<>(onlyGroups));
        this.disabledGroups = Collections.unmodifiableSet(new LinkedHashSet<>(disabledGroups));
        this.availableRequirements = Collections.unmodifiableSet(new LinkedHashSet<>(availableRequirements));
    }

    /**
     * Returns an instance with no restrictions (all sets empty).
     *
     * @return empty selection options, never {@code null}
     */
    @NotNull
    public static SelectionOptions empty() {
        return new SelectionOptions(Set.of(), Set.of(), Set.of());
    }

    /**
     * Creates a new instance with the given rule sets.
     *
     * @param onlyGroups            groups that must intersect for inclusion, must not be {@code null}
     * @param disabledGroups        groups that exclude a mixin when matched, must not be {@code null}
     * @param availableRequirements requirement flags available in the environment, must not be {@code null}
     * @return selection options, never {@code null}
     */
    @NotNull
    public static SelectionOptions of(
            @NotNull final Set<@NotNull String> onlyGroups,
            @NotNull final Set<@NotNull String> disabledGroups,
            @NotNull final Set<@NotNull String> availableRequirements
    ) {
        return new SelectionOptions(onlyGroups, disabledGroups, availableRequirements);
    }

    /**
     * Groups that a mixin must intersect to be included (if non-empty).
     *
     * @return unmodifiable set of groups, never {@code null}
     */
    @NotNull
    public Set<@NotNull String> getOnlyGroups() {
        return this.onlyGroups;
    }

    /**
     * Groups that exclude a mixin when matched.
     *
     * @return unmodifiable set of groups, never {@code null}
     */
    @NotNull
    public Set<@NotNull String> getDisabledGroups() {
        return this.disabledGroups;
    }

    /**
     * Requirement flags available in the environment.
     *
     * @return unmodifiable set of requirement flags, never {@code null}
     */
    @NotNull
    public Set<@NotNull String> getAvailableRequirements() {
        return this.availableRequirements;
    }

    @Override
    public String toString() {
        return "SelectionOptions{" +
                "onlyGroups=" + onlyGroups +
                ", disabledGroups=" + disabledGroups +
                ", availableRequirements=" + availableRequirements +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof SelectionOptions that)) return false;
        return this.onlyGroups.equals(that.onlyGroups)
                && this.disabledGroups.equals(that.disabledGroups)
                && this.availableRequirements.equals(that.availableRequirements);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.onlyGroups, this.disabledGroups, this.availableRequirements);
    }
}
