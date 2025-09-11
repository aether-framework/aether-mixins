package de.splatgames.aether.mixins.core.plan;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Redirect;
import de.splatgames.aether.mixins.core.config.problems.ConfigProblems;
import de.splatgames.aether.mixins.core.config.refmap.RefEntry;
import de.splatgames.aether.mixins.core.config.refmap.RefMixin;
import de.splatgames.aether.mixins.core.config.refmap.Refmap;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WeavePlannerTest {

    @NotNull
    private static RefEntry injectEntry(@NotNull final String method, @NotNull final Inject.At at) {
        final RefEntry e = new RefEntry();
        e.setType(RefEntry.Type.INJECT);
        e.setMethod(method);
        e.setAt(at);
        e.setId("inject-id");
        e.setOptional(false);
        e.setRemap(true);
        return e;
    }

    @NotNull
    private static RefEntry redirectEntry(@NotNull final String method,
                                          @NotNull final String owner, @NotNull final String name, @NotNull final String desc,
                                          @NotNull final Redirect.InvokeKind kind, final int ordinal) {
        final RefEntry e = new RefEntry();
        e.setType(RefEntry.Type.REDIRECT);
        e.setMethod(method);
        e.setCallOwner(owner);
        e.setCallName(name);
        e.setCallDesc(desc);
        e.setKind(kind);
        e.setOrdinal(ordinal);
        e.setId("redir-id");
        e.setOptional(true);
        e.setRemap(true);
        return e;
    }

    @NotNull
    private static RefMixin mixin(@NotNull final String className,
                                  final int priority,
                                  @NotNull final List<String> targets,
                                  @NotNull final List<RefEntry> entries,
                                  @NotNull final List<String> groups,
                                  @NotNull final List<String> requires,
                                  @NotNull final List<String> conflictsWith) {
        final RefMixin m = new RefMixin();
        m.setClassName(className);
        m.setPriority(priority);
        m.setTargets(targets);
        m.setEntries(entries);
        m.setGroups(groups);
        m.setRequires(requires);
        m.setConflictsWith(conflictsWith);
        return m;
    }

    @NotNull
    private static Refmap refmap(@NotNull final List<RefMixin> mixins) {
        final Refmap r = new Refmap(Refmap.CURRENT_SCHEMA);
        r.setMixins(mixins);
        return r;
    }

    @Test
    void plan_flattens_filters_transforms_and_resolves_conflicts() {
        // mixin A and B conflict with each other; B has higher priority → B survives
        final RefMixin a = mixin(
                "a.A", 10,
                List.of("pkg.Target"),
                List.of(injectEntry("run()V", Inject.At.HEAD)),
                List.of("core"),
                List.of(),
                List.of("b.B", "groupX")
        );
        final RefMixin b = mixin(
                "b.B", 20,
                List.of("pkg.Target"),
                List.of(redirectEntry("run()V", "pkg/Util", "doX", "(I)I", Redirect.InvokeKind.INVOKESTATIC, 0)),
                List.of("groupX"),
                List.of(),
                List.of("a.A")
        );

        final Refmap ref1 = refmap(List.of(a));
        final Refmap ref2 = refmap(List.of(b));

        final WeavePlanner planner = new WeavePlanner();
        final ConfigProblems problems = new ConfigProblems("test");
        final SelectionOptions opts = SelectionOptions.empty();

        final WeavePlan plan = planner.plan(List.of(ref1, ref2), opts, problems);

        // Expect only B to remain
        assertThat(plan.getMixins()).hasSize(1);
        final PlannedMixin kept = plan.getMixins().get(0);
        assertThat(kept.getClassName()).isEqualTo("b.B");
        assertThat(kept.getEntries()).hasSize(1);
        assertThat(kept.getEntries().get(0).getKind()).isEqualTo(PlannedEntry.Kind.REDIRECT);

        // Conflict warning should be present
        assertThat(problems.all()).anySatisfy(p ->
                assertThat(p.message()).contains("Resolved conflict: kept b.B"));
    }

    @Test
    void selection_onlyGroups_and_disabledGroups_and_requires() {
        final RefMixin coreMixin = mixin(
                "x.Core", 0,
                List.of("T"),
                List.of(injectEntry("t()V", Inject.At.TAIL)),
                List.of("core"),               // groups
                List.of("featX"),              // requires
                List.of()
        );
        final Refmap ref = refmap(List.of(coreMixin));

        final WeavePlanner planner = new WeavePlanner();
        final ConfigProblems problems = new ConfigProblems("test");

        // 1) onlyGroups={"core"}, availableRequirements includes featX → included
        SelectionOptions opts1 = SelectionOptions.of(Set.of("core"), Set.of(), Set.of("featX"));
        WeavePlan p1 = planner.plan(List.of(ref), opts1, problems);
        assertThat(p1.getMixins()).extracting(PlannedMixin::getClassName)
                .containsExactly("x.Core");

        // 2) onlyGroups={"other"} → excluded (warns no mixins selected)
        final ConfigProblems probs2 = new ConfigProblems("test");
        SelectionOptions opts2 = SelectionOptions.of(Set.of("other"), Set.of(), Set.of("featX"));
        WeavePlan p2 = planner.plan(List.of(ref), opts2, probs2);
        assertThat(p2.getMixins()).isEmpty();
        assertThat(probs2.all()).anySatisfy(p -> assertThat(p.message()).contains("No mixins selected"));

        // 3) disabledGroups={"core"} → excluded
        final ConfigProblems probs3 = new ConfigProblems("test");
        SelectionOptions opts3 = SelectionOptions.of(Set.of(), Set.of("core"), Set.of("featX"));
        WeavePlan p3 = planner.plan(List.of(ref), opts3, probs3);
        assertThat(p3.getMixins()).isEmpty();

        // 4) requires not satisfied → excluded
        final ConfigProblems probs4 = new ConfigProblems("test");
        SelectionOptions opts4 = SelectionOptions.of(Set.of("core"), Set.of(), Set.of(/* missing featX */));
        WeavePlan p4 = planner.plan(List.of(ref), opts4, probs4);
        assertThat(p4.getMixins()).isEmpty();
    }

    @Test
    void entries_are_transformed_correctly() {
        final RefMixin m = mixin(
                "M", 0,
                List.of("T"),
                List.of(
                        injectEntry("a()V", Inject.At.HEAD),
                        redirectEntry("b()I", "pkg/Util", "f", "()I", Redirect.InvokeKind.INVOKESTATIC, 0)
                ),
                List.of(), List.of(), List.of()
        );
        final WeavePlan plan = new WeavePlanner()
                .plan(List.of(refmap(List.of(m))), SelectionOptions.empty(), new ConfigProblems("test"));

        assertThat(plan.getMixins()).hasSize(1);
        final PlannedMixin pm = plan.getMixins().get(0);
        assertThat(pm.getEntries()).hasSize(2);

        final PlannedEntry e0 = pm.getEntries().get(0);
        assertThat(e0.getKind()).isEqualTo(PlannedEntry.Kind.INJECT);
        assertThat(e0.getMethod()).isEqualTo("a()V");
        assertThat(e0.getAt()).isEqualTo(Inject.At.HEAD);

        final PlannedEntry e1 = pm.getEntries().get(1);
        assertThat(e1.getKind()).isEqualTo(PlannedEntry.Kind.REDIRECT);
        assertThat(e1.getMethod()).isEqualTo("b()I");
        assertThat(e1.getCallOwner()).isEqualTo("pkg/Util");
        assertThat(e1.getCallName()).isEqualTo("f");
        assertThat(e1.getCallDesc()).isEqualTo("()I");
        assertThat(e1.getInvokeKind()).isEqualTo(Redirect.InvokeKind.INVOKESTATIC);
        assertThat(e1.getOrdinal()).isEqualTo(0);
    }

    @Test
    void weavePlan_is_immutable() {
        final RefMixin m = mixin(
                "M", 0, List.of("T"), List.of(injectEntry("x()V", Inject.At.HEAD)),
                List.of(), List.of(), List.of()
        );
        final WeavePlan plan = new WeavePlanner()
                .plan(List.of(refmap(List.of(m))), SelectionOptions.empty(), new ConfigProblems("test"));

        assertThat(plan.getMixins()).isUnmodifiable();
        assertThat(plan.getMixins().get(0).getTargets()).isUnmodifiable();
        assertThat(plan.getMixins().get(0).getEntries()).isUnmodifiable();
    }
}
