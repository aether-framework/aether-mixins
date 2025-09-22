package de.splatgames.aether.mixins.bytecode.weaver.asm.loader.utils;

import de.splatgames.aether.mixins.bytecode.weaver.asm.util.MixinMeta;
import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Redirect;
import de.splatgames.aether.mixins.core.config.problems.ConfigProblems;
import de.splatgames.aether.mixins.core.config.refmap.RefEntry;
import de.splatgames.aether.mixins.core.config.refmap.RefMixin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static org.objectweb.asm.Opcodes.ASM9;

/**
 * ASM-based utilities for reading mixin metadata and reference information directly from class bytes.
 *
 * <p>This utility avoids defining or loading the mixin classes. Instead, it reads raw bytecode
 * from a {@link ClassLoader} resource stream and parses annotations with ASM, which is both
 * faster and safer for static analysis during configuration loading.</p>
 *
 * <h2>Capabilities</h2>
 * <ul>
 *   <li>Lightweight detection of {@code @Mixin} presence and extraction of basic metadata
 *       (targets, explicit {@code value} types, priority) via {@link #readMixinMetaFromBytes(String, ClassLoader)}.</li>
 *   <li>Deeper scan for method-level {@code @Inject} and {@code @Redirect} annotations without
 *       classloading, producing a structured {@link RefMixin} suitable for refmap generation via
 *       {@link #scanRefMixinFromBytes(String, ClassLoader, ConfigProblems, String)}.</li>
 * </ul>
 *
 * <h2>Error handling</h2>
 * <ul>
 *   <li>Non-fatal issues are reported to {@link ConfigProblems} (for the scanning method),
 *       allowing callers to aggregate diagnostics. The simpler meta reader returns {@code null}
 *       for missing classes or I/O errors.</li>
 *   <li>All I/O is best-effort; failure to obtain a resource stream is treated as "not found".</li>
 * </ul>
 *
 * <h2>Thread-safety</h2>
 * <p>This class is stateless and thread-safe.</p>
 *
 * @author Erik Pförtner
 * @see MixinMeta
 * @see RefMixin
 * @see RefEntry
 * @see Inject
 * @see Redirect
 * @see ClassReader
 * @since 0.2.0
 */
public final class AsmClassLoaderUtils {

    /**
     * Utility class; prevent instantiation.
     */
    private AsmClassLoaderUtils() {
        // utility class, should not be instantiated
    }

    /**
     * Reads minimal {@code @Mixin} metadata from class bytes without loading the class.
     *
     * <p>Only top-level class annotations are parsed; method bodies and frames are skipped.
     * When the class is not available on the given {@link ClassLoader}, this method returns {@code null}.
     * If present, the returned {@link MixinMeta} contains:</p>
     *
     * <ul>
     *   <li>{@code hasMixin} — whether {@code @Mixin} is present,</li>
     *   <li>{@code targets} — string class names from {@code targets()},</li>
     *   <li>{@code values} — string class names derived from {@code value()} (i.e., {@link Type} entries),</li>
     *   <li>{@code priority} — the declared priority or the default (1000).</li>
     * </ul>
     *
     * @param mixinBinaryName the binary name of the mixin (e.g., {@code com.example.MyMixin}); must not be {@code null}
     * @param cl              the loader used to resolve the {@code .class} resource; must not be {@code null}
     * @return a populated {@link MixinMeta} if bytes were available (with {@code hasMixin=true} only if {@code @Mixin} exists),
     * {@code null} if the class bytes could not be found or an error occurred
     */
    @Nullable
    public static MixinMeta readMixinMetaFromBytes(@NotNull final String mixinBinaryName,
                                                   @NotNull final ClassLoader cl) {
        final String res = mixinBinaryName.replace('.', '/') + ".class";
        try (var in = cl.getResourceAsStream(res)) {
            if (in == null) {
                return null;
            }
            final ClassReader cr = new ClassReader(in);
            final ClassNode node = new ClassNode(ASM9);
            cr.accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);

            if (node.visibleAnnotations == null) {
                return new MixinMeta(false);
            }

            AnnotationNode mixin = null;
            for (var an : node.visibleAnnotations) {
                if ("Lde/splatgames/aether/mixins/core/api/Mixin;".equals(an.desc)) {
                    mixin = an;
                    break;
                }
            }
            if (mixin == null) {
                return new MixinMeta(false);
            }

            final MixinMeta meta = new MixinMeta(true);

            // parse elements
            if (mixin.values != null) {
                for (int i = 0; i < mixin.values.size(); i += 2) {
                    final String k = (String) mixin.values.get(i);
                    final Object v = mixin.values.get(i + 1);

                    if ("targets".equals(k) && v instanceof List<?> lst) {
                        for (Object o : lst)
                            if (o instanceof String s) {
                                meta.getTargets().add(s);
                            }
                    } else if ("value".equals(k) && v instanceof List<?> lst) {
                        for (Object o : lst)
                            if (o instanceof Type t) {
                                meta.getValues().add(t.getClassName());
                            }
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

    /**
     * Scans a mixin class from raw bytes and produces a {@link RefMixin} model for configuration/refmap use.
     *
     * <p>This method parses:</p>
     * <ul>
     *   <li>{@code @Mixin} on the class to extract target class names from {@code targets()} and {@code value()}.</li>
     *   <li>Method-level {@code @Inject} and {@code @Redirect} annotations, mapping them into {@link RefEntry} items
     *       without resolving or loading any referenced types.</li>
     * </ul>
     *
     * <p>Diagnostics are emitted to {@code problems}. Missing or malformed details are generally treated as
     * non-fatal unless they would produce an unusable result, in which case {@code null} is returned.</p>
     *
     * @param mixinBinaryName the binary name of the mixin (e.g., {@code com.example.MyMixin}); must not be {@code null}
     * @param cl              the class loader to read class bytes from; must not be {@code null}
     * @param problems        a sink for warnings and errors encountered during parsing; must not be {@code null}
     * @param setPath         a logical path used to contextualize diagnostics (e.g., config set path); must not be {@code null}
     * @return a {@link RefMixin} with extracted targets and entries, or {@code null} if the class could not be read,
     * lacks {@code @Mixin}, or has no usable entries
     * @see RefEntry
     * @see Inject
     * @see Redirect
     */
    public static @Nullable RefMixin scanRefMixinFromBytes(@NotNull String mixinBinaryName,
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
     * Looks up a value in an {@link AnnotationNode}'s key/value pairs.
     *
     * @param an  the annotation node; must not be {@code null}
     * @param key the element name to search for; must not be {@code null}
     * @return the associated value or {@code null} if absent
     */
    @Nullable
    private static Object annVal(@NotNull final AnnotationNode an,
                                 @NotNull final String key) {
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
     * Extracts a {@code String[]} element from an annotation into a list accumulator.
     *
     * @param an  the annotation node; must not be {@code null}
     * @param key the element name; must not be {@code null}
     * @param out destination list to which discovered strings are appended; must not be {@code null}
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
     * Extracts a {@code Type[]} element from an annotation and appends each element's class name to the list.
     *
     * @param an  the annotation node; must not be {@code null}
     * @param key the element name; must not be {@code null}
     * @param out destination list receiving class names; must not be {@code null}
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
     * Reads an integer-valued annotation element, returning a default when missing.
     *
     * @param an  the annotation node; must not be {@code null}
     * @param key the element name; must not be {@code null}
     * @param def the default value to return when the element is missing or not an integer
     * @return the integer value or {@code def} if not present
     */
    private static int readInt(@NotNull final AnnotationNode an,
                               @NotNull final String key,
                               final int def) {
        final Object v = annVal(an, key);
        return (v instanceof Integer i) ? i : def;
    }

    /**
     * Builds a {@link RefEntry} for an {@code @Inject}-annotated method.
     *
     * <p>Populates the entry with method signature, id (defaulting to the method name when absent),
     * optional/remap flags, and the desired {@link Inject.At} location. If invalid or insufficient
     * data is present, {@code null} is returned.</p>
     *
     * @param inj the {@code @Inject} annotation node; must not be {@code null}
     * @param m   the method node from which the annotation was read; must not be {@code null}
     * @return a populated {@link RefEntry} or {@code null} if the annotation is malformed
     */
    @Nullable
    private static RefEntry buildInjectEntryFromAnn(@NotNull final AnnotationNode inj,
                                                    @NotNull final MethodNode m) {
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
     * Builds a {@link RefEntry} for a {@code @Redirect}-annotated method.
     *
     * <p>Populates the entry with handler method signature, call-site owner/name/desc,
     * invocation kind, ordinal, and flags. Returns {@code null} if required elements are missing
     * or malformed.</p>
     *
     * @param red the {@code @Redirect} annotation node; must not be {@code null}
     * @param m   the method node from which the annotation was read; must not be {@code null}
     * @return a populated {@link RefEntry} or {@code null} if the annotation is malformed
     */
    @Nullable
    private static RefEntry buildRedirectEntryFromAnn(@NotNull final AnnotationNode red,
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
