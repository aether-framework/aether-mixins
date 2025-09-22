package de.splatgames.aether.mixins.agent;

import de.splatgames.aether.mixins.bytecode.weaver.asm.AsmWeaver;
import de.splatgames.aether.mixins.bytecode.weaver.asm.util.MixinMeta;
import de.splatgames.aether.mixins.bytecode.weaver.hook.DefaultHookResolver;
import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Redirect;
import de.splatgames.aether.mixins.core.config.ConfigLoader;
import de.splatgames.aether.mixins.core.config.YamlConfigLoader;
import de.splatgames.aether.mixins.core.config.mixins.MixinSet;
import de.splatgames.aether.mixins.core.config.mixins.MixinsConfig;
import de.splatgames.aether.mixins.core.config.problems.ConfigProblems;
import de.splatgames.aether.mixins.core.config.refmap.JsonRefmapLoader;
import de.splatgames.aether.mixins.core.config.refmap.RefEntry;
import de.splatgames.aether.mixins.core.config.refmap.RefMixin;
import de.splatgames.aether.mixins.core.config.refmap.Refmap;
import de.splatgames.aether.mixins.core.config.refmap.RefmapLoader;
import de.splatgames.aether.mixins.core.config.runtime.RuntimeConfig;
import de.splatgames.aether.mixins.core.plan.PlannedMixin;
import de.splatgames.aether.mixins.core.plan.SelectionOptions;
import de.splatgames.aether.mixins.core.plan.WeavePlan;
import de.splatgames.aether.mixins.core.plan.WeavePlanner;
import de.splatgames.aether.mixins.core.weaver.spi.Weaver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.objectweb.asm.Opcodes.ASM9;

/**
 * Java agent entrypoint for <em>Aether Mixins</em>.
 *
 * <h2>What it does</h2>
 * <ol>
 *   <li>Discovers and loads {@code mixins.yml} (and referenced refmaps).</li>
 *   <li>Builds a global {@link WeavePlan} and slices it by target class.</li>
 *   <li>Registers a {@link ClassFileTransformer} ({@code AgentTransformer}) that only
 *       transforms classes with a plan.</li>
 *   <li>If dynamically attached and the JVM supports retransform, retransforms
 *       already loaded targets.</li>
 * </ol>
 *
 * <h2>Config discovery</h2>
 * The agent looks for configuration in this order:
 * <ol>
 *   <li>System property {@code -Daether.mixins.config=&lt;path&gt;}</li>
 *   <li>{@code ./mixins.yml} or {@code ./mixins.yaml}</li>
 *   <li>Classpath resource {@code /mixins.yml} (extracted to a temporary file)</li>
 * </ol>
 *
 * <h2>Safe-mode behavior</h2>
 * The runtime’s {@link RuntimeConfig#isSafeMode()} is respected by the transformer.
 * In safe mode, errors are logged and the original class bytes are returned. With
 * safe mode disabled, fatal weave errors will halt the JVM.
 *
 * <h2>Thread-safety</h2>
 * All state used by the transformer is held in concurrent structures
 * ({@link ConcurrentHashMap}, {@link AtomicReference}). Once registered, the
 * transformer is safe to use concurrently by the JVM’s class loading threads.
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class MixinsAgent {

    /**
     * No instantiation (static utility class).
     * <p>
     * The agent is a static utility class and must not be instantiated.
     * It is being invoked by the JVM only via its static {@code premain} or {@code agentmain}
     * methods.
     * </p>
     */
    private MixinsAgent() { /* no instantiation */ }

    /**
     * Agent entry when started with {@code -javaagent:...}.
     *
     * @param agentArgs optional agent args (currently ignored)
     * @param inst      JVM {@link Instrumentation}, never {@code null}
     * @throws Exception on fatal bootstrap errors
     */
    public static void premain(@Nullable final String agentArgs, @NotNull final Instrumentation inst) throws Exception {
        bootstrap(agentArgs, inst, /*attached*/ false);
    }

    /**
     * Agent entry when dynamically attached via the Attach API.
     *
     * @param agentArgs optional agent args (currently ignored)
     * @param inst      JVM {@link Instrumentation}, never {@code null}
     * @throws Exception on fatal bootstrap errors
     */
    public static void agentmain(@Nullable final String agentArgs, @NotNull final Instrumentation inst) throws Exception {
        bootstrap(agentArgs, inst, /*attached*/ true);
    }

    /**
     * Shared bootstrap:
     * <ol>
     *   <li>Register the {@link ClassFileTransformer} with retransformation support.</li>
     *   <li>Load YAML + refmaps + annotation-driven mixins.</li>
     *   <li>Plan, slice by target class, set runtime, and optionally retransform.</li>
     *   <li>Emit diagnostics to {@code System.err}.</li>
     * </ol>
     *
     * @param agentArgs raw agent args (unused)
     * @param inst      instrumentation handle
     * @param attached  {@code true} if called from {@link #agentmain(String, Instrumentation)}
     * @throws Exception for unrecoverable initialization errors
     */
    private static void bootstrap(@Nullable final String agentArgs,
                                  @NotNull final Instrumentation inst,
                                  final boolean attached) throws Exception {
        final ConfigProblems problems = new ConfigProblems("MixinsAgent");

        // Mutable runtime & plan state shared with the transformer
        final ConcurrentHashMap<String, WeavePlan> perClassPlans = new ConcurrentHashMap<>();
        final AtomicReference<RuntimeConfig> runtimeRef =
                new AtomicReference<>(new RuntimeConfig(problems));

        // Wire weaver & transformer (retransform enabled)
        final Weaver weaver = new AsmWeaver(new DefaultHookResolver(ClassLoader.getSystemClassLoader()));
        final ClassFileTransformer transformer = new AgentTransformer(perClassPlans, weaver, runtimeRef::get);
        inst.addTransformer(transformer, /*canRetransform*/ true);

        // 1) Load YAML config
        final Path yamlConfig = discoverConfigPath();
        final ConfigLoader cfgLoader = new YamlConfigLoader();
        final MixinsConfig cfg = cfgLoader.load(yamlConfig);
        final RuntimeConfig runtime = cfg.getRuntime();

        // 2) Load refmaps + scan classes for extra mixins (annotation-driven)
        final RefmapLoader refmapLoader = new JsonRefmapLoader();
        final List<Refmap> refmaps = new ArrayList<>();
        final List<RefMixin> extraMixins = new ArrayList<>();

        final ClassLoader cl = ClassLoader.getSystemClassLoader();

        for (int i = 0; i < cfg.getMixins().size(); i++) {
            final MixinSet set = cfg.getMixins().get(i);
            final String setPath = "mixins[" + i + "]";

            final List<String> files = (set.getFiles() != null) ? set.getFiles() : List.of();
            final List<String> classes = (set.getClasses() != null) ? set.getClasses() : List.of();

            if (files.isEmpty() && classes.isEmpty()) {
                problems.warn(setPath, "Mixin set '" + set.getName() + "' declares neither files nor classes.");
                continue;
            }

            // File-backed refmaps
            for (final String f : files) {
                final Path p = resolveToPathOrTemp(f);
                try {
                    refmaps.add(refmapLoader.load(p, problems));
                } catch (final IOException ioe) {
                    problems.error(setPath + ".files", "Failed to load refmap '" + f + "': " + ioe.getMessage());
                }
            }

            // Annotation-driven mixin classes
            for (final String cn : classes) {
                final RefMixin refScannedMixin = scanRefMixinFromBytes(cn, cl, problems, setPath);
                if (refScannedMixin == null) {
                    continue;
                }

                final MixinMeta mixinMeta = readMixinMetaFromBytes(cn, cl);
                if (mixinMeta == null || !mixinMeta.hasMixin()) {
                    problems.warn(setPath + ".classes", "Class '" + cn + "' lacks @Mixin — skipped.");
                    continue;
                }

                final var targets = new ArrayList<String>();
                targets.addAll(mixinMeta.getTargets());
                targets.addAll(mixinMeta.getValues());
                targets.removeIf(s -> s == null || s.isBlank() || "java.lang.Object".equals(s));
                final var uniqTargets = new LinkedHashSet<>(targets);
                if (uniqTargets.isEmpty()) {
                    problems.error(setPath + ".classes", "Mixin class '" + cn + "' has no targets; skipping.");
                    continue;
                }

                final RefMixin rm = new RefMixin();
                rm.setClassName(refScannedMixin.getClassName());
                rm.setTargets(new ArrayList<>(uniqTargets));
                rm.setEntries(refScannedMixin.getEntries());
                rm.setPriority(mixinMeta.getPriority());
                rm.setGroups(List.of());
                rm.setRequires(List.of());
                rm.setConflictsWith(List.of());

                rm.validate(problems, setPath + ".classes('" + cn + "')");
                extraMixins.add(rm);
            }
        }

        // Deduplicate annotation-driven mixins by class name
        if (!extraMixins.isEmpty()) {
            var dedup = new LinkedHashMap<String, RefMixin>(extraMixins.size());
            for (var m : extraMixins) dedup.put(m.getClassName(), m);
            extraMixins.clear();
            extraMixins.addAll(dedup.values());
        }

        // 3) Plan the session
        final SelectionOptions options = SelectionOptions.empty();
        final WeavePlanner planner = new WeavePlanner();
        final WeavePlan fullPlan = planner.plan(refmaps, options, problems, extraMixins);

        // 4) Slice plan per target class (internal JVM name)
        final Map<String, WeavePlan> sliced = sliceByTargetClass(fullPlan);
        perClassPlans.clear();
        perClassPlans.putAll(sliced);

        // 5) Publish runtime config for the transformer
        runtimeRef.set(runtime);

        // 6) Retransform already-loaded targets if supported
        if (inst.isRetransformClassesSupported()) {
            tryRetransformLoadedTargets(inst, perClassPlans);
        }

        // 7) Diagnostics
        for (final var p : problems.all()) {
            System.err.println("[Aether Mixins] " + p.severity() + " @ " + p.path() + " — " + p.message());
        }
    }

    /**
     * Converts a {@link Redirect} annotation on a method to a {@link RefEntry}.
     * If the annotation has no ID, a synthetic one is derived from the method name.
     *
     * @param m method with {@code @Redirect}, never {@code null}
     * @param r annotation instance, never {@code null}
     * @return a populated refmap entry
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
     * Converts an {@link Inject} annotation on a method to a {@link RefEntry}.
     * If the annotation has no ID, a synthetic one is derived from the method name.
     *
     * @param m   method with {@code @Inject}, never {@code null}
     * @param inj annotation instance, never {@code null}
     * @return a populated refmap entry
     */
    @NotNull
    private static RefEntry toRefEntry(@NotNull final Method m, @NotNull final Inject inj) {
        final RefEntry e = new RefEntry();
        e.setType(RefEntry.Type.INJECT);
        e.setMethod(inj.method());
        e.setId((inj.id() == null || inj.id().isBlank())
                ? m.getName()
                : inj.id());
        e.setOptional(inj.optional());
        e.setRemap(inj.remap());
        e.setAt(inj.at());
        return e;
    }

    /**
     * Builds a per-class plan map by restricting each {@link PlannedMixin} to exactly
     * one target class (internal name).
     *
     * @param fullPlan global plan covering all targets, never {@code null}
     * @return map {@code internalName -> per-class WeavePlan}, never {@code null}
     */
    @NotNull
    private static Map<String, WeavePlan> sliceByTargetClass(@NotNull final WeavePlan fullPlan) {
        final Map<String, List<PlannedMixin>> byTarget = new LinkedHashMap<>();

        for (final PlannedMixin pm : fullPlan.getMixins()) {
            for (final String rawTarget : pm.getTargets()) {
                final String internal = toInternalName(rawTarget);
                byTarget.computeIfAbsent(internal, k -> new ArrayList<>())
                        .add(cloneForSingleTarget(pm, internal));
            }
        }

        final Map<String, WeavePlan> result = new LinkedHashMap<>();
        for (final var e : byTarget.entrySet()) {
            result.put(e.getKey(), new WeavePlan(List.copyOf(e.getValue())));
        }
        return result;
    }

    /**
     * Creates a copy of the mixin with a single internal-name target.
     *
     * @param pm             source mixin, never {@code null}
     * @param internalTarget internal JVM name for the one target, never {@code null}
     * @return a copy of {@code pm} with a single target
     */
    @NotNull
    private static PlannedMixin cloneForSingleTarget(@NotNull final PlannedMixin pm, @NotNull final String internalTarget) {
        return new PlannedMixin(
                pm.getClassName(),
                pm.getPriority(),
                List.of(internalTarget),
                pm.getEntries(),
                pm.getGroups(),
                pm.getRequires(),
                pm.getConflictsWith()
        );
    }

    /**
     * Converts a binary name to an internal JVM name (slashes). If already internal, returns as-is.
     *
     * @param name binary or internal name, never {@code null}
     * @return internal JVM name (slash-separated), never {@code null}
     */
    @NotNull
    private static String toInternalName(@NotNull final String name) {
        return name.indexOf('/') >= 0 ? name : name.replace('.', '/');
    }

    /**
     * Locates the YAML config in the standard locations.
     *
     * @return resolved path, never {@code null}
     * @throws IllegalStateException if no configuration can be found
     */
    @NotNull
    private static Path discoverConfigPath() {
        final String prop = System.getProperty("aether.mixins.config");
        if (prop != null && !prop.isBlank()) return Path.of(prop.trim());
        final Path cwdYml = Path.of("mixins.yml");
        if (Files.isRegularFile(cwdYml)) return cwdYml;
        final Path cwdYaml = Path.of("mixins.yaml");
        if (Files.isRegularFile(cwdYaml)) return cwdYaml;

        final Path tmp = extractClasspathResource("/mixins.yml");
        if (tmp != null) return tmp;

        System.out.println("[Aether Mixins] Looked for config at:");
        System.out.println(" - System property aether.mixins.config=" + prop);
        System.out.println(" - " + cwdYml.toAbsolutePath());
        System.out.println(" - " + cwdYaml.toAbsolutePath());
        System.out.println(" - classpath resource /mixins.yml");

        throw new IllegalStateException("No mixins config found. Provide -Daether.mixins.config=<path> or place mixins.yml in the working directory.");
    }

    /**
     * Resolves a refmap reference as a file path or classpath resource.
     * Classpath resources are extracted to a temporary file.
     *
     * @param fileOrResource file system path or classpath resource, never {@code null}
     * @return a {@link Path} pointing to a readable file, never {@code null}
     * @throws IllegalStateException if neither file nor resource exists
     */
    @NotNull
    private static Path resolveToPathOrTemp(@NotNull final String fileOrResource) {
        final Path p = Path.of(fileOrResource);
        if (Files.exists(p)) return p;

        final String res = fileOrResource.startsWith("/") ? fileOrResource : "/" + fileOrResource;
        final Path tmp = extractClasspathResource(res);
        if (tmp != null) return tmp;

        throw new IllegalStateException("Refmap not found as file or resource: " + fileOrResource);
    }

    /**
     * Extracts a classpath resource to a temporary file.
     *
     * @param resource absolute resource path (e.g. {@code /mixins.yml}), never {@code null}
     * @return the temp file or {@code null} if the resource does not exist
     * @throws UncheckedIOException if extraction fails
     */
    @Nullable
    private static Path extractClasspathResource(@NotNull final String resource) {
        try (InputStream in = MixinsAgent.class.getResourceAsStream(resource)) {
            if (in == null) return null;
            final Path tmp = Files.createTempFile("aether-mixins-", "-res");
            Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return tmp;
        } catch (final IOException ioe) {
            throw new UncheckedIOException("Failed to extract resource " + resource, ioe);
        }
    }

    /**
     * Best-effort retransformation for already-loaded target classes (dynamic-attach flow).
     *
     * <p>Only classes that are both modifiable and present in {@code perClassPlans} are considered.
     * Errors are logged and ignored so bootstrap can proceed.</p>
     *
     * @param inst          JVM instrumentation
     * @param perClassPlans map of internal class name to per-class plan
     */
    private static void tryRetransformLoadedTargets(@NotNull final Instrumentation inst,
                                                    @NotNull final Map<String, WeavePlan> perClassPlans) {
        if (!inst.isRetransformClassesSupported()) {
            System.err.println("[Aether Mixins] Retransformation not supported by this JVM.");
            return;
        }
        final List<Class<?>> toRetransform = new ArrayList<>();
        for (final Class<?> c : inst.getAllLoadedClasses()) {
            if (!inst.isModifiableClass(c)) continue;
            final String internal = c.getName().replace('.', '/');
            if (perClassPlans.containsKey(internal)) {
                toRetransform.add(c);
            }
        }
        if (toRetransform.isEmpty()) return;

        try {
            inst.retransformClasses(toRetransform.toArray(new Class<?>[0]));
        } catch (final Throwable e) {
            System.err.println("[Aether Mixins] Failed to retransform some classes: " + e);
        }
    }

    /**
     * Reads {@link de.splatgames.aether.mixins.core.api.Mixin @Mixin} annotation data from the class bytes of the given class.
     *
     * @param mixinBinaryName binary name of the mixin class, never {@code null}
     * @param cl              classloader to load the class bytes from, never {@code null}
     * @return parsed mixin metadata or {@code null} if the class cannot be read
     * @throws NullPointerException if {@code mixinBinaryName} or {@code cl} is {@code null}
     * @since 0.2.0
     */
    @Nullable
    private static MixinMeta readMixinMetaFromBytes(@NotNull final String mixinBinaryName, @NotNull final ClassLoader cl) {
        final String res = mixinBinaryName.replace('.', '/') + ".class";
        try (var in = cl.getResourceAsStream(res)) {
            if (in == null) return null;
            final ClassReader cr = new ClassReader(in);
            final ClassNode node = new ClassNode(ASM9);
            cr.accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);

            if (node.visibleAnnotations == null) return new MixinMeta(false);

            AnnotationNode mixin = null;
            for (var an : node.visibleAnnotations) {
                if ("Lde/splatgames/aether/mixins/core/api/Mixin;".equals(an.desc)) {
                    mixin = an;
                    break;
                }
            }
            if (mixin == null) return new MixinMeta(false);

            final MixinMeta meta = new MixinMeta(true);

            // parse elements
            if (mixin.values != null) {
                for (int i = 0; i < mixin.values.size(); i += 2) {
                    final String k = (String) mixin.values.get(i);
                    final Object v = mixin.values.get(i + 1);

                    if ("targets".equals(k) && v instanceof List<?> lst) {
                        for (Object o : lst) if (o instanceof String s) meta.getTargets().add(s);
                    } else if ("value".equals(k) && v instanceof List<?> lst) {
                        for (Object o : lst) if (o instanceof Type t) meta.getValues().add(t.getClassName());
                    } else if ("priority".equals(k) && v instanceof Integer p) {
                        meta.setPriority(p);
                    }
                }
            }
            return meta;
        } catch (final Exception e) {
            return null;
        }
    }

    // Reads @Mixin on the class and @Inject/@Redirect on its methods from raw bytes.
    // Does NOT define/load the mixin class, so no class_value is resolved.

    /**
     * Scans a mixin class from its bytecode to extract {@link RefMixin} data.
     *
     * <p>This method does not load or define the mixin class, so any {@code class_value}
     * elements in the annotations are not resolved.</p>
     *
     * @param mixinBinaryName binary name of the mixin class, never {@code null}
     * @param cl              classloader to load the class bytes from, never {@code null}
     * @param problems        config problems collector, never {@code null}
     * @param setPath         path to the declaring mixin set for diagnostics, never {@code null}
     * @return a populated {@link RefMixin} or {@code null} if the class cannot be read or is invalid
     * @throws NullPointerException if any argument is {@code null}
     * @since 0.2.0
     */
    private static @Nullable RefMixin scanRefMixinFromBytes(
            @NotNull String mixinBinaryName,
            @NotNull ClassLoader cl,
            @NotNull ConfigProblems problems,
            @NotNull String setPath
    ) {
        final String res = mixinBinaryName.replace('.', '/') + ".class";
        final ClassNode node = new ClassNode(ASM9);
        try (var in = cl.getResourceAsStream(res)) {
            if (in == null) {
                problems.warn(setPath + ".classes", "Mixin class bytes not found: " + mixinBinaryName);
                return null;
            }
            new ClassReader(in).accept(node, ClassReader.SKIP_FRAMES);
        } catch (Exception e) {
            problems.error(setPath + ".classes", "Failed reading class bytes for '" + mixinBinaryName + "': " + e);
            return null;
        }

        final String MIXIN_DESC = "Lde/splatgames/aether/mixins/core/api/Mixin;";
        final String INJECT_DESC = "Lde/splatgames/aether/mixins/core/api/Inject;";
        final String REDIRECT_DESC = "Lde/splatgames/aether/mixins/core/api/Redirect;";

        // find @Mixin on the class
        AnnotationNode mixinAnn = null;
        if (node.visibleAnnotations != null) {
            for (var an : node.visibleAnnotations) {
                if (MIXIN_DESC.equals(an.desc)) {
                    mixinAnn = an;
                    break;
                }
            }
        }
        if (mixinAnn == null) {
            problems.warn(setPath + ".classes", "Class '" + mixinBinaryName + "' lacks @Mixin — skipped.");
            return null;
        }

        // extract targets(): String[] and value(): Type[] without loading any target classes
        final List<String> targets = new ArrayList<>();
        extractStringArray(mixinAnn, "targets", targets);
        extractTypeClassNames(mixinAnn, "value", targets);
        // dedupe/clean and reject Object
        final var uniq = new LinkedHashSet<>(targets);
        uniq.removeIf(s -> s == null || s.isBlank() || "java.lang.Object".equals(s));
        if (uniq.isEmpty()) {
            problems.error(setPath + ".classes", "Mixin class '" + mixinBinaryName + "' has no targets; skipping.");
            return null;
        }

        // collect method entries from annotations (still no class loading)
        final var entries = new ArrayList<RefEntry>();
        for (var m : node.methods) {
            if (m.visibleAnnotations == null) {
                continue;
            }
            for (var an : m.visibleAnnotations) {
                if (INJECT_DESC.equals(an.desc)) {
                    final var e = buildInjectEntryFromAnn(an, m);
                    if (e != null) {
                        entries.add(e);
                    }
                } else if (REDIRECT_DESC.equals(an.desc)) {
                    final var e = buildRedirectEntryFromAnn(an, m);
                    if (e != null) {
                        entries.add(e);
                    }
                }
            }
        }
        if (entries.isEmpty()) {
            problems.warn(setPath + ".classes", "Mixin class '" + mixinBinaryName + "' declares no @Redirect/@Inject; skipped.");
            return null;
        }

        final var rm = new RefMixin();
        rm.setClassName(node.name.replace('/', '.'));
        rm.setTargets(new ArrayList<>(uniq));
        rm.setEntries(entries);
        rm.setPriority(readInt(mixinAnn, "priority", 1000));
        rm.setGroups(List.of());
        rm.setRequires(List.of());
        rm.setConflictsWith(List.of());
        rm.validate(problems, setPath + ".classes('" + mixinBinaryName + "')");
        return rm;
    }

    /**
     * Retrieves a named element value from an annotation node.
     *
     * @param an  annotation node, never {@code null}
     * @param key element name, never {@code null}
     * @return the element value or {@code null} if not found
     * @since 0.2.0
     */
    @Nullable
    private static Object annVal(@NotNull final AnnotationNode an, @NotNull final String key) {
        if (an.values == null) {
            return null;
        }
        for (int i = 0; i < an.values.size(); i += 2) {
            if (key.equals(an.values.get(i))) {
                return an.values.get(i + 1);
            }
        }
        return null;
    }

    /**
     * Extracts a {@code String[]} annotation element value into a list.
     *
     * @param an  annotation node, never {@code null}
     * @param key element name, never {@code null}
     * @param out output list to add to, never {@code null}
     * @since 0.2.0
     */
    private static void extractStringArray(@NotNull final AnnotationNode an,
                                           @NotNull final String key,
                                           @NotNull final List<String> out) {
        final Object v = annVal(an, key);
        if (v instanceof List<?> lst) {
            for (Object o : lst)
                if (o instanceof String s) {
                    out.add(s);
                }
        }
    }

    /**
     * Extracts a {@code Class<?>[]} annotation element value into a list of class names.
     *
     * @param an  annotation node, never {@code null}
     * @param key element name, never {@code null}
     * @param out output list to add to, never {@code null}
     * @since 0.2.0
     */
    private static void extractTypeClassNames(@NotNull final AnnotationNode an,
                                              @NotNull final String key,
                                              @NotNull final List<String> out) {
        final Object v = annVal(an, key);
        if (v instanceof List<?> lst) {
            for (Object o : lst)
                if (o instanceof Type t) {
                    out.add(t.getClassName());
                }
        }
    }

    /**
     * Reads an integer annotation element value with a default.
     *
     * @param an  annotation node, never {@code null}
     * @param key element name, never {@code null}
     * @param def default value if not found or not an integer
     * @return the integer value or {@code def}
     * @since 0.2.0
     */
    private static int readInt(@NotNull final AnnotationNode an, @NotNull final String key, final int def) {
        final Object v = annVal(an, key);
        return (v instanceof Integer i) ? i : def;
    }

    /**
     * Builds a {@link RefEntry} of type {@link RefEntry.Type#INJECT} from an {@link Inject} annotation node.
     *
     * @param inj annotation node, never {@code null}
     * @param m   method node where the annotation is present, never {@code null}
     * @return a populated refmap entry or {@code null} if the annotation is invalid
     * @since 0.2.0
     */
    private static RefEntry buildInjectEntryFromAnn(
            AnnotationNode inj, MethodNode m) {
        final Object method = annVal(inj, "method");
        if (!(method instanceof String ms) || ms.isBlank()) {
            return null;
        }
        final var e = new RefEntry();
        e.setType(RefEntry.Type.INJECT);
        e.setMethod(ms);
        final Object id = annVal(inj, "id");
        e.setId((id instanceof String s && !s.isBlank()) ? s : m.name);
        e.setOptional(Boolean.TRUE.equals(annVal(inj, "optional")));
        e.setRemap(Boolean.TRUE.equals(annVal(inj, "remap")));
        final Object at = annVal(inj, "at"); // enum as String[]{desc,name}
        if (at instanceof String[] pair && pair.length == 2) {
            try {
                e.setAt(Inject.At.valueOf(pair[1]));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        } else {
            e.setAt(Inject.At.HEAD);
        }
        return e;
    }

    /**
     * Builds a {@link RefEntry} of type {@link RefEntry.Type#REDIRECT} from a {@link Redirect} annotation node.
     *
     * @param red annotation node, never {@code null}
     * @param m   method node where the annotation is present, never {@code null}
     * @return a populated refmap entry or {@code null} if the annotation is invalid
     * @since 0.2.0
     */
    @Nullable
    private static RefEntry buildRedirectEntryFromAnn(
            @NotNull final AnnotationNode red,
            @NotNull final MethodNode m) {
        final Object method = annVal(red, "method");
        if (!(method instanceof String ms) || ms.isBlank()) {
            return null;
        }
        final Object co = annVal(red, "callOwner");
        final Object cn = annVal(red, "callName");
        final Object cd = annVal(red, "callDesc");
        if (!(co instanceof String) || !(cn instanceof String) || !(cd instanceof String)) {
            return null;
        }

        final var e = new RefEntry();
        e.setType(RefEntry.Type.REDIRECT);
        e.setMethod(ms);
        final Object id = annVal(red, "id");
        e.setId((id instanceof String s && !s.isBlank()) ? s : m.name);
        e.setOptional(Boolean.TRUE.equals(annVal(red, "optional")));
        e.setRemap(Boolean.TRUE.equals(annVal(red, "remap")));
        e.setCallOwner((String) co);
        e.setCallName((String) cn);
        e.setCallDesc((String) cd);

        final Object kind = annVal(red, "kind"); // enum as String[]{desc,name}
        if (kind instanceof String[] pair && pair.length == 2) {
            try {
                e.setKind(Redirect.InvokeKind.valueOf(pair[1]));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        } else {
            e.setKind(Redirect.InvokeKind.AUTO);
        }

        final Object ord = annVal(red, "ordinal");
        if (ord instanceof Integer i) {
            e.setOrdinal(i);
        }
        return e;
    }
}
