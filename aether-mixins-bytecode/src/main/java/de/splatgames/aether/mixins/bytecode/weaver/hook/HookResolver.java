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
 * and a {@link PlannedEntry} (the plan item describing what to inject or redirect) and attempts
 * to produce a {@link ResolvedHook} describing the hook's owner, name, and descriptor.
 * The returned symbol is expected to be suitable for an {@code INVOKESTATIC} call site in the target class.</p>
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Locate candidate hook methods in the mixin class (e.g., via bytecode scanning or reflection).</li>
 *   <li>Apply selection rules:
 *     <ul>
 *       <li>Match annotation kind (e.g., {@code @Inject}, {@code @Redirect}).</li>
 *       <li>Match optional identifier (ID).</li>
 *       <li>Reject ambiguous results where multiple candidates match.</li>
 *     </ul>
 *   </li>
 *   <li>Validate method signatures and descriptor compatibility for correctness.</li>
 *   <li>Report any issues via the provided diagnostics collector.</li>
 * </ul>
 *
 * <h2>Expected semantics</h2>
 * <ul>
 *   <li>Exactly one hook must be resolved per {@link PlannedEntry}.
 *       If no candidate or multiple candidates match, the implementation should report a problem
 *       and return {@link Optional#empty()}.</li>
 *   <li>
 *       INJECT hooks are always invoked via {@code INVOKESTATIC}, with the expected descriptor matching the
 *       target site and arguments.
 *   </li>
 *   <li>
 *       REDIRECT hooks must match the signature of the original method:
 *       <ul>
 *           <li>For instance calls, the receiver is passed as the first argument to the hook.</li>
 *           <li>For static calls, the signatures must match exactly.</li>
 *           <li>The return type must always match the original invocation.</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <h2>Diagnostics</h2>
 * <p>Implementations should report issues to {@code problems} instead of throwing exceptions,
 * unless a fatal condition prevents resolution (e.g., unreadable class bytes).
 * The provided {@code path} should be used as a human-readable prefix to help pinpoint the
 * location of the issue in logs.</p>
 *
 * <h2>Thread-safety</h2>
 * <p>Resolvers are not required to be thread-safe.
 * A typical usage pattern is to create a new resolver instance for each weaving run.</p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * HookResolver resolver = new AsmHookResolver(classSource);
 * Optional<ResolvedHook> rh = resolver.resolve(mixin, entry, problems, "planner/MyMixin#0");
 * rh.ifPresent(h -> {
 *     // Pass to weaver
 * });
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
