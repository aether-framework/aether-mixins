package de.splatgames.aether.mixins.bytecode.weaver.hook;

import de.splatgames.aether.mixins.core.config.problems.ConfigProblems;
import de.splatgames.aether.mixins.core.plan.PlannedEntry;
import de.splatgames.aether.mixins.core.plan.PlannedMixin;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Resolves a planned hook declaration into a concrete method symbol that can be invoked
 * by the bytecode weaver.
 *
 * <p>A resolver takes a {@link PlannedMixin} (the mixin class that contains hook methods)
 * and a {@link PlannedEntry} (the plan item describing what to inject/redirect) and attempts
 * to produce a {@link ResolvedHook} describing the hook's owner/name/descriptor. The returned
 * symbol is expected to be suitable for an {@code INVOKESTATIC} call site in the target class.</p>
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Locate candidate hook method(s) in the mixin class (e.g., via bytecode scanning or reflection).</li>
 *   <li>Apply selection rules (e.g., match annotation kind, match optional identifier, disallow ambiguity).</li>
 *   <li>Optionally validate basic signature constraints and emit diagnostics when expectations are violated.</li>
 * </ul>
 *
 * <h2>Expected semantics</h2>
 * <ul>
 *   <li>Exactly one hook must be resolved per {@link PlannedEntry}. If no candidate or multiple candidates
 *       match, the implementation should report a problem and return {@link Optional#empty()}.</li>
 *   <li>For MVP compatibility with the default weaver:
 *     <ul>
 *       <li>INJECT hooks are expected to be <em>static</em> with descriptor {@code ()V}.</li>
 *       <li>REDIRECT hooks are expected to be <em>static</em> and descriptor-compatible with the original invoke
 *           (prepend receiver type for instance calls; same return type).</li>
 *     </ul>
 *     Implementations may enforce these requirements strictly or report them as warnings/errors in {@code problems}.
 *   </li>
 * </ul>
 *
 * <h2>Diagnostics</h2>
 * <p>Implementations should prefer reporting issues to {@code problems} rather than throwing exceptions,
 * unless a fatal condition prevents resolution (e.g., unreadable class bytes). Use the provided {@code path}
 * as a human-readable prefix that helps pinpoint the failure location in logs.</p>
 *
 * <h2>Thread-safety</h2>
 * <p>Resolvers are not required to be thread-safe. A typical usage pattern is one resolver instance per weaving run.</p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * HookResolver resolver = new AsmHookResolver(classSource);
 * Optional<ResolvedHook> rh = resolver.resolve(mixin, entry, problems, "planner/MyMixin#0");
 * rh.ifPresent(h -> * pass to weaver * );
 * }</pre>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public interface HookResolver {

    /**
     * Resolves a single hook method for the given planned entry within the given mixin class.
     *
     * <p>On success, the returned {@link ResolvedHook} identifies the hook method by internal owner name,
     * method name, and JVM descriptor. On failure (no match, ambiguity, or validation error), the
     * implementation should report an appropriate message to {@code problems} and return
     * {@link Optional#empty()}.</p>
     *
     * @param mixin    the planned mixin whose class contains the hook; never {@code null}
     * @param entry    the planned entry describing the injection/redirect to resolve; never {@code null}
     * @param problems diagnostics collector to receive warnings and errors; never {@code null}
     * @param path     human-readable diagnostics prefix (e.g., {@code "resolve/com/example/Target/MyMixin:enter"});
     *                 never {@code null}
     * @return a non-empty {@link Optional} with the resolved hook symbol if resolution succeeded;
     *         otherwise {@link Optional#empty()}
     */
    @NotNull
    Optional<ResolvedHook> resolve(
            @NotNull final PlannedMixin mixin,
            @NotNull final PlannedEntry entry,
            @NotNull final ConfigProblems problems,
            @NotNull final String path
    );
}
