package de.splatgames.aether.mixins.core.config.mixins;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Represents a named collection of mixins and their associated refmap files.
 *
 * <p>A {@code MixinSet} groups together related mixins that are described
 * by one or more external refmap files. These files contain the resolved symbolic
 * bindings for mixin hooks, such as owners, method names, and JVM descriptors.</p>
 *
 * <h2>Usage</h2>
 * <p>Mixin sets are typically defined in the top-level configuration
 * and loaded by the runtime when initializing the mixin system.</p>
 *
 * <h3>Example (YAML)</h3>
 * <blockquote><pre>{@code
 * mixins:
 *   - name: core-service
 *     files:
 *       - mixins/service.refmap.json
 *
 *   - name: extensions
 *     files:
 *       - mixins/extensions.refmap.json
 * }</pre></blockquote>
 *
 * @author Erik Pförtner
 * @see MixinsConfig
 * @since 0.1.0
 */
public final class MixinSet {

    /**
     * Human-readable name of this mixin set.
     *
     * <p>The name is primarily used for diagnostics, grouping, and referencing
     * within external configuration files.</p>
     */
    private String name;

    /**
     * List of refmap files that define the symbolic bindings for this mixin set.
     *
     * <p>Each file is typically specified relative to the application's working
     * directory or may be loaded from the classpath, depending on the loader
     * implementation.</p>
     */
    private List<String> files;

    /**
     * List of fully qualified mixin class names included in this set.
     */
    private List<String> classes;

    /**
     * Returns the display name of this mixin set.
     *
     * @return the display name, never {@code null} once set
     */
    @NotNull
    public String getName() {
        return this.name;
    }

    /**
     * Sets the display name of this mixin set.
     *
     * @param name the human-readable name to use, must not be {@code null}
     */
    public void setName(@NotNull final String name) {
        this.name = name;
    }

    /**
     * Returns the list of refmap files associated with this mixin set.
     *
     * <p>These files define how mixins are applied to their respective
     * target classes and hooks.</p>
     *
     * @return list of refmap file locations, or {@code null} if none are defined
     */
    @Nullable
    public List<String> getFiles() {
        return this.files;
    }

    /**
     * Sets the list of refmap file locations for this mixin set.
     *
     * @param files list of file paths or classpath resources,
     *              may be {@code null} if no files are configured
     */
    public void setFiles(@Nullable final List<String> files) {
        this.files = (files != null) ? List.copyOf(files) : List.of();
    }

    /**
     * Returns the list of fully qualified mixin class names included in this set.
     *
     * @return list of mixin class names, or {@code null} if none are defined
     */
    @Nullable
    public List<String> getClasses() {
        return this.classes;
    }

    /**
     * Sets the list of fully qualified mixin class names for this mixin set.
     *
     * @param classes list of mixin class names, may be {@code null} if none are configured
     */
    public void setClasses(@Nullable final List<String> classes) {
        this.classes = (classes != null) ? List.copyOf(classes) : List.of();
    }

    /**
     * Returns a string representation of this mixin set for debugging purposes.
     *
     * @return a string representation containing the name and associated files
     */
    @Override
    public String toString() {
        return "MixinSet{name='" + this.name + "', files=" + this.files + ", classes=" + this.classes + '}';
    }

    /**
     * Computes a hash code for this mixin set based on its name and files.
     *
     * @return hash code representing this mixin set
     */
    @Override
    public int hashCode() {
        return Objects.hash(this.name, this.files, this.classes);
    }

    /**
     * Compares this mixin set to another object for equality.
     *
     * <p>Two {@code MixinSet} instances are considered equal if both their
     * {@link #getName() names} and {@link #getFiles() file lists} are equal.</p>
     *
     * @param o the object to compare with
     * @return {@code true} if both objects are equal, {@code false} otherwise
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof MixinSet that)) return false;
        return Objects.equals(this.name, that.name) &&
                Objects.equals(this.files, that.files)
                && Objects.equals(this.classes, that.classes);
    }
}
