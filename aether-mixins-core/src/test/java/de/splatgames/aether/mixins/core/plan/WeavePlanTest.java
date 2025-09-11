package de.splatgames.aether.mixins.core.plan;

import de.splatgames.aether.mixins.core.api.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WeavePlanTest {

    @Test
    void empty_factory_returns_empty_plan() {
        WeavePlan p = WeavePlan.empty();
        assertThat(p.getMixins()).isEmpty();
    }

    @Test
    void constructor_defensive_copy_and_unmodifiable() {
        PlannedMixin m = new PlannedMixin("M", 0, List.of("T"),
                List.of(PlannedEntry.inject("m()V", "id", false, true, Inject.At.HEAD)),
                Set.of(), Set.of(), Set.of());

        WeavePlan p = new WeavePlan(List.of(m));
        assertThat(p.getMixins()).hasSize(1).isUnmodifiable();
        assertThat(p.getMixins().get(0).getEntries()).isUnmodifiable();
    }
}
