package de.splatgames.aether.mixins.core.plan;

import de.splatgames.aether.mixins.core.config.problems.ConfigProblems;
import de.splatgames.aether.mixins.core.config.refmap.RefEntry;
import de.splatgames.aether.mixins.core.config.refmap.RefMixin;
import de.splatgames.aether.mixins.core.config.refmap.Refmap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Builds a {@link WeavePlan} from one or more {@link Refmap} documents and applies selection rules.
 *
 * <h2>Process</h2>
 * <ol>
 *   <li>Flatten all {@link Refmap#getMixins()} across inputs into a single stream of mixins.</li>
 *   <li>Filter mixins according to {@link SelectionOptions} (e.g., groups, required flags).</li>
 *   <li>Transform structures:
 *     <ul>
 *       <li>{@link RefMixin} → {@link PlannedMixin}</li>
 *       <li>{@link RefEntry} → {@link PlannedEntry}</li>
 *     </ul>
 *   </li>
 *   <li>Apply {@link ConflictResolver} to remove mixins that conflict with each other.</li>
 * </ol>
 *
 * <h2>Assumptions</h2>
 * <p>
 * Inputs are expected to have been validated beforehand
 * (e.g., via {@link Refmap#validate(ConfigProblems, String)} and related nested validations).
 * This planner enforces only basic non-null invariants and structural consistency,
 * but does not perform full schema validation.
 * </p>
 *
 * <h2>Determinism</h2>
 * <p>
 * The relative order of mixins after filtering is preserved until conflict resolution,
 * which itself is deterministic based on priority and class name.
 * </p>
 *
 * <h2>Complexity</h2>
 * <ul>
 *   <li>Filtering and mapping operations are O(n).</li>
 *   <li>Conflict resolution is O(n²) relative to the number of selected mixins,
 *       which is acceptable for typical mixin counts.</li>
 * </ul>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class WeavePlanner {

    /**
     * Evaluates whether a mixin passes the group-based selection rules.
     *
     * <p>Rules:</p>
     * <ul>
     *   <li>If {@code disabledGroups} contains any of the mixin's groups → exclude.</li>
     *   <li>If {@code onlyGroups} is non-empty → include only if there is an intersection.</li>
     *   <li>Otherwise → include.</li>
     * </ul>
     *
     * @param m mixin to test, must not be {@code null}
     * @param o selection options, must not be {@code null}
     * @return {@code true} if the mixin passes group filtering; {@code false} otherwise
     */
    private static boolean includeByGroups(@NotNull final RefMixin m, @NotNull final SelectionOptions o) {
        // disabled → exclude
        if (!o.getDisabledGroups().isEmpty()) {
            for (String g : m.getGroups()) {
                if (o.getDisabledGroups().contains(g)) return false;
            }
        }
        // onlyGroups set → must intersect
        if (!o.getOnlyGroups().isEmpty()) {
            for (String g : m.getGroups()) {
                if (o.getOnlyGroups().contains(g)) return true;
            }
            return false;
        }
        return true;
    }

    /**
     * Evaluates whether a mixin passes the requirement-based selection rules.
     *
     * <p>Rule: {@code m.requires ⊆ availableRequirements}.</p>
     *
     * @param m mixin to test, must not be {@code null}
     * @param o selection options, must not be {@code null}
     * @return {@code true} if all requirements are available; {@code false} otherwise
     */
    private static boolean includeByRequires(@NotNull final RefMixin m, @NotNull final SelectionOptions o) {
        if (m.getRequires().isEmpty()) return true;
        return o.getAvailableRequirements().containsAll(m.getRequires());
    }

    /**
     * Converts a validated {@link RefMixin} to a {@link PlannedMixin}.
     *
     * <p>Collections are defensively copied by the {@link PlannedMixin} constructor.</p>
     *
     * @param m source mixin, must not be {@code null}
     * @return planned mixin, never {@code null}
     * @throws NullPointerException if required fields are {@code null}
     */
    private static @NotNull PlannedMixin toPlanned(@NotNull final RefMixin m) {
        final List<PlannedEntry> entries = m.getEntries().stream()
                .map(WeavePlanner::toPlanned)
                .toList();

        return new PlannedMixin(
                Objects.requireNonNull(m.getClassName(), "className"),
                m.getPriority(),
                m.getTargets(),
                entries,
                Set.copyOf(m.getGroups()),
                Set.copyOf(m.getRequires()),
                Set.copyOf(m.getConflictsWith())
        );
    }

    /**
     * Converts a validated {@link RefEntry} to a {@link PlannedEntry}.
     *
     * <p>Precondition: {@link RefEntry} must have a non-null, valid type and required fields populated
     * by the loader/validator.</p>
     *
     * @param e source entry, must not be {@code null}
     * @return planned entry, never {@code null}
     * @throws NullPointerException     if required fields are {@code null}
     * @throws IllegalArgumentException if invariants enforced by {@link PlannedEntry} are violated
     */
    private static @NotNull PlannedEntry toPlanned(@NotNull final RefEntry e) {
        if (e.isInject()) {
            return PlannedEntry.inject(
                    Objects.requireNonNull(e.getMethod(), "method"),
                    e.getId(),
                    e.isOptional(),
                    e.isRemap(),
                    Objects.requireNonNull(e.getAt(), "at")
            );
        } else {
            // Treat any non-INJECT as REDIRECT (the validator ensures correct type).
            return PlannedEntry.redirect(
                    Objects.requireNonNull(e.getMethod(), "method"),
                    e.getId(),
                    e.isOptional(),
                    e.isRemap(),
                    Objects.requireNonNull(e.getCallOwner(), "callOwner"),
                    Objects.requireNonNull(e.getCallName(), "callName"),
                    Objects.requireNonNull(e.getCallDesc(), "callDesc"),
                    e.getKind(),
                    e.getOrdinal()
            );
        }
    }

    /**
     * Creates a weave plan from the given refmaps, applies selection rules, and resolves conflicts.
     *
     * <p>This is a convenience overload of {@link #plan(List, SelectionOptions, ConfigProblems, List)}
     * that does not add any extra mixins.</p>
     *
     * <p>Notes:</p>
     * <ul>
     *   <li>If no mixins remain after selection, a warning is recorded.</li>
     *   <li>The returned plan has conflicts removed according to {@link ConflictResolver} policy.</li>
     * </ul>
     *
     * @param refmaps  refmaps to consider, must not be {@code null} and must not contain {@code null} elements
     * @param options  selection rules, must not be {@code null}; use {@link SelectionOptions#empty()} for defaults
     * @param problems diagnostics collector, must not be {@code null}
     * @return finalized weave plan, never {@code null}
     * @throws NullPointerException if any argument (or refmap element) is {@code null}
     */
    @NotNull
    public WeavePlan plan(@NotNull List<@NotNull Refmap> refmaps,
                          @NotNull SelectionOptions options,
                          @NotNull ConfigProblems problems) {
        return plan(refmaps, options, problems, java.util.List.of());
    }

    /**
     * Creates a weave plan from the given refmaps, applies selection rules, and resolves conflicts.
     *
     * <p>Notes:</p>
     * <ul>
     *   <li>If no mixins remain after selection, a warning is recorded.</li>
     *   <li>The returned plan has conflicts removed according to {@link ConflictResolver} policy.</li>
     * </ul>
     *
     * @param refmaps  refmaps to consider, must not be {@code null} and must not contain {@code null} elements
     * @param options  selection rules, must not be {@code null}; use {@link SelectionOptions#empty()} for defaults
     * @param problems diagnostics collector, must not be {@code null}
     * @param extraMixins additional mixins to include outside of refmaps, must not be {@code null} and must not contain {@code null} elements
     * @return finalized weave plan, never {@code null}
     * @throws NullPointerException if any argument (or refmap element) is {@code null}
     */
    @NotNull
    public WeavePlan plan(@NotNull final List<@NotNull Refmap> refmaps,
                          @NotNull final SelectionOptions options,
                          @NotNull final ConfigProblems problems,
                          @NotNull List<RefMixin> extraMixins) {
        Objects.requireNonNull(refmaps, "refmaps");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(problems, "problems");
        for (Refmap r : refmaps) Objects.requireNonNull(r, "refmaps element");

        // 1) flatten refmap mixins
        final List<RefMixin> all = new ArrayList<>(refmaps.stream()
                .flatMap(r -> r.getMixins().stream())
                .toList());

        for (RefMixin m : extraMixins) {
            Objects.requireNonNull(m, "refMixins element");
            all.add(m);
        }

        // 2) filter by selection options
        final List<RefMixin> selected = all.stream()
                .filter(m -> includeByGroups(m, options))
                .filter(m -> includeByRequires(m, options))
                .toList();

        if (selected.isEmpty()) {
            problems.warn("planner", "No mixins selected after applying selection options.");
        }

        // 3) transform into plan units
        final List<PlannedMixin> planned = selected.stream()
                .map(WeavePlanner::toPlanned)
                .toList();

        // 4) resolve conflicts
        final WeavePlan preliminary = new WeavePlan(planned);
        return new ConflictResolver().resolve(preliminary, problems);
    }
}
