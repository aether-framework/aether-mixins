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

public final class AsmClassLoaderUtils {

    private AsmClassLoaderUtils() {
        // utility class, should not be instantiated
    }

    @Nullable
    public static MixinMeta readMixinMetaFromBytes(@NotNull final String mixinBinaryName, @NotNull final ClassLoader cl) {
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

    // Reads @Mixin on the class and @Inject/@Redirect on its methods from raw bytes.
    // Does NOT define/load the mixin class, so no class_value is resolved.

    public static @Nullable RefMixin scanRefMixinFromBytes(
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

    private static int readInt(@NotNull final AnnotationNode an, @NotNull final String key, final int def) {
        final Object v = annVal(an, key);
        return (v instanceof Integer i) ? i : def;
    }

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
