package de.splatgames.aether.mixins.core.config.refmap;

import de.splatgames.aether.mixins.core.config.problems.ConfigProblems;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Describes a single mixin declaration within a refmap document.
 *
 * <p>A {@code RefMixin} binds a mixin class (by its fully qualified binary name)
 * to one or more target classes and lists its hook {@link RefEntry entries}.
 * Optional metadata fields allow grouping, requirements, and conflict hints.</p>
 *
 * <h2>Required fields</h2>
 * <ul>
 *   <li>{@link #getClassName() className} — fully qualified binary name (e.g. {@code com.example.ServiceMixin})</li>
 *   <li>{@link #getTargets() targets} — non-empty list of binary names (e.g. {@code com.example.Service})</li>
 *   <li>{@link #getEntries() entries} — non-empty list of hook bindings</li>
 * </ul>
 *
 * <h2>Optional fields</h2>
 * <ul>
 *   <li>{@link #getPriority() priority} — application order (default {@code 0})</li>
 *   <li>{@link #getGroups() groups} — logical labels for selection</li>
 *   <li>{@link #getRequires() requires} — external requirements (format interpreted by configuration layer)</li>
 *   <li>{@link #getConflictsWith() conflictsWith} — known conflicts (by mixin class name or group)</li>
 * </ul>
 *
 * <p>All list setters defensively copy the input and reject {@code null} elements.</p>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class RefMixin {

    /**
     * Fully qualified binary name of the mixin class.
     */
    @Nullable
    private String className;

    /**
     * Target classes (binary names).
     */
    @NotNull
    private List<@NotNull String> targets = List.of();

    /**
     * Priority used to determine application order. Higher = later application (see runtime docs).
     */
    private int priority = 0;

    /**
     * Hook entries (injects/redirects).
     */
    @NotNull
    private List<@NotNull RefEntry> entries = List.of();

    /**
     * Optional grouping labels.
     */
    @NotNull
    private List<@NotNull String> groups = List.of();

    /**
     * External requirements (format interpreted by configuration layer).
     */
    @NotNull
    private List<@NotNull String> requires = List.of();

    /**
     * Known conflicts (by mixin class name or group label).
     */
    @NotNull
    private List<@NotNull String> conflictsWith = List.of();

    /**
     * Creates an empty {@code RefMixin}. Values must be set via setters.
     */
    public RefMixin() {
    }

    /**
     * Convenience factory for a fully specified {@code RefMixin}.
     *
     * @param className mixin class binary name, must not be {@code null}
     * @param targets   non-empty list of target class binary names, must not be {@code null}
     * @param entries   non-empty list of entries, must not be {@code null}
     * @return a new {@code RefMixin} instance
     */
    @NotNull
    public static RefMixin of(@NotNull final String className,
                              @NotNull final List<@NotNull String> targets,
                              @NotNull final List<@NotNull RefEntry> entries) {
        final RefMixin m = new RefMixin();
        m.setClassName(className);
        m.setTargets(targets);
        m.setEntries(entries);
        return m;
    }

    /**
     * Checks if a string is null, empty, or only whitespace.
     *
     * @param s string to check, may be {@code null}
     * @return {@code true} if {@code s} is {@code null}, empty, or only whitespace
     */
    private static boolean isBlank(@Nullable final String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * Validates this mixin and records diagnostics.
     *
     * <p>Checks:
     * <ul>
     *   <li>{@code className} present and non-blank</li>
     *   <li>{@code targets} non-empty and contain no blank entries</li>
     *   <li>{@code entries} non-empty and each entry validates</li>
     * </ul>
     *
     * @param problems diagnostics collector, must not be {@code null}
     * @param path     path prefix for messages (e.g. {@code "mixins[0]"}), must not be {@code null}
     */
    public void validate(@NotNull final ConfigProblems problems, @NotNull final String path) {
        if (isBlank(this.className)) {
            problems.error(path + ".class", "Mixin class name is required (binary name).");
        }

        if (this.targets.isEmpty()) {
            problems.error(path + ".targets", "At least one target class is required.");
        } else {
            for (int i = 0; i < this.targets.size(); i++) {
                final String t = this.targets.get(i);
                if (isBlank(t)) {
                    problems.error(path + ".targets[" + i + "]", "Target class name must not be blank.");
                }
            }
        }

        if (this.entries.isEmpty()) {
            problems.error(path + ".entries", "At least one entry (inject/redirect) is required.");
        } else {
            for (int i = 0; i < this.entries.size(); i++) {
                final RefEntry e = this.entries.get(i);
                if (e == null) {
                    problems.error(path + ".entries[" + i + "]", "Null entry is not allowed.");
                } else {
                    e.validate(problems, path + ".entries[" + i + "]");
                }
            }
        }
    }

    /**
     * Checks if there is at least one target.
     *
     * @return {@code true} if there is at least one target.
     */
    public boolean hasTargets() {
        return !this.targets.isEmpty();
    }

    /**
     * Checks if there is at least one entry.
     *
     * @return {@code true} if there is at least one entry.
     */
    public boolean hasEntries() {
        return !this.entries.isEmpty();
    }

    /**
     * Adds a single target class name.
     *
     * @param target binary class name, must not be {@code null}
     */
    public void addTarget(@NotNull final String target) {
        Objects.requireNonNull(target, "target");
        final List<String> copy = new ArrayList<>(this.targets);
        copy.add(target);
        this.targets = List.copyOf(copy);
    }

    /**
     * Adds a single entry.
     *
     * @param entry non-null {@link RefEntry}
     */
    public void addEntry(@NotNull final RefEntry entry) {
        Objects.requireNonNull(entry, "entry");
        final List<RefEntry> copy = new ArrayList<>(this.entries);
        copy.add(entry);
        this.entries = List.copyOf(copy);
    }

    /**
     * Returns the mixin class name (binary name).
     *
     * @return class name, may be {@code null} if not set yet
     */
    @Nullable
    public String getClassName() {
        return this.className;
    }

    /**
     * Sets the mixin class name (binary name).
     *
     * @param className class name; may be {@code null} (loader may fill later)
     */
    public void setClassName(@Nullable final String className) {
        this.className = className;
    }

    /**
     * Returns an unmodifiable snapshot of the targets list.
     *
     * @return never {@code null}; may be empty
     */
    @NotNull
    public List<@NotNull String> getTargets() {
        return this.targets;
    }

    /**
     * Replaces the targets list (defensive copy, no null elements allowed).
     *
     * @param targets new targets list, must not be {@code null} and must not contain {@code null}
     * @throws NullPointerException if the list or an element is {@code null}
     */
    public void setTargets(@NotNull final List<@NotNull String> targets) {
        for (final String s : targets) Objects.requireNonNull(s, "targets element");
        this.targets = List.copyOf(targets);
    }

    /**
     * Returns the priority (no bounds enforced here).
     *
     * @return the priority (default {@code 0})
     */
    public int getPriority() {
        return this.priority;
    }

    /**
     * Sets the priority (no bounds enforced here).
     *
     * @param priority new priority value
     */
    public void setPriority(final int priority) {
        this.priority = priority;
    }

    /**
     * Returns an unmodifiable snapshot of the entries list.
     *
     * @return never {@code null}; may be empty
     */
    @NotNull
    public List<@NotNull RefEntry> getEntries() {
        return this.entries;
    }

    /**
     * Replaces the entries list (defensive copy, no null elements allowed).
     *
     * @param entries new entries list, must not be {@code null} and must not contain {@code null}
     * @throws NullPointerException if the list or an element is {@code null}
     */
    public void setEntries(@NotNull final List<@NotNull RefEntry> entries) {
        for (final RefEntry e : entries) Objects.requireNonNull(e, "entries element");
        this.entries = List.copyOf(entries);
    }

    /**
     * Returns groups (logical labels for selection).
     *
     * @return groups (never {@code null})
     */
    @NotNull
    public List<@NotNull String> getGroups() {
        return this.groups;
    }

    /**
     * Sets groups (defensive copy, no null elements).
     *
     * @param groups group labels, must not be {@code null}
     */
    public void setGroups(@NotNull final List<@NotNull String> groups) {
        for (final String s : groups) Objects.requireNonNull(s, "groups element");
        this.groups = List.copyOf(groups);
    }

    /**
     * Returns requirements (format interpreted by configuration layer).
     *
     * @return requirements (never {@code null})
     */
    @NotNull
    public List<@NotNull String> getRequires() {
        return this.requires;
    }

    /**
     * Sets requirements (defensive copy, no null elements).
     *
     * @param requires requirements, must not be {@code null}
     */
    public void setRequires(@NotNull final List<@NotNull String> requires) {
        for (final String s : requires) Objects.requireNonNull(s, "requires element");
        this.requires = List.copyOf(requires);
    }

    /**
     * Returns conflict identifiers (by mixin class name or group).
     *
     * @return conflict identifiers (never {@code null})
     */
    @NotNull
    public List<@NotNull String> getConflictsWith() {
        return this.conflictsWith;
    }

    /**
     * Sets conflict identifiers (defensive copy, no null elements).
     *
     * @param conflictsWith conflicts, must not be {@code null}
     */
    public void setConflictsWith(@NotNull final List<@NotNull String> conflictsWith) {
        for (final String s : conflictsWith) Objects.requireNonNull(s, "conflictsWith element");
        this.conflictsWith = List.copyOf(conflictsWith);
    }

    @Override
    public String toString() {
        return "RefMixin{" +
                "className='" + this.className + '\'' +
                ", targets=" + this.targets +
                ", priority=" + this.priority +
                ", entries=" + this.entries +
                ", groups=" + this.groups +
                ", requires=" + this.requires +
                ", conflictsWith=" + this.conflictsWith +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof RefMixin that)) return false;
        return this.priority == that.priority &&
                Objects.equals(this.className, that.className) &&
                Objects.equals(this.targets, that.targets) &&
                Objects.equals(this.entries, that.entries) &&
                Objects.equals(this.groups, that.groups) &&
                Objects.equals(this.requires, that.requires) &&
                Objects.equals(this.conflictsWith, that.conflictsWith);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.className, this.targets, this.priority,
                this.entries, this.groups, this.requires, this.conflictsWith);
    }
}
