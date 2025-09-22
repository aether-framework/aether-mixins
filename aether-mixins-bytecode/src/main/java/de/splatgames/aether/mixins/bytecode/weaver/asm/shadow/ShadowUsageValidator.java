package de.splatgames.aether.mixins.bytecode.weaver.asm.shadow;

import de.splatgames.aether.mixins.bytecode.weaver.asm.ClassWork;
import de.splatgames.aether.mixins.bytecode.weaver.asm.shadow.utils.AnnotationUtils;
import de.splatgames.aether.mixins.bytecode.weaver.hook.ResolvedHook;
import de.splatgames.aether.mixins.core.config.problems.ConfigProblems;
import de.splatgames.aether.mixins.core.weaver.spi.WeaveRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.objectweb.asm.Opcodes.ACC_ABSTRACT;
import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ASM9;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.PUTSTATIC;

/**
 * Pre-weave validator that enforces correct and safe @Shadow usage.
 *
 * <ul>
 *   <li>Static hooks must NOT use @Shadow (until access bridges are implemented).</li>
 *   <li>Writes to final target fields are forbidden unless explicitly allowed via @Mutable + runtime flag.</li>
 *   <li>Optional shadows that are unresolved MUST NOT be used.</li>
 * </ul>
 * <p>
 * This validator is designed to run once per target class before installing ASM adapters.
 *
 * @author Erik Pförtner
 * @since 0.2.0
 */
public final class ShadowUsageValidator {

    private static final String CTX_PREFIX = "shadow-use/";
    private static final String MUTABLE_DESC = "Lde/splatgames/aether/mixins/core/api/Mutable;";

    private ShadowUsageValidator() {
    }

    public static void validate(@NotNull final ClassWork work,
                                @NotNull final WeaveRequest request,
                                @NotNull final ConfigProblems problems,
                                @NotNull final String internalName) {

        final boolean allowMutableFinalWrites = request.runtime().isAllowFinalFieldWeakening();

        // Load target class node (needed for resolving target field/method properties like final/static)
        final ClassNode targetNode = loadClassNode(request, internalName, problems, "resolve/" + internalName);
        if (targetNode == null) return;

        // Collect all hooks for this target
        final List<ResolvedHook> hooks = new ArrayList<>();
        work.getInjects().values().forEach(list -> list.forEach(s -> hooks.add(s.hook())));
        work.getRedirects().values().forEach(list -> list.forEach(s -> hooks.add(s.hook())));

        // Early out
        if (hooks.isEmpty()) return;

        // Per mixin cache to avoid re-parsing the same mixin classes
        final Map<String, ClassNode> mixinCache = new HashMap<>();
        final Map<String, ShadowRegistry> shadowCache = new HashMap<>();
        final Map<String, Map<String, MethodNode>> methodCache = new HashMap<>();

        for (ResolvedHook hook : hooks) {
            final String mixinOwner = hook.owner();
            final ClassNode mixinNode = mixinCache.computeIfAbsent(mixinOwner, owner -> {
                ClassNode cn = loadClassNode(request, owner, problems, "resolve/" + internalName + "/" + owner);
                return cn == null ? new ClassNode() : cn;
            });
            if (mixinNode.name == null) {
                // failed to load, errors already reported
                continue;
            }

            // Find the hook method in the mixin
            Map<String, MethodNode> hookMap = methodCache.computeIfAbsent(mixinOwner, _unused -> {
                Map<String, MethodNode> m = new HashMap<>();
                if (mixinNode.methods != null) {
                    for (MethodNode mn : mixinNode.methods) {
                        m.put(mn.name + mn.desc, mn);
                    }
                }
                return m;
            });
            final String path = CTX_PREFIX + internalName + "/" + hook.name() + hook.desc();
            final MethodNode hookMethod = hookMap.get(hook.name() + hook.desc());

            if (hookMethod == null) {
                problems.error(path, "Hook method not found in mixin: " +
                        mixinOwner + "." + hook.name() + hook.desc());
                continue;
            }

            // Build quick @Shadow registry for this mixin: map mixin-name+desc -> ShadowMeta
            ShadowRegistry reg = shadowCache.computeIfAbsent(mixinOwner, _owner -> {
                Map<String, ShadowMeta> shadowMethods = new HashMap<>();
                Map<String, ShadowMeta> shadowFields = new HashMap<>();
                collectShadows(mixinNode, targetNode, shadowMethods, shadowFields, problems,
                        "shadow/" + internalName + "/" + mixinOwner);
                return new ShadowRegistry(shadowMethods, shadowFields);
            });
            final Map<String, ShadowMeta> shadowMethods = reg.methods();
            final Map<String, ShadowMeta> shadowFields = reg.fields();

            if (hookMethod.instructions == null) {
                continue; // defensiv
            }

            for (AbstractInsnNode insn = hookMethod.instructions.getFirst(); insn != null; insn = insn.getNext()) {

                // Field usage?
                if (insn instanceof FieldInsnNode fin && fin.owner.equals(mixinOwner)) {
                    final ShadowMeta meta = shadowFields.get(fin.name + fin.desc);
                    if (meta != null) {
                        // Writes to final target field forbidden unless @Mutable + runtime flag
                        if (fin.getOpcode() == PUTFIELD || fin.getOpcode() == PUTSTATIC) {
                            if (meta.targetFinal() && !(meta.mutable() && allowMutableFinalWrites)) {
                                problems.error(path,
                                        "Write to final @Shadow field is forbidden: " + printable(meta, fin.desc));
                            }
                        }
                    }
                }

                // Method usage?
                if (insn instanceof MethodInsnNode min && min.owner.equals(mixinOwner)) {
                    final ShadowMeta meta = shadowMethods.get(min.name + min.desc);
                    if (meta != null) {
                        // Optional unresolved used? -> error
                        if (!meta.resolved() && !meta.optional()) {
                            problems.error(path,
                                    "Unresolved required @Shadow method used: " + printable(meta, min.desc));
                        } else if (!meta.resolved()) {
                            problems.error(path,
                                    "Optional @Shadow method is missing in target but used: " + printable(meta, min.desc));
                        }
                    }
                }
            }
        }
    }

    @Nullable
    private static ClassNode loadClassNode(@NotNull final WeaveRequest request,
                                           @NotNull final String internalName,
                                           @NotNull final ConfigProblems problems,
                                           @NotNull final String ctx) {
        try {
            final byte[] bytes = request.source().getClassBytes(internalName);
            if (bytes == null) {
                problems.error(ctx, "Class not found: " + internalName);
                return null;
            }
            final ClassNode cn = new ClassNode(ASM9);
            new ClassReader(bytes).accept(cn, 0);
            return cn;
        } catch (Exception e) {
            problems.error(ctx, "Failed to read class '" + internalName + "': " + e.getMessage());
            return null;
        }
    }

    @Nullable
    private static FieldNode findField(@NotNull final ClassNode cn,
                                       @NotNull final String name,
                                       @NotNull final String desc) {
        if (cn.fields == null) return null;
        for (FieldNode f : cn.fields) {
            if (name.equals(f.name) && desc.equals(f.desc)) return f;
        }
        return null;
    }

    @NotNull
    private static String printable(@NotNull final ShadowMeta meta, @NotNull final String desc) {
        return (meta.staticMember() ? "static " : "") + meta.strippedName() + " " + desc;
    }

    private static void collectShadows(@NotNull final ClassNode mixin,
                                       @NotNull final ClassNode target,
                                       @NotNull final Map<String, ShadowMeta> shadowMethods,
                                       @NotNull final Map<String, ShadowMeta> shadowFields,
                                       @NotNull final ConfigProblems problems,
                                       @NotNull final String ctx) {

        if (mixin.fields != null) {
            for (FieldNode f : mixin.fields) {
                ShadowAttributes sa = AnnotationUtils.getShadowAnnotation(f.visibleAnnotations, f.invisibleAnnotations);
                if (sa == null) continue;

                final String prefix = sa.prefix(); // respect explicit "" (exact match)
                if (!prefix.isEmpty() && !f.name.startsWith(prefix)) {
                    problems.error(ctx, "@Shadow field name '" + f.name + "' does not start with prefix '" + prefix + "'");
                    continue;
                }

                final String stripped = prefix.isEmpty() ? f.name : f.name.substring(prefix.length());
                final FieldNode tf = findField(target, stripped, f.desc);
                final boolean resolved = tf != null;
                final boolean targetFinal = tf != null && (tf.access & ACC_FINAL) != 0;

                final boolean mutable = hasAnnotation(f.visibleAnnotations, MUTABLE_DESC)
                        || hasAnnotation(f.invisibleAnnotations, MUTABLE_DESC);

                final ShadowMeta meta = new ShadowMeta(
                        /* static? */ (f.access & ACC_STATIC) != 0,
                        stripped,
                        sa.optional(),
                        resolved,
                        targetFinal,
                        mutable
                );

                // Key by MIXIN name+desc (so we can catch uses BEFORE rewrite)
                shadowFields.put(f.name + f.desc, meta);
            }
        }

        if (mixin.methods != null) {
            for (MethodNode m : mixin.methods) {
                ShadowAttributes sa = AnnotationUtils.getShadowAnnotation(m.visibleAnnotations, m.invisibleAnnotations);
                if (sa == null) continue;

                final String prefix = sa.prefix();

                if (!prefix.isEmpty() && !m.name.startsWith(prefix)) {
                    problems.error(ctx, "@Shadow method name '" + m.name + "' does not start with prefix '" + prefix + "'");
                    continue;
                }

                final String stripped = prefix.isEmpty() ? m.name : m.name.substring(prefix.length());
                final boolean resolved = target.methods.stream().anyMatch(tm -> tm.name.equals(stripped) && tm.desc.equals(m.desc));

                final ShadowMeta meta = new ShadowMeta(
                        (m.access & ACC_STATIC) != 0,
                        stripped,
                        sa.optional(),
                        resolved,
                        false,
                        false
                );

                shadowMethods.put(m.name + m.desc, meta);

                // Optional: warn if method has a body (should be abstract/empty)
                if ((m.access & ACC_ABSTRACT) == 0 && m.instructions != null && m.instructions.size() > 0) {
                    problems.warn(ctx, "@Shadow method should be abstract/empty: " + m.name + m.desc);
                }
            }
        }
    }

    private static boolean hasAnnotation(@Nullable final List<AnnotationNode> list, @NotNull final String desc) {
        if (list == null) return false;
        for (AnnotationNode a : list) if (desc.equals(a.desc)) return true;
        return false;
    }
}
