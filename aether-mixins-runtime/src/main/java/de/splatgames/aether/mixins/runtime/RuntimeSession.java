package de.splatgames.aether.mixins.runtime;

import de.splatgames.aether.mixins.core.config.mixins.MixinsConfig;
import de.splatgames.aether.mixins.core.config.refmap.Refmap;
import de.splatgames.aether.mixins.core.config.runtime.RuntimeConfig;
import de.splatgames.aether.mixins.core.plan.WeavePlan;
import de.splatgames.aether.mixins.core.weaver.spi.WeaveResult;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of a completed Aether Mixins runtime execution.
 *
 * <h2>Purpose</h2>
 * <p>
 * {@code RuntimeSession} groups the key inputs and outputs of a single {@code RuntimeDriver#execute(...)} call:
 * the parsed YAML configuration, the set of successfully loaded refmaps, the finalized weave plan,
 * the weaver’s per-class outcomes, and a timing measurement. It is designed for reporting, debugging,
 * and programmatic post-processing (e.g., metrics, audit logs).
 * </p>
 *
 * <h2>Contents</h2>
 * <ul>
 *   <li><b>{@link #config()}:</b> The validated YAML configuration used to drive selection and weaving.</li>
 *   <li><b>{@link #refmaps()}:</b> The set of refmaps (only those that loaded successfully).</li>
 *   <li><b>{@link #plan()}:</b> The completed weave plan (after selection and conflict resolution).</li>
 *   <li><b>{@link #result()}:</b> The weaver’s outcome summary (transformed/skipped/failed, per-class entries).</li>
 *   <li><b>{@link #durationNanos()}:</b> Wall-clock time from the start of execution to completion, in nanoseconds.</li>
 * </ul>
 *
 * <h2>Access patterns</h2>
 * <ul>
 *   <li>Use {@link #runtime()} to fetch runtime options directly from the loaded config.</li>
 *   <li>Iterate {@link #result()} to enumerate per-class outcomes for reporting.</li>
 *   <li>Inspect {@link #plan()} to understand which mixins/entries were selected and why.</li>
 * </ul>
 *
 * <h2>Timing</h2>
 * <p>
 * {@link #durationNanos()} reports the total time spent in configuration loading, refmap loading,
 * planning, and weaving for this session. Convert with {@code TimeUnit} as needed, e.g.:
 * </p>
 * <pre>{@code
 * long ms = TimeUnit.NANOSECONDS.toMillis(session.durationNanos());
 * }</pre>
 *
 * <h2>Thread-safety</h2>
 * <p>
 * This record is immutable and thread-safe to publish after construction. The referenced objects
 * ({@link MixinsConfig}, {@link Refmap}, {@link WeavePlan}, {@link WeaveResult}) are expected to
 * be treated as immutable snapshots by their respective modules.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * RuntimeSession session = driver.execute(yamlPath, options, source, sink, weaver, problems);
 * System.out.println("Transformed: " + session.result().transformed());
 * }</pre>
 *
 * @param config        loaded YAML configuration used for this run; never {@code null}
 * @param refmaps       list of successfully loaded refmaps; never {@code null} (may be empty)
 * @param plan          finalized weave plan; never {@code null}
 * @param result        weaver result summary; never {@code null}
 * @param durationNanos total elapsed time in nanoseconds for this execution
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record RuntimeSession(
        @NotNull MixinsConfig config,
        @NotNull List<@NotNull Refmap> refmaps,
        @NotNull WeavePlan plan,
        @NotNull WeaveResult result,
        long durationNanos
) {
    /**
     * Canonical constructor enforcing non-null invariants for all reference components.
     *
     * @throws NullPointerException if any reference component is {@code null}
     */
    public RuntimeSession {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(refmaps, "refmaps");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(result, "result");
    }

    /**
     * Convenience accessor for the runtime options embedded in {@link #config()}.
     *
     * @return the {@link RuntimeConfig} used by this session; never {@code null}
     */
    public @NotNull RuntimeConfig runtime() {
        return this.config.getRuntime();
    }
}
