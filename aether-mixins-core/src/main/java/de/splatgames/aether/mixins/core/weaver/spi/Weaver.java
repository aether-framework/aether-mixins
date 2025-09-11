package de.splatgames.aether.mixins.core.weaver.spi;

import de.splatgames.aether.mixins.core.config.problems.ConfigProblems;
import org.jetbrains.annotations.NotNull;

/**
 * Service Provider Interface (SPI) for mixin weavers.
 *
 * <p>An implementation consumes a {@link WeaveRequest}, applies the
 * {@link de.splatgames.aether.mixins.core.plan.WeavePlan WeavePlan} to target classes
 * (obtained via the request's {@code ClassSource}), and publishes transformed classes
 * via the provided {@link ClassSink}. All diagnostics (warnings/errors) MUST be recorded
 * in the supplied {@link ConfigProblems} instance; only <em>fatal</em> conditions should
 * be surfaced as exceptions.</p>
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Resolve and read target class bytecode using {@link WeaveRequest#source()}.</li>
 *   <li>Apply planned hooks (e.g., {@code @Inject} at HEAD/TAIL, {@code @Redirect} call-site rewrites).</li>
 *   <li>Recompute/verify frames and perform minimal bytecode verification according to
 *       {@code request.runtime().getVerifyFrames()}.</li>
 *   <li>Honor safe-mode semantics (see below) and publish results using {@link WeaveRequest#sink()}.</li>
 *   <li>Populate a {@link WeaveResult} with per-class outcomes and counts.</li>
 * </ul>
 *
 * <h2>Naming</h2>
 * <p>Class names used by implementations SHOULD be internal JVM names (slash-separated),
 * for example {@code com/example/Foo}. Implementations are encouraged to validate that the
 * internal name matches the classfile's {@code this_class}.</p>
 *
 * <h2>Safe mode</h2>
 * <p>If {@code request.runtime().isSafeMode()} is {@code true}, failures for individual classes
 * MUST be treated as non-fatal: the weaver SHOULD leave the class unmodified, record diagnostics
 * (including enough context to investigate), and mark the class outcome as
 * {@link WeaveResult.Outcome#FAILED}. The session should continue for remaining classes.</p>
 *
 * <h2>Verification &amp; frames</h2>
 * <p>Implementations SHOULD adhere to the configured verification level:
 * use no verification for NONE, basic recomputation/verification for BASIC, and strict checks
 * (e.g., full frame recomputation with additional assertions) for STRICT. Exact behavior is
 * left to the implementation but MUST be documented if it deviates from these intents.</p>
 *
 * <h2>Dump-on-error</h2>
 * <p>If {@code request.runtime().isDumpClassesOnError()} is {@code true}, implementations SHOULD
 * emit original and/or transformed class bytes on failures (e.g., to a diagnostics sink).
 * The exact location and format are implementation-defined.</p>
 *
 * <h2>Determinism</h2>
 * <p>Within a single session, processing order SHOULD be deterministic (e.g., stable iteration order
 * over the plan). When multiple hooks apply to the same join point, respect the plan’s priority rules.</p>
 *
 * <h2>Threading</h2>
 * <p>Unless otherwise documented by an implementation, callers MUST treat the weaver as not thread-safe
 * and serialize calls to {@link #weave(WeaveRequest, ConfigProblems)}.</p>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@FunctionalInterface
public interface Weaver {

    /**
     * Executes a weaving session described by {@code request}.
     *
     * <p>Implementations MUST:
     * <ul>
     *   <li>Record all non-fatal diagnostics to {@code problems} (do not throw).</li>
     *   <li>Publish transformed classes via {@link WeaveRequest#sink()} using internal names as keys.</li>
     *   <li>Return a {@link WeaveResult} summarizing outcomes (transformed/skipped/failed).</li>
     * </ul>
     * Implementations MAY throw only for fatal errors that prevent continuing the session
     * (e.g., unrecoverable I/O issues or invalid global state). In safe mode, per-class failures
     * MUST be contained and reflected as {@link WeaveResult.Outcome#FAILED} entries instead of exceptions.</p>
     *
     * @param request  immutable request containing the plan, I/O adapters, and runtime options; must not be {@code null}
     * @param problems diagnostics collector for warnings and errors; must not be {@code null}
     * @return a {@link WeaveResult} summary; never {@code null}
     * @throws Exception only for fatal errors that cannot be handled according to safe-mode semantics
     */
    @NotNull
    WeaveResult weave(@NotNull final WeaveRequest request, @NotNull final ConfigProblems problems) throws Exception;
}
