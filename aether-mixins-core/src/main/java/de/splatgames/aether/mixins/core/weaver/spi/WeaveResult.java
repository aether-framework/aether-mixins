package de.splatgames.aether.mixins.core.weaver.spi;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * Result summary of a weaving session.
 *
 * <p>A {@code WeaveResult} captures per-class outcomes together with simple aggregates
 * (transformed/skipped/failed) and total elapsed time. The list of {@link Entry entries}
 * reflects the processing order used by the weaver. Diagnostics (warnings/errors) are
 * expected to be recorded separately via the caller-provided problems collector.</p>
 *
 * <h2>Immutability &amp; thread-safety</h2>
 * <ul>
 *   <li>This type is <em>shallowly immutable</em>: the {@link #entries()} list is defensively
 *       copied and unmodifiable; counters and duration are {@code final} primitives.</li>
 *   <li>Callers may safely retain and publish instances across threads.</li>
 * </ul>
 *
 * <h2>Consistency</h2>
 * <p>This type does not enforce consistency between the aggregate counters and the content
 * of {@link #entries()}. Implementations creating a {@code WeaveResult} should ensure
 * the counts accurately reflect the per-class outcomes.</p>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class WeaveResult {

    @NotNull
    private final List<@NotNull Entry> entries;
    private final int transformed;
    private final int skipped;
    private final int failed;
    private final long durationNanos;

    /**
     * Internal constructor establishing the immutability contract.
     *
     * <p>The {@code entries} list is validated for non-null and absence of null elements,
     * and then defensively copied. Counters and duration are stored as provided.</p>
     *
     * @param entries       per-class outcomes, must not be {@code null} and contain no {@code null} elements
     * @param transformed   count of transformed classes
     * @param skipped       count of skipped classes
     * @param failed        count of failed classes
     * @param durationNanos total elapsed time in nanoseconds
     */
    private WeaveResult(
            @NotNull final List<@NotNull Entry> entries,
            final int transformed,
            final int skipped,
            final int failed,
            final long durationNanos
    ) {
        for (Entry e : Objects.requireNonNull(entries, "entries")) {
            Objects.requireNonNull(e, "entry");
        }
        this.entries = List.copyOf(entries);
        this.transformed = transformed;
        this.skipped = skipped;
        this.failed = failed;
        this.durationNanos = durationNanos;
    }

    /**
     * Creates a result summary.
     *
     * <p>Note: This factory does not recompute aggregates from {@code entries}. Pass counters that
     * reflect the actual outcomes if you rely on aggregate accuracy.</p>
     *
     * @param entries       per-class outcomes, must not be {@code null} and contain no {@code null} elements
     * @param transformed   count of transformed classes
     * @param skipped       count of skipped classes
     * @param failed        count of failed classes
     * @param durationNanos total elapsed time in nanoseconds
     * @return result, never {@code null}
     */
    @NotNull
    public static WeaveResult of(
            @NotNull final List<@NotNull Entry> entries,
            final int transformed,
            final int skipped,
            final int failed,
            final long durationNanos
    ) {
        return new WeaveResult(entries, transformed, skipped, failed, durationNanos);
    }

    /**
     * Per-class outcomes in processing order.
     *
     * @return unmodifiable list of entries, never {@code null}
     */
    @NotNull
    public List<@NotNull Entry> entries() {
        return this.entries;
    }

    /**
     * Count of transformed classes.
     *
     * @return number of classes that were transformed
     */
    public int transformed() {
        return this.transformed;
    }

    /**
     * Count of skipped classes.
     *
     * @return number of classes examined but left unchanged
     */
    public int skipped() {
        return this.skipped;
    }

    /**
     * Count of failed classes.
     *
     * @return number of classes that failed to transform
     */
    public int failed() {
        return this.failed;
    }

    /**
     * Elapsed time in nanoseconds for the session.
     *
     * <p>This duration is implementation-defined (e.g., wall clock vs. CPU time)
     * and should be documented by the producing {@code Weaver}.</p>
     *
     * @return total elapsed time in nanoseconds
     */
    public long durationNanos() {
        return this.durationNanos;
    }

    @Override
    public String toString() {
        return "WeaveResult{transformed=" + transformed +
                ", skipped=" + skipped +
                ", failed=" + failed +
                ", entries=" + entries.size() +
                ", durationNanos=" + durationNanos + '}';
    }

    /**
     * Outcome kind for a processed class.
     */
    public enum Outcome {
        /**
         * Class was transformed (at least one bytecode change).
         */
        TRANSFORMED,
        /**
         * Class was examined but no changes were required.
         */
        SKIPPED,
        /**
         * Class failed to transform (left unmodified in safe mode).
         */
        FAILED
    }

    /**
     * A single class outcome.
     *
     * <p>Represents the processing result for one class. The {@code internalName}
     * uses the JVM internal form (slash-separated).</p>
     *
     * @param internalName internal JVM name, never {@code null}
     * @param outcome      outcome kind, never {@code null}
     */
    public record Entry(@NotNull String internalName, @NotNull Outcome outcome) {
        public Entry {
            Objects.requireNonNull(internalName, "internalName");
            Objects.requireNonNull(outcome, "outcome");
        }
    }
}
