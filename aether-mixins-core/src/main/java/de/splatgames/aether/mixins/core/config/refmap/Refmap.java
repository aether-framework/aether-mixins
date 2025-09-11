package de.splatgames.aether.mixins.core.config.refmap;

import de.splatgames.aether.mixins.core.config.problems.ConfigProblems;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Top-level refmap document describing mixin bindings resolved for a deployment.
 *
 * <p>A refmap is a data container produced by a loader (e.g., JSON parser) and consumed
 * by the mixin runtime. It consists of a schema version and a list of {@link RefMixin}
 * items.</p>
 *
 * <h2>Schema</h2>
 * <p>The {@linkplain #getSchema() schema} controls how the document is interpreted.
 * The current implementation targets {@link #CURRENT_SCHEMA} and may warn when encountering
 * other values.</p>
 *
 * <h2>Immutability of accessors</h2>
 * <p>{@link #getMixins()} returns an <em>unmodifiable snapshot</em> of the internal list.
 * Use {@link #setMixins(List)} or {@link #addMixin(RefMixin)} to modify the content.</p>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class Refmap {

    /**
     * Current supported schema version.
     */
    public static final int CURRENT_SCHEMA = 1;

    /**
     * Schema version of this refmap.
     */
    private final int schema;

    /**
     * List of mixins in this refmap. Never null; use setter to change.
     */
    @NotNull
    private List<@NotNull RefMixin> mixins = List.of();

    /**
     * Creates a refmap with the given schema version.
     *
     * @param schema schema version (typically {@link #CURRENT_SCHEMA})
     */
    public Refmap(final int schema) {
        this.schema = schema;
    }

    /**
     * Creates a refmap with the given schema and initial mixins.
     *
     * @param schema schema version
     * @param mixins initial mixins (copied defensively), must not be {@code null}
     * @return a new {@code Refmap} instance
     */
    @NotNull
    public static Refmap of(final int schema, @NotNull final List<@NotNull RefMixin> mixins) {
        final Refmap r = new Refmap(schema);
        r.setMixins(mixins);
        return r;
    }

    /**
     * Returns the schema version of this refmap.
     *
     * @return schema version as integer
     */
    public int getSchema() {
        return this.schema;
    }

    /**
     * Returns an unmodifiable snapshot of the mixins contained in this refmap.
     *
     * @return never {@code null}; may be empty
     */
    @NotNull
    public List<@NotNull RefMixin> getMixins() {
        return this.mixins;
    }

    /**
     * Replaces the current mixins with the provided list.
     *
     * <p>The list is defensively copied (unmodifiable). Null elements are rejected.</p>
     *
     * @param mixins new mixin list, must not be {@code null} and must not contain {@code null} elements
     * @throws NullPointerException if {@code mixins} or one of its elements is {@code null}
     */
    public void setMixins(@NotNull final List<@NotNull RefMixin> mixins) {
        // Validate no null elements
        for (final RefMixin m : mixins) {
            Objects.requireNonNull(m, "mixins element");
        }
        this.mixins = List.copyOf(mixins);
    }

    /**
     * Adds a single mixin to this refmap.
     *
     * @param mixin the mixin to add, must not be {@code null}
     */
    public void addMixin(@NotNull final RefMixin mixin) {
        Objects.requireNonNull(mixin, "mixin");
        final List<RefMixin> copy = new ArrayList<>(this.mixins);
        copy.add(mixin);
        this.mixins = List.copyOf(copy);
    }

    /**
     * Returns a new {@code Refmap} with the same schema and the provided mixins.
     *
     * @param mixins new mixins list, must not be {@code null}
     * @return a new {@code Refmap} instance
     */
    @NotNull
    public Refmap withMixins(@NotNull final List<@NotNull RefMixin> mixins) {
        return Refmap.of(this.schema, mixins);
    }

    /**
     * @return {@code true} if the refmap contains at least one mixin
     */
    public boolean hasMixins() {
        return !this.mixins.isEmpty();
    }

    /**
     * Performs basic validation and records diagnostics.
     *
     * <p>Checks:
     * <ul>
     *   <li>Schema is positive; warns if not equal to {@link #CURRENT_SCHEMA}.</li>
     *   <li>No {@code null} entries in the mixins list.</li>
     * </ul>
     * Further, detailed validation of individual mixins can be performed elsewhere.</p>
     *
     * @param problems diagnostics collector, must not be {@code null}
     * @param path     human-readable path prefix (e.g. {@code "refmap"}), must not be {@code null}
     */
    public void validate(@NotNull final ConfigProblems problems, @NotNull final String path) {
        if (this.schema <= 0) {
            problems.error(path + ".schema", "Schema must be a positive integer.");
        } else if (this.schema != CURRENT_SCHEMA) {
            problems.warn(path + ".schema", "Unrecognized schema '" + this.schema + "'; expected " + CURRENT_SCHEMA + ".");
        }
    }

    @Override
    public String toString() {
        return "Refmap{schema=" + this.schema + ", mixins=" + this.mixins + '}';
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) return true;
        if (!(o instanceof Refmap that)) return false;
        return this.schema == that.schema &&
                Objects.equals(this.mixins, that.mixins);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.schema, this.mixins);
    }
}
