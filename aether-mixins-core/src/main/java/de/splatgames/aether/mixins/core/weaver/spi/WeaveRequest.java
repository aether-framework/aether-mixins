package de.splatgames.aether.mixins.core.weaver.spi;

import de.splatgames.aether.mixins.core.config.runtime.RuntimeConfig;
import de.splatgames.aether.mixins.core.plan.WeavePlan;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Immutable request describing a single weaving session.
 *
 * <p>A {@code WeaveRequest} bundles the execution inputs required by a {@link Weaver}:
 * an immutable {@link WeavePlan}, a {@link ClassSource} to read bytecode from,
 * a {@link ClassSink} to publish transformed classes to, and the {@link RuntimeConfig}
 * that governs safety/verification behaviors.</p>
 *
 * <h2>Immutability &amp; thread-safety</h2>
 * <ul>
 *   <li>This type is <em>shallowly immutable</em>: all fields are {@code final} and set once
 *       during construction. The referenced collaborators ({@link WeavePlan}, {@link ClassSource},
 *       {@link ClassSink}, {@link RuntimeConfig}) are not defensively copied.</li>
 *   <li>Implementations of the collaborators may or may not be thread-safe. Unless otherwise
 *       documented, callers SHOULD treat a {@code WeaveRequest} as confined to a single-threaded
 *       weaving session.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * WeaveRequest req = WeaveRequest.of(plan, source, sink, runtimeConfig);
 * WeaveResult  res = weaver.weave(req, problems);
 * }</pre>
 *
 * <h2>Validation</h2>
 * <p>The factory enforces non-null requirements via {@link Objects#requireNonNull(Object, String)}.
 * Additional semantic validation (e.g., verifying that the {@link ClassSource} can actually
 * resolve the plan's targets) is out of scope for this DTO and belongs to the {@link Weaver}.</p>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class WeaveRequest {

    @NotNull
    private final WeavePlan plan;
    @NotNull
    private final ClassSource source;
    @NotNull
    private final ClassSink sink;
    @NotNull
    private final RuntimeConfig runtime;

    /**
     * Internal constructor establishing the immutability contract.
     *
     * <p>All parameters are required and validated for non-null. Use
     * {@link #of(WeavePlan, ClassSource, ClassSink, RuntimeConfig)} to create instances.</p>
     *
     * @param plan    the immutable weave plan, must not be {@code null}
     * @param source  the class source, must not be {@code null}
     * @param sink    the class sink, must not be {@code null}
     * @param runtime runtime configuration, must not be {@code null}
     */
    private WeaveRequest(
            @NotNull final WeavePlan plan,
            @NotNull final ClassSource source,
            @NotNull final ClassSink sink,
            @NotNull final RuntimeConfig runtime
    ) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.source = Objects.requireNonNull(source, "source");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    /**
     * Creates a new request.
     *
     * @param plan    immutable weave plan, must not be {@code null}
     * @param source  class source, must not be {@code null}
     * @param sink    class sink, must not be {@code null}
     * @param runtime runtime options, must not be {@code null}
     * @return new request instance, never {@code null}
     */
    @NotNull
    public static WeaveRequest of(
            @NotNull final WeavePlan plan,
            @NotNull final ClassSource source,
            @NotNull final ClassSink sink,
            @NotNull final RuntimeConfig runtime
    ) {
        return new WeaveRequest(plan, source, sink, runtime);
    }

    /**
     * The immutable weave plan to execute.
     *
     * @return plan, never {@code null}
     */
    @NotNull
    public WeavePlan plan() {
        return this.plan;
    }

    /**
     * The bytecode source used to read classes.
     *
     * @return class source, never {@code null}
     */
    @NotNull
    public ClassSource source() {
        return this.source;
    }

    /**
     * The bytecode sink used to publish transformed classes.
     *
     * @return class sink, never {@code null}
     */
    @NotNull
    public ClassSink sink() {
        return this.sink;
    }

    /**
     * Runtime options (safe mode, frame verification, dump-on-error).
     *
     * @return runtime config, never {@code null}
     */
    @NotNull
    public RuntimeConfig runtime() {
        return this.runtime;
    }
}
