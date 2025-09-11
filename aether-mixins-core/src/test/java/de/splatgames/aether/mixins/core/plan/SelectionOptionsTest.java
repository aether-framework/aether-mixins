package de.splatgames.aether.mixins.core.plan;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SelectionOptionsTest {

    @Test
    void empty_is_all_empty_sets() {
        SelectionOptions o = SelectionOptions.empty();
        assertThat(o.getOnlyGroups()).isEmpty();
        assertThat(o.getDisabledGroups()).isEmpty();
        assertThat(o.getAvailableRequirements()).isEmpty();
    }

    @Test
    void of_makes_unmodifiable_copies_and_equals_hashcode() {
        SelectionOptions o1 = SelectionOptions.of(Set.of("a"), Set.of("b"), Set.of("c"));
        SelectionOptions o2 = SelectionOptions.of(Set.of("a"), Set.of("b"), Set.of("c"));

        assertThat(o1).isEqualTo(o2);
        assertThat(o1.hashCode()).isEqualTo(o2.hashCode());

        assertThat(o1.getOnlyGroups()).isUnmodifiable();
        assertThat(o1.getDisabledGroups()).isUnmodifiable();
        assertThat(o1.getAvailableRequirements()).isUnmodifiable();
    }
}
