package de.splatgames.aether.mixins.core.config.refmap;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;
import de.splatgames.aether.mixins.core.api.Redirect;
import de.splatgames.aether.mixins.core.config.problems.ConfigProblems;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Builds a {@link Refmap} directly from {@link Mixin} classes by scanning their annotated hook methods.
 *
 * <p><b>Primary path:</b> This enables an annotation-driven workflow (similar to Sponge) where the
 * annotations {@link Mixin}, {@link Inject} and {@link Redirect} are the only source of configuration.</p>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class AnnotationRefmapBuilder {

    private AnnotationRefmapBuilder() {
        // no instances
    }

    /**
     * Builds a {@link Refmap} by scanning the given mixin classes.
     *
     * @param schema   refmap schema version to set (use {@link Refmap#CURRENT_SCHEMA}), must be {@code >= 1}
     * @param mixins   classes annotated with {@link Mixin}, must not be {@code null} (elements must not be {@code null})
     * @param problems diagnostics collector, must not be {@code null}
     * @return an in-memory {@link Refmap}, never {@code null}
     */
    @NotNull
    public static Refmap fromMixins(final int schema,
                                    @NotNull final List<@NotNull Class<?>> mixins,
                                    @NotNull final ConfigProblems problems) {
        if (schema < 1) {
            problems.warn("refmap.schema", "Non-positive schema '" + schema + "'; using " + Refmap.CURRENT_SCHEMA);
        }
        Objects.requireNonNull(mixins, "mixins");
        Objects.requireNonNull(problems, "problems");
        for (Class<?> c : mixins) Objects.requireNonNull(c, "mixin class");

        final Refmap refmap = new Refmap(schema > 0 ? schema : Refmap.CURRENT_SCHEMA);
        final List<RefMixin> out = new ArrayList<>(mixins.size());

        for (Class<?> mixinClass : mixins) {
            final Mixin mixinAnn = mixinClass.getAnnotation(Mixin.class);
            if (mixinAnn == null) {
                problems.warn(mixinClass.getName(), "Class is not annotated with @Mixin; skipped");
                continue;
            }

            final RefMixin rm = new RefMixin();
            rm.setClassName(mixinClass.getName());
            rm.setPriority(mixinAnn.priority());

            // targets (dedup + trim basic)
            final List<String> targets = dedupStrings(Arrays.asList(mixinAnn.targets()));
            if (targets.isEmpty()) {
                problems.error(mixinClass.getName() + ".targets", "@Mixin targets() must not be empty");
            }
            rm.setTargets(targets);

            // groups/requires/conflicts (dedup)
            rm.setGroups(dedupStrings(Arrays.asList(mixinAnn.groups())));
            rm.setRequires(dedupStrings(Arrays.asList(mixinAnn.requires())));
            rm.setConflictsWith(dedupStrings(Arrays.asList(mixinAnn.conflictsWith())));

            // entries: scan declared methods for @Inject/@Redirect
            rm.setEntries(scanEntries(mixinClass, problems));

            // per-mixin validation
            rm.validate(problems, "annotation:" + mixinClass.getName());
            out.add(rm);
        }

        refmap.setMixins(out);
        // top-level validation
        refmap.validate(problems, "annotation:refmap");
        return refmap;
    }

    /**
     * Scans a mixin class for {@link Inject} and {@link Redirect} hook methods and converts them to {@link RefEntry}s.
     *
     * @param mixinClass mixin class, must not be {@code null}
     * @param problems   diagnostics collector, must not be {@code null}
     * @return immutable list of ref entries, never {@code null}
     */
    @NotNull
    private static List<RefEntry> scanEntries(@NotNull final Class<?> mixinClass,
                                              @NotNull final ConfigProblems problems) {
        final List<RefEntry> entries = new ArrayList<>();
        for (Method m : mixinClass.getDeclaredMethods()) {
            final Inject inj = m.getAnnotation(Inject.class);
            String path = mixinClass.getName() + "#" + m.getName();
            if (inj != null) {
                final RefEntry e = new RefEntry();
                e.setType(RefEntry.Type.INJECT);
                e.setId(nonNullOrDefault(injectId(inj), m.getName()));
                e.setMethod(inj.method());
                e.setAt(inj.at());
                e.setOptional(injectOptional(inj));
                e.setRemap(injectRemap(inj));
                e.validate(problems, path);
                entries.add(e);
                continue;
            }

            final Redirect red = m.getAnnotation(Redirect.class);
            if (red != null) {
                final RefEntry e = new RefEntry();
                e.setType(RefEntry.Type.REDIRECT);
                e.setId(nonNullOrDefault(redirectId(red), m.getName()));
                e.setMethod(red.method());
                e.setCallOwner(red.callOwner());
                e.setCallName(red.callName());
                e.setCallDesc(red.callDesc());
                e.setKind(nonNullOrDefault(red.kind(), Redirect.InvokeKind.AUTO));
                e.setOrdinal(red.ordinal());
                e.setOptional(redirectOptional(red));
                e.setRemap(redirectRemap(red));
                e.validate(problems, path);
                entries.add(e);
            }
        }
        return List.copyOf(entries);
    }

    /**
     * Returns {@code s} if non-null/non-blank, otherwise {@code def}.
     */
    @NotNull
    private static String nonNullOrDefault(@Nullable final String s, @NotNull final String def) {
        return (s != null && !s.isBlank()) ? s : def;
    }

    /**
     * Returns {@code obj} if non-null, otherwise {@code def}.
     */
    @NotNull
    private static <T> T nonNullOrDefault(@Nullable final T obj, @NotNull final T def) {
        return obj != null ? obj : def;
    }

    /**
     * Deduplicates and trims a list of strings, removing empties while preserving order.
     */
    @NotNull
    private static List<String> dedupStrings(@NotNull final List<String> in) {
        final Set<String> seen = new LinkedHashSet<>();
        final List<String> out = new ArrayList<>(in.size());
        for (String s : in) {
            if (s == null) continue;
            final String t = s.trim();
            if (t.isEmpty()) continue;
            if (seen.add(t)) out.add(t);
        }
        return List.copyOf(out);
    }

    // -------- Annotation element helpers (handle presence/absence of optional elements) --------

    /**
     * Returns the {@code id} element from {@link Inject} or {@code null} if the element is not present in the annotation type.
     */
    @Nullable
    private static String injectId(@NotNull final Inject ann) {
        try {
            return (String) Inject.class.getMethod("id").invoke(ann);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    /**
     * Returns {@code optional} from {@link Inject}, defaulting to {@code false} if element is absent.
     */
    private static boolean injectOptional(@NotNull final Inject ann) {
        try {
            return (boolean) Inject.class.getMethod("optional").invoke(ann);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    /**
     * Returns {@code remap} from {@link Inject}, defaulting to {@code true} if element is absent.
     */
    private static boolean injectRemap(@NotNull final Inject ann) {
        try {
            return (boolean) Inject.class.getMethod("remap").invoke(ann);
        } catch (ReflectiveOperationException ignored) {
            return true;
        }
    }

    /**
     * Returns the {@code id} element from {@link Redirect} or {@code null} if the element is not present.
     */
    @Nullable
    private static String redirectId(@NotNull final Redirect ann) {
        try {
            return (String) Redirect.class.getMethod("id").invoke(ann);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    /**
     * Returns {@code optional} from {@link Redirect}, defaulting to {@code false} if element is absent.
     */
    private static boolean redirectOptional(@NotNull final Redirect ann) {
        try {
            return (boolean) Redirect.class.getMethod("optional").invoke(ann);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    /**
     * Returns {@code remap} from {@link Redirect}, defaulting to {@code true} if element is absent.
     */
    private static boolean redirectRemap(@NotNull final Redirect ann) {
        try {
            return (boolean) Redirect.class.getMethod("remap").invoke(ann);
        } catch (ReflectiveOperationException ignored) {
            return true;
        }
    }
}
