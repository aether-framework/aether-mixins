package de.splatgames.aether.mixins.core.plan;

import de.splatgames.aether.mixins.core.config.problems.ConfigProblems;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves declared conflicts among planned mixins.
 *
 * <p><b>Policy:</b></p>
 * <ul>
 *   <li>A conflict occurs if one mixin's {@code conflictsWith} contains the other mixin's class name
 *       or intersects any of its groups.</li>
 *   <li>Resolution keeps the mixin with the higher {@link PlannedMixin#getPriority() priority}.
 *       On tie, the lexicographically smaller {@link PlannedMixin#getClassName()} wins.</li>
 *   <li>A diagnostic is recorded for each dropped mixin.</li>
 * </ul>
 *
 * <p><b>Determinism:</b> Input order is preserved where decisions are equal (stable behavior via {@link LinkedHashMap}).</p>
 *
 * <p><b>Scope:</b> Only mixins that affect at least one common target class are compared against each other.</p>
 */
public final class ConflictResolver {

    /**
     * Determines whether {@code a} declares a conflict against {@code b}.
     *
     * <p>A conflict is detected if:</p>
     * <ul>
     *   <li>{@code a.conflictsWith} explicitly contains {@code b.className}, or</li>
     *   <li>{@code a.conflictsWith} contains any label present in {@code b.groups}.</li>
     * </ul>
     *
     * @param a left mixin, must not be {@code null}
     * @param b right mixin, must not be {@code null}
     * @return {@code true} if a conflict is declared by {@code a} against {@code b}; {@code false} otherwise
     */
    private static boolean conflicts(@NotNull final PlannedMixin a, @NotNull final PlannedMixin b) {
        // class-name based conflict
        if (a.getConflictsWith().contains(b.getClassName())) return true;

        // group-based conflict
        for (final String g : b.getGroups()) {
            if (a.getConflictsWith().contains(g)) return true;
        }
        return false;
    }

    /**
     * Picks the survivor between two conflicting mixins, using deterministic rules.
     *
     * <p>Rules:</p>
     * <ol>
     *   <li>Higher {@link PlannedMixin#getPriority() priority} wins.</li>
     *   <li>On tie, the lexicographically smaller {@link PlannedMixin#getClassName()} wins.</li>
     * </ol>
     *
     * @param a first mixin, must not be {@code null}
     * @param b second mixin, must not be {@code null}
     * @return the chosen survivor, never {@code null}
     */
    @NotNull
    private static PlannedMixin pick(@NotNull final PlannedMixin a, @NotNull final PlannedMixin b) {
        if (a.getPriority() != b.getPriority()) {
            return (a.getPriority() > b.getPriority()) ? a : b;
        }
        return (a.getClassName().compareTo(b.getClassName()) <= 0) ? a : b;
    }

    /**
     * Applies conflict resolution and returns a filtered plan.
     *
     * <p>Conflicts are computed deterministically. When a conflict is detected, the loser is removed.
     * The decision for a conflicting pair is based on priority and then class name.</p>
     *
     * <p>To reduce unnecessary comparisons, candidates are grouped per target class and only mixins
     * that affect at least one common target are checked against each other.</p>
     *
     * @param plan     initial plan, must not be {@code null}
     * @param problems diagnostics collector, must not be {@code null}
     * @return filtered plan with conflicts removed, never {@code null}
     */
    @NotNull
    public WeavePlan resolve(@NotNull final WeavePlan plan, @NotNull final ConfigProblems problems) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(problems, "problems");

        final List<PlannedMixin> mixins = new ArrayList<>(plan.getMixins());

        // Build quick lookup: class -> mixin (first occurrence wins, preserves input order).
        final Map<String, PlannedMixin> byClass = mixins.stream()
                .collect(Collectors.toMap(PlannedMixin::getClassName, m -> m, (a, b) -> a, LinkedHashMap::new));

        // Group candidates per target to limit comparisons to overlapping targets.
        final Map<String, List<PlannedMixin>> byTarget = new LinkedHashMap<>();
        for (final PlannedMixin m : byClass.values()) {
            for (final String target : m.getTargets()) {
                byTarget.computeIfAbsent(target, k -> new ArrayList<>()).add(m);
            }
        }

        final Set<String> removed = new HashSet<>();

        for (final Map.Entry<String, List<PlannedMixin>> group : byTarget.entrySet()) {
            final String target = group.getKey();
            final List<PlannedMixin> list = group.getValue();

            // Pairwise within the target group
            for (int i = 0; i < list.size(); i++) {
                final PlannedMixin a = list.get(i);
                if (removed.contains(a.getClassName())) {
                    continue;
                }

                for (int j = i + 1; j < list.size(); j++) {
                    final PlannedMixin b = list.get(j);
                    if (removed.contains(b.getClassName())) {
                        continue;
                    }

                    final boolean aVsB = conflicts(a, b);
                    final boolean bVsA = conflicts(b, a);
                    if (aVsB || bVsA) {
                        final PlannedMixin keep = pick(a, b);
                        final PlannedMixin drop = (keep == a) ? b : a;
                        problems.warn("conflicts/" + target,
                                "Resolved conflict: kept " + keep.getClassName() +
                                        " (priority " + keep.getPriority() + ") over " + drop.getClassName());
                        removed.add(drop.getClassName());
                    }
                }
            }
        }

        final List<PlannedMixin> filtered = mixins.stream()
                .filter(m -> !removed.contains(m.getClassName()))
                .toList();

        return new WeavePlan(filtered);
    }
}
