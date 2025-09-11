package de.splatgames.aether.mixins.core.plan;

import de.splatgames.aether.mixins.core.config.problems.ConfigProblems;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConflictResolverTest {

    @NotNull
    private static PlannedMixin mixin(@NotNull final String cls,
                                      final int prio,
                                      @NotNull final Set<String> groups,
                                      @NotNull final Set<String> conflicts) {
        return new PlannedMixin(
                cls, prio,
                List.of("T"),
                List.of(PlannedEntry.inject("m()V", "id", false, true, de.splatgames.aether.mixins.core.api.Inject.At.HEAD)),
                groups, Set.of(), conflicts
        );
    }

    @Test
    void resolves_by_priority_then_name() {
        final PlannedMixin aLow = mixin("a.A", 10, Set.of("g1"), Set.of("b.B"));
        final PlannedMixin bHigh = mixin("b.B", 20, Set.of("g2"), Set.of("a.A"));

        final WeavePlan plan = new WeavePlan(List.of(aLow, bHigh));
        final WeavePlan resolved = new ConflictResolver().resolve(plan, new ConfigProblems("test"));

        assertThat(resolved.getMixins()).extracting(PlannedMixin::getClassName).containsExactly("b.B");

        // Tie → lexicographic keeps "a.A"
        final PlannedMixin aTie = mixin("a.A", 10, Set.of("g1"), Set.of("b.B"));
        final PlannedMixin bTie = mixin("b.B", 10, Set.of("g2"), Set.of("a.A"));

        final WeavePlan tiePlan = new WeavePlan(List.of(aTie, bTie));
        final WeavePlan tieResolved = new ConflictResolver().resolve(tiePlan, new ConfigProblems("test"));

        assertThat(tieResolved.getMixins()).extracting(PlannedMixin::getClassName).containsExactly("a.A");
    }

    @Test
    void conflicts_by_group() {
        // a conflicts with group "X"; b is in group X → drop b (since a has higher prio)
        final PlannedMixin a = mixin("pkg.A", 5, Set.of(), Set.of("X"));
        final PlannedMixin b = mixin("pkg.B", 1, Set.of("X"), Set.of());

        final WeavePlan resolved = new ConflictResolver()
                .resolve(new WeavePlan(List.of(a, b)), new ConfigProblems("test"));

        assertThat(resolved.getMixins()).extracting(PlannedMixin::getClassName).containsExactly("pkg.A");
    }
}
