package de.splatgames.aether.mixins.core.plan;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Planned mixin application with resolved targets and hook entries.
 *
 * <p>This immutable value object represents a compiled view of a mixin to be applied to one or more
 * target classes. The lists/sets exposed by accessors are unmodifiable snapshots preserving the
 * insertion order of the provided collections where applicable.</p>
 *
 * <h2>Semantics</h2>
 * <ul>
 *   <li><b>className</b>: Fully-qualified binary name of the mixin class.</li>
 *   <li><b>priority</b>: Used for deterministic ordering when multiple mixins affect the same join points.</li>
 *   <li><b>targets</b>: Binary names of classes the mixin may apply to (order preserved from input).</li>
 *   <li><b>entries</b>: Planned hook entries (inject/redirect). Order is preserved and may be
 *       relevant for deterministic weaving.</li>
 *   <li><b>groups</b>: Arbitrary labels for selection/enablement.</li>
 *   <li><b>requires</b>: Capabilities/flags that must be available to enable this mixin.</li>
 *   <li><b>conflictsWith</b>: Class names or group labels that declare conflicts for resolution.</li>
 * </ul>
 *
 * <p>All collections returned by this class are unmodifiable.</p>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class PlannedMixin {
    @NotNull
    private final String className;
    private final int priority;
    @NotNull
    private final List<@NotNull String> targets;
    @NotNull
    private final List<@NotNull PlannedEntry> entries;
    @NotNull
    private final Set<@NotNull String> groups;
    @NotNull
    private final Set<@NotNull String> requires;
    @NotNull
    private final Set<@NotNull String> conflictsWith;

    /**
     * Creates a new planned mixin.
     *
     * <p>All parameters must be non-null. Elements within the provided collections must also be non-null.
     * Defensive, unmodifiable copies are taken.</p>
     *
     * @param className     fully-qualified binary name of the mixin class, must not be {@code null}
     * @param priority      priority used for deterministic ordering
     * @param targets       list of target class binary names, must not be {@code null} and must not contain {@code null} elements
     * @param entries       list of planned entries, must not be {@code null} and must not contain {@code null} elements
     * @param groups        labels for grouping, must not be {@code null} and must not contain {@code null} elements
     * @param requires      required capabilities/flags, must not be {@code null} and must not contain {@code null} elements
     * @param conflictsWith conflicting mixin class names or group labels, must not be {@code null} and must not contain {@code null} elements
     * @throws NullPointerException if any parameter or element within the collections is {@code null}
     */
    public PlannedMixin(
            @NotNull final String className,
            final int priority,
            @NotNull final List<@NotNull String> targets,
            @NotNull final List<@NotNull PlannedEntry> entries,
            @NotNull final Set<@NotNull String> groups,
            @NotNull final Set<@NotNull String> requires,
            @NotNull final Set<@NotNull String> conflictsWith
    ) {
        this.className = Objects.requireNonNull(className, "className");
        this.priority = priority;

        Objects.requireNonNull(targets, "targets");
        for (String t : targets) Objects.requireNonNull(t, "targets element");
        this.targets = List.copyOf(targets);

        Objects.requireNonNull(entries, "entries");
        for (PlannedEntry e : entries) Objects.requireNonNull(e, "entries element");
        this.entries = List.copyOf(entries);

        Objects.requireNonNull(groups, "groups");
        for (String g : groups) Objects.requireNonNull(g, "groups element");
        this.groups = Set.copyOf(groups);

        Objects.requireNonNull(requires, "requires");
        for (String r : requires) Objects.requireNonNull(r, "requires element");
        this.requires = Set.copyOf(requires);

        Objects.requireNonNull(conflictsWith, "conflictsWith");
        for (String c : conflictsWith) Objects.requireNonNull(c, "conflictsWith element");
        this.conflictsWith = Set.copyOf(conflictsWith);
    }

    /**
     * Returns the fully-qualified binary name of the mixin class.
     *
     * @return class name, never {@code null}
     */
    @NotNull
    public String getClassName() {
        return this.className;
    }

    /**
     * Returns the priority used for deterministic ordering.
     *
     * @return priority value
     */
    public int getPriority() {
        return this.priority;
    }

    /**
     * Returns the target class names (binary names).
     *
     * @return unmodifiable list of targets, never {@code null}
     */
    @NotNull
    public List<@NotNull String> getTargets() {
        return this.targets;
    }

    /**
     * Returns the planned hook entries for this mixin.
     *
     * @return unmodifiable list of entries, never {@code null}
     */
    @NotNull
    public List<@NotNull PlannedEntry> getEntries() {
        return this.entries;
    }

    /**
     * Returns the group labels associated with this mixin.
     *
     * @return unmodifiable set of groups, never {@code null}
     */
    @NotNull
    public Set<@NotNull String> getGroups() {
        return this.groups;
    }

    /**
     * Returns the requirement flags that must be satisfied to enable this mixin.
     *
     * @return unmodifiable set of requirements, never {@code null}
     */
    @NotNull
    public Set<@NotNull String> getRequires() {
        return this.requires;
    }

    /**
     * Returns identifiers (class names or group labels) that declare conflicts with this mixin.
     *
     * @return unmodifiable set of conflict identifiers, never {@code null}
     */
    @NotNull
    public Set<@NotNull String> getConflictsWith() {
        return this.conflictsWith;
    }

    @Override
    public String toString() {
        return "PlannedMixin{" +
                "className='" + this.className + '\'' +
                ", priority=" + this.priority +
                ", targets=" + this.targets +
                ", entries=" + this.entries +
                ", groups=" + this.groups +
                ", requires=" + this.requires +
                ", conflictsWith=" + this.conflictsWith +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof PlannedMixin that)) return false;
        return this.priority == that.priority &&
                this.className.equals(that.className) &&
                this.targets.equals(that.targets) &&
                this.entries.equals(that.entries) &&
                this.groups.equals(that.groups) &&
                this.requires.equals(that.requires) &&
                this.conflictsWith.equals(that.conflictsWith);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.className, this.priority, this.targets, this.entries, this.groups, this.requires, this.conflictsWith);
    }
}
