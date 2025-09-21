package de.splatgames.aether.mixins.runtime;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;
import de.splatgames.aether.mixins.core.api.Redirect;
import de.splatgames.aether.mixins.core.config.ConfigLoader;
import de.splatgames.aether.mixins.core.config.mixins.MixinSet;
import de.splatgames.aether.mixins.core.config.mixins.MixinsConfig;
import de.splatgames.aether.mixins.core.config.problems.ConfigProblems;
import de.splatgames.aether.mixins.core.config.refmap.RefEntry;
import de.splatgames.aether.mixins.core.config.refmap.RefMixin;
import de.splatgames.aether.mixins.core.config.refmap.Refmap;
import de.splatgames.aether.mixins.core.config.refmap.RefmapLoader;
import de.splatgames.aether.mixins.core.config.runtime.RuntimeConfig;
import de.splatgames.aether.mixins.core.plan.SelectionOptions;
import de.splatgames.aether.mixins.core.plan.WeavePlan;
import de.splatgames.aether.mixins.core.plan.WeavePlanner;
import de.splatgames.aether.mixins.core.weaver.spi.ClassSink;
import de.splatgames.aether.mixins.core.weaver.spi.ClassSource;
import de.splatgames.aether.mixins.core.weaver.spi.WeaveRequest;
import de.splatgames.aether.mixins.core.weaver.spi.WeaveResult;
import de.splatgames.aether.mixins.core.weaver.spi.Weaver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * High-level runtime driver that orchestrates configuration loading, refmap parsing,
 * plan construction, and weaving.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Load {@link MixinsConfig} from YAML via a {@link ConfigLoader}.</li>
 *   <li>Resolve and load refmaps via a {@link RefmapLoader} (paths are resolved relative to the YAML file).</li>
 *   <li>Optionally augment refmaps from annotation-scanned mixin classes declared under {@code mixins[].classes}.</li>
 *   <li>Build a {@link WeavePlan} using {@link WeavePlanner} and provided {@link SelectionOptions}.</li>
 *   <li>Execute the {@link Weaver} with a {@link WeaveRequest} and return a {@link RuntimeSession} snapshot.</li>
 * </ul>
 *
 * <h2>Error handling &amp; diagnostics</h2>
 * <ul>
 *   <li>Content/consistency issues should be recorded into {@link ConfigProblems} (warnings/errors).</li>
 *   <li>Refmap I/O issues are reported into {@code problems} and skipped; the driver proceeds where possible.</li>
 *   <li>YAML root/parse errors may propagate from {@link ConfigLoader#load(Path)} by design.</li>
 *   <li>Weaver failures may propagate depending on the runtime's safe mode.</li>
 * </ul>
 *
 * <h2>Thread-safety</h2>
 * <p>
 * Instances are immutable after construction and safe to publish. The {@link #execute(Path, SelectionOptions, ClassSource, ClassSink, Weaver, ConfigProblems)}
 * method performs I/O and should be executed with appropriate external coordination if multiple runs share I/O resources.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * var driver   = new RuntimeDriver(configLoader, refmapLoader);
 * var options  = SelectionOptions.empty();
 * var problems = new ConfigProblems("session");
 *
 * RuntimeSession session = driver.execute(yamlPath, options, source, sink, weaver, problems);
 * System.out.println("Transformed: " + session.result().transformed());
 * }</pre>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class RuntimeDriver {

    /**
     * Loader for the top-level YAML mixins configuration.
     */
    @NotNull
    private final ConfigLoader configLoader;

    /**
     * Loader for JSON refmaps referenced by the YAML.
     */
    @NotNull
    private final RefmapLoader refmapLoader;

    /**
     * Constructs a new driver with the given configuration and refmap loaders.
     *
     * @param configLoader loader for mixins YAML, must not be {@code null}
     * @param refmapLoader loader for JSON refmaps, must not be {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public RuntimeDriver(@NotNull final ConfigLoader configLoader,
                         @NotNull final RefmapLoader refmapLoader) {
        this.configLoader = Objects.requireNonNull(configLoader, "configLoader");
        this.refmapLoader = Objects.requireNonNull(refmapLoader, "refmapLoader");
    }

    /**
     * Returns the parent directory of the YAML file, or the YAML path itself if it has no parent.
     *
     * @param yamlPath path to the YAML file, must not be {@code null}
     * @return base directory used for resolving relative refmap paths, never {@code null}
     */
    @NotNull
    private static Path baseDirOf(@NotNull final Path yamlPath) {
        final Path parent = yamlPath.getParent();
        return (parent != null) ? parent : yamlPath;
    }

    /**
     * Resolves a refmap path string against a base directory and normalizes the result.
     *
     * @param baseDir base directory for resolution, must not be {@code null}
     * @param file    string path to the refmap, must not be {@code null}
     * @return normalized resolved path, never {@code null}
     */
    @NotNull
    private static Path resolveRefmapPath(@NotNull final Path baseDir,
                                          @NotNull final String file) {
        final String trimmed = file.trim();
        return baseDir.resolve(trimmed).normalize();
    }

    /**
     * Scans {@code mixins[].classes} entries for classes annotated with {@link Mixin} and converts
     * them to {@link RefMixin} structures (including {@link Redirect} and {@link Inject} method entries).
     *
     * <p>Classes without {@link Mixin} are skipped with a warning. Classes with no targets or no
     * hook annotations are reported and skipped.</p>
     *
     * @param cfg      loaded YAML configuration, must not be {@code null}
     * @param problems diagnostics collector, must not be {@code null}
     * @return list of {@link RefMixin} discovered from annotations (possibly empty), never {@code null}
     */
    @NotNull
    private static List<RefMixin> scanAnnotationMixins(@NotNull final MixinsConfig cfg,
                                                       @NotNull final ConfigProblems problems) {
        final List<RefMixin> extraMixins = new ArrayList<>();
        final ClassLoader cl = Thread.currentThread().getContextClassLoader();

        for (int i = 0; i < cfg.getMixins().size(); i++) {
            final MixinSet set = cfg.getMixins().get(i);
            final List<String> classes = (set.getClasses() != null) ? set.getClasses() : List.of();
            final String pathPrefix = "mixins[" + i + "].classes";

            for (final String cn : classes) {
                final RefMixin maybe = buildRefMixinFromClassName(cn, cl, problems, pathPrefix);
                if (maybe != null) {
                    extraMixins.add(maybe);
                }
            }
        }
        return extraMixins;
    }

    /**
     * Loads the given class name, validates {@link Mixin} presence, extracts targets and hook entries,
     * validates the resulting {@link RefMixin}, and returns it; otherwise returns {@code null} and records diagnostics.
     *
     * @param className  fully qualified class name, must not be {@code null}
     * @param cl         class loader to use, must not be {@code null}
     * @param problems   diagnostics collector, must not be {@code null}
     * @param pathPrefix diagnostics path prefix, must not be {@code null}
     * @return a validated {@link RefMixin}, or {@code null} if the class is missing/invalid
     */
    @Nullable
    private static RefMixin buildRefMixinFromClassName(@NotNull final String className,
                                                       @NotNull final ClassLoader cl,
                                                       @NotNull final ConfigProblems problems,
                                                       @NotNull final String pathPrefix) {
        try {
            final Class<?> mixinClass = Class.forName(className, false, cl);

            final Mixin mixinAnno = mixinClass.getAnnotation(Mixin.class);
            if (mixinAnno == null) {
                problems.warn(pathPrefix, "Class '" + className + "' lacks @Mixin — skipped.");
                return null;
            }

            final List<String> targets = extractTargets(mixinAnno);
            if (targets.isEmpty()) {
                problems.error(pathPrefix, "Mixin class '" + className + "' has no targets; skipping.");
                return null;
            }

            final List<RefEntry> entries = collectEntriesFromMethods(mixinClass.getDeclaredMethods());
            if (entries.isEmpty()) {
                problems.warn(pathPrefix, "Mixin class '" + className + "' declares no @Redirect or @Inject entries; skipping.");
                return null;
            }

            final RefMixin rm = new RefMixin();
            rm.setClassName(mixinClass.getName());
            rm.setTargets(targets);
            rm.setEntries(entries);
            rm.setPriority(mixinAnno.priority());
            rm.setGroups(List.of());
            rm.setRequires(List.of());
            rm.setConflictsWith(List.of());

            rm.validate(problems, pathPrefix + "('" + className + "')");
            return rm;

        } catch (final ClassNotFoundException ex) {
            problems.warn(pathPrefix, "Mixin class not found: " + className);
            return null;
        }
    }

    /**
     * Extracts declared target internal names from a {@link Mixin} annotation.
     *
     * @param mixinAnno mixin annotation instance, must not be {@code null}
     * @return list of targets (possibly empty), never {@code null}
     */
    @NotNull
    private static List<String> extractTargets(@NotNull final Mixin mixinAnno) {
        final String[] annTargets = mixinAnno.targets();
        final String[] valueTargets = Arrays.stream(mixinAnno.value())
            .map(Class::getName)
            .toArray(String[]::new);
        if (annTargets.length == 0 && valueTargets.length > 0) {
            return List.of(valueTargets);
        }
        if (annTargets.length > 0 && valueTargets.length > 0) {
            return Stream.concat(Arrays.stream(annTargets), Arrays.stream(valueTargets)).toList();
        }
        return annTargets.length > 0 ? List.of(annTargets) : List.of();
    }

    /**
     * Collects {@link RefEntry} definitions from methods annotated with {@link Redirect} and/or {@link Inject}.
     *
     * <p>For {@code @Redirect}, sets type, method signature, id, optional/remap flags, and call site metadata.
     * For {@code @Inject}, sets type, method signature, id, optional/remap flags, and join point.</p>
     *
     * @param methods declared methods of the mixin class, must not be {@code null}
     * @return list of ref entries (possibly empty), never {@code null}
     */
    @NotNull
    private static List<RefEntry> collectEntriesFromMethods(@NotNull final Method[] methods) {
        final List<RefEntry> entries = new ArrayList<>();
        for (final Method m : methods) {
            final Redirect r = m.getAnnotation(Redirect.class);
            if (r != null) {
                entries.add(toRefEntry(m, r));
            }
            final Inject inj = m.getAnnotation(Inject.class);
            if (inj != null) {
                entries.add(toRefEntry(m, inj));
            }
        }
        return entries;
    }

    /**
     * Converts a {@link Redirect} annotation on a method into a {@link RefEntry}.
     *
     * @param m method annotated with {@code @Redirect}, must not be {@code null}
     * @param r annotation instance, must not be {@code null}
     * @return populated ref entry, never {@code null}
     */
    @NotNull
    private static RefEntry toRefEntry(@NotNull final Method m, @NotNull final Redirect r) {
        final RefEntry e = new RefEntry();
        e.setType(RefEntry.Type.REDIRECT);
        e.setMethod(r.method());
        e.setId((r.id() == null || r.id().isBlank()) ? m.getName() : r.id());
        e.setOptional(r.optional());
        e.setRemap(r.remap());
        e.setCallOwner(r.callOwner());
        e.setCallName(r.callName());
        e.setCallDesc(r.callDesc());
        e.setKind(r.kind());
        e.setOrdinal(r.ordinal());
        return e;
    }

    /**
     * Converts an {@link Inject} annotation on a method into a {@link RefEntry}.
     *
     * @param m   method annotated with {@link Inject}, must not be {@code null}
     * @param inj annotation instance, must not be {@code null}
     * @return populated ref entry, never {@code null}
     */
    @NotNull
    private static RefEntry toRefEntry(@NotNull final Method m, @NotNull final Inject inj) {
        final RefEntry e = new RefEntry();
        e.setType(RefEntry.Type.INJECT);
        e.setMethod(inj.method());
        e.setId((inj.id() == null || inj.id().isBlank()) ? m.getName() : inj.id());
        e.setOptional(inj.optional());
        e.setRemap(inj.remap());
        e.setAt(inj.at());
        return e;
    }

    /**
     * Deduplicates {@link RefMixin} entries by class name while preserving first occurrence order.
     *
     * @param mixins list of mixins to deduplicate, must not be {@code null}
     * @return new list with unique class names (possibly empty), never {@code null}
     */
    @NotNull
    private static List<RefMixin> deduplicateMixins(@NotNull final List<RefMixin> mixins) {
        if (mixins.isEmpty()) {
            return List.of();
        }
        final LinkedHashMap<String, RefMixin> dedup = new LinkedHashMap<>(mixins.size());
        for (final RefMixin rm : mixins) {
            dedup.put(rm.getClassName(), rm);
        }
        return new ArrayList<>(dedup.values());
    }

    /**
     * Executes a full mixing session: load YAML, load refmaps, (optionally) augment from annotated classes, plan, weave.
     *
     * <p>The returned {@link RuntimeSession} aggregates inputs/outputs of the run for inspection and reporting.</p>
     *
     * @param yamlPath path to the YAML configuration, must not be {@code null}
     * @param options  selection rules (groups/requires); use {@link SelectionOptions#empty()} for defaults, must not be {@code null}
     * @param source   class source for reading bytecode, must not be {@code null}
     * @param sink     class sink for writing transformed bytecode, must not be {@code null}
     * @param weaver   weaver implementation (should be fully wired, e.g., with its HookResolver), must not be {@code null}
     * @param problems diagnostics collector for warnings and errors, must not be {@code null}
     * @return immutable session snapshot containing config, refmaps, plan, weaver result, and timing; never {@code null}
     * @throws IOException              if reading the YAML fails; other refmap I/O errors are recorded in {@code problems}
     * @throws IllegalArgumentException if the YAML is syntactically invalid (as signaled by {@link ConfigLoader})
     * @throws Exception                for fatal weaver errors (depending on safe-mode)
     */
    @NotNull
    public RuntimeSession execute(@NotNull final Path yamlPath,
                                  @NotNull final SelectionOptions options,
                                  @NotNull final ClassSource source,
                                  @NotNull final ClassSink sink,
                                  @NotNull final Weaver weaver,
                                  @NotNull final ConfigProblems problems) throws Exception {
        final long t0 = System.nanoTime();

        final MixinsConfig cfg = this.configLoader.load(yamlPath);
        final List<Refmap> refmaps = this.loadRefmaps(cfg, yamlPath, problems);

        final List<RefMixin> extraMixins = deduplicateMixins(scanAnnotationMixins(cfg, problems));
        final WeavePlan plan = new WeavePlanner().plan(refmaps, options, problems, extraMixins);

        final RuntimeConfig rt = cfg.getRuntime();
        final WeaveRequest request = WeaveRequest.of(plan, source, sink, rt);
        final WeaveResult result = weaver.weave(request, problems);

        final long durationNanos = System.nanoTime() - t0;
        return new RuntimeSession(cfg, refmaps, plan, result, durationNanos);
    }

    /**
     * Loads all refmaps referenced by the given configuration.
     *
     * <p>Paths under {@code mixins[*].files[*]} are resolved relative to the YAML file's parent directory.</p>
     *
     * <p>I/O/parse errors for individual refmaps are recorded into {@code problems} (as errors) and skipped.
     * If no refmaps are loaded successfully, a warning is recorded and an empty list is returned.</p>
     *
     * @param cfg      loaded YAML configuration, must not be {@code null}
     * @param yamlPath path to the YAML file, used as base directory for refmap resolution; must not be {@code null}
     * @param problems diagnostics collector, must not be {@code null}
     * @return list of successfully loaded refmaps (possibly empty), never {@code null}
     */
    @NotNull
    private List<Refmap> loadRefmaps(@NotNull final MixinsConfig cfg,
                                     @NotNull final Path yamlPath,
                                     @NotNull final ConfigProblems problems) {
        final Path baseDir = baseDirOf(yamlPath);
        final List<Refmap> out = new ArrayList<>();

        if (cfg.getMixins().isEmpty()) {
            problems.warn("mixins", "No mixin sets declared in YAML.");
            return List.of();
        }

        for (int i = 0; i < cfg.getMixins().size(); i++) {
            final MixinSet set = cfg.getMixins().get(i);
            final String setPath = "mixins[" + i + "]";
            final List<String> files = (set.getFiles() != null) ? set.getFiles() : List.of();
            final List<String> classes = (set.getClasses() != null) ? set.getClasses() : List.of();

            if (files.isEmpty() && classes.isEmpty()) {
                problems.warn(setPath, "Mixin set '" + set.getName() + "' declares neither files nor classes.");
                continue;
            }

            for (int j = 0; j < files.size(); j++) {
                final String file = files.get(j);
                final String refPath = setPath + ".files[" + j + "]";
                final Path resolved = resolveRefmapPath(baseDir, file);

                try {
                    final Refmap rm = this.refmapLoader.load(resolved, problems);
                    out.add(rm);
                } catch (final IOException ioe) {
                    problems.error(refPath, "Failed to read refmap '" + file + "': " + ioe.getMessage());
                }
            }
        }

        if (out.isEmpty()) {
            problems.warn("refmaps", "No refmaps were loaded successfully.");
        }
        return List.copyOf(out);
    }
}
