package de.splatgames.aether.mixins.bytecode.weaver.asm.shadow;

import de.splatgames.aether.mixins.bytecode.weaver.asm.shadow.utils.AnnotationUtils;
import de.splatgames.aether.mixins.core.config.problems.ConfigProblems;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashMap;
import java.util.Map;

/**
 * Index of resolved {@code @Shadow} members for a single mixin/target pair.
 *
 * <p>This utility scans a mixin {@link ClassNode} for fields and methods annotated with
 * the Aether Mixins {@code @Shadow} annotation, attempts to resolve those members
 * against a concrete target {@link ClassNode}, and records the outcome as
 * {@link ShadowBinding} entries that can later be consulted during pre-merge and
 * bytecode rewriting.</p>
 *
 * <h2>Resolution model</h2>
 * <ul>
 *   <li>For each {@code @Shadow}-annotated field or method in the mixin, the declared
 *       {@code prefix} is stripped from the mixin member name to obtain the
 *       <em>stripped name</em>. The stripped name is then matched against the target
 *       member name using the same descriptor.</li>
 *   <li>If the target member exists, the binding is marked {@linkplain ShadowBinding#isResolved() resolved}.
 *       Otherwise, if the {@code @Shadow} is not {@code optional}, an error is reported to
 *       {@link ConfigProblems}.</li>
 *   <li>For fields, a static-vs-instance mismatch between mixin and target is reported as an error.</li>
 * </ul>
 *
 * <h2>Keying</h2>
 * <p>Bindings are stored in two maps (fields/methods), keyed by the concatenation
 * {@code strippedName + desc}. Lookups must therefore supply the <em>stripped</em> name
 * (after any configured prefix) and the exact JVM descriptor.</p>
 *
 * <h2>Prefix handling</h2>
 * <ul>
 *   <li>Methods: if the {@code @Shadow} prefix is blank, {@code "shadow$"} is assumed.</li>
 *   <li>Fields: if the {@code @Shadow} prefix is blank, an empty prefix is assumed
 *       (i.e., the mixin name must already match the target name).</li>
 * </ul>
 *
 * <p>This asymmetry reflects common conventions for method shadows vs. field shadows and matches
 * the current Aether Mixins design.</p>
 *
 * <h2>Thread-safety</h2>
 * <p>Instances are not thread-safe. Create and use a {@code ShadowMap} per weaving pass.</p>
 *
 * @author Erik Pförtner
 * @see ShadowBinding
 * @see ShadowAttributes
 * @see AnnotationUtils
 * @see ClassNode
 * @see FieldNode
 * @see MethodNode
 * @since 0.2.0
 */
public final class ShadowMap {
    /**
     * Field bindings indexed by {@code strippedName + desc}.
     *
     * <p>Each value describes the kind, static flag, stripped name, descriptor, optional flag,
     * and resolution status of the shadowed field.</p>
     */
    private final Map<String, ShadowBinding> fields = new HashMap<>();

    /**
     * Method bindings indexed by {@code strippedName + desc}.
     *
     * <p>Each value describes the kind, static flag, stripped name, descriptor, optional flag,
     * and resolution status of the shadowed method.</p>
     */
    private final Map<String, ShadowBinding> methods = new HashMap<>();

    /**
     * Non-instantiable from outside; use {@link #from(ClassNode, ClassNode, ConfigProblems, String)}.
     */
    private ShadowMap() {
        // utility class, not instantiable
    }

    /**
     * Builds a {@code ShadowMap} by scanning a mixin class for {@code @Shadow}-annotated
     * fields and methods and resolving them against a target class.
     *
     * <p>For each annotated member the following checks are performed:</p>
     * <ul>
     *   <li><b>Prefix check:</b> the mixin member name must start with the declared {@code prefix}.
     *       If not, an error is reported and the member is skipped.</li>
     *   <li><b>Resolution:</b> the target is searched for a member with
     *       {@code name = stripped(mixinName, prefix)} and an identical descriptor.</li>
     *   <li><b>Required presence:</b> if the member is not found and the shadow is not marked
     *       {@code optional}, an error is reported.</li>
     *   <li><b>Static-ness (fields only):</b> a static/instance mismatch between mixin and target
     *       is reported as an error.</li>
     * </ul>
     *
     * <p>Bindings are inserted into the returned map under the key
     * {@code strippedName + desc}. For methods, if the shadow's prefix is blank, the
     * implicit default {@code "shadow$"} is used; for fields, a blank prefix is treated as
     * an empty string.</p>
     *
     * @param mixin       the mixin class to scan; must not be {@code null}
     * @param target      the target class to resolve against; must not be {@code null}
     * @param problems    diagnostics sink for reporting resolution and consistency errors; must not be {@code null}
     * @param contextPath human-readable context used to qualify diagnostics (e.g. {@code "shadow/<owner>/<mixinOwner>"});
     *                    must not be {@code null}
     * @return a non-null {@code ShadowMap} containing bindings for all {@code @Shadow} members encountered
     */
    @NotNull
    public static ShadowMap from(@NotNull final ClassNode mixin,
                                 @NotNull final ClassNode target,
                                 @NotNull final ConfigProblems problems,
                                 @NotNull final String contextPath) {

        ShadowMap map = new ShadowMap();

        if (mixin.fields != null) {
            for (FieldNode field : mixin.fields) {
                var shadow = AnnotationUtils.getShadowAnnotation(field.visibleAnnotations, field.invisibleAnnotations);
                if (shadow == null) {
                    continue;
                }

                // Explicit blank => empty prefix for fields
                String prefix = shadow.prefix().isBlank() ? "" : shadow.prefix();
                if (!field.name.startsWith(prefix)) {
                    problems.error(contextPath,
                            "@Shadow field name '" + field.name + "' does not start with prefix '" + prefix + "'");
                    continue;
                }

                String stripped = field.name.substring(prefix.length());

                FieldNode targetField = target.fields.stream()
                        .filter(f -> f.name.equals(stripped) && f.desc.equals(field.desc))
                        .findFirst()
                        .orElse(null);

                boolean resolved = targetField != null;

                if (!resolved && !shadow.optional()) {
                    problems.error(contextPath, "@Shadow field not found in target: " + stripped + " " + field.desc);
                }

                if (resolved) {
                    boolean mixinStatic = (field.access & Opcodes.ACC_STATIC) != 0;
                    boolean targetStatic = (targetField.access & Opcodes.ACC_STATIC) != 0;
                    if (mixinStatic != targetStatic) {
                        problems.error(contextPath, "@Shadow static mismatch for field: " + stripped);
                    }
                }

                boolean mutable = AnnotationUtils.hasMutableAnnotation(field.visibleAnnotations, field.invisibleAnnotations);

                map.fields.put(stripped + field.desc, new ShadowBinding(
                        ShadowBinding.Kind.FIELD,
                        (field.access & Opcodes.ACC_STATIC) != 0,
                        stripped,
                        field.desc,
                        shadow.optional(),
                        resolved,
                        mutable
                ));
            }
        }

        if (mixin.methods != null) {
            for (MethodNode method : mixin.methods) {
                var shadow = AnnotationUtils.getShadowAnnotation(method.visibleAnnotations, method.invisibleAnnotations);
                if (shadow == null) {
                    continue;
                }

                // Explicit blank => empty prefix for fields
                String prefix = shadow.prefix().isBlank() ? "" : shadow.prefix();
                if (!method.name.startsWith(prefix)) {
                    problems.error(contextPath,
                            "@Shadow method name '" + method.name + "' does not start with prefix '" + prefix + "'");
                    continue;
                }

                String stripped = method.name.substring(prefix.length());

                MethodNode targetMethod = target.methods.stream()
                        .filter(m -> m.name.equals(stripped) && m.desc.equals(method.desc))
                        .findFirst()
                        .orElse(null);

                boolean resolved = targetMethod != null;

                if (!resolved && !shadow.optional()) {
                    problems.error(contextPath, "@Shadow method not found in target: " + stripped + method.desc);
                }

                boolean mutable = AnnotationUtils.hasMutableAnnotation(method.visibleAnnotations, method.invisibleAnnotations);

                map.methods.put(stripped + method.desc, new ShadowBinding(
                        ShadowBinding.Kind.METHOD,
                        (method.access & Opcodes.ACC_STATIC) != 0,
                        stripped,
                        method.desc,
                        shadow.optional(),
                        resolved,
                        mutable
                ));
            }
        }

        return map;
    }

    /**
     * Looks up a field binding by stripped name and descriptor.
     *
     * <p>The {@code name} parameter must be the <em>stripped</em> name (i.e., the mixin
     * field name after removing the {@code @Shadow} prefix).</p>
     *
     * @param name the stripped field name; must not be {@code null}
     * @param desc the JVM field descriptor (e.g., {@code Ljava/lang/String;}); must not be {@code null}
     * @return the {@link ShadowBinding} for the field, or {@code null} if no binding exists
     */
    @Nullable
    public ShadowBinding field(@NotNull final String name, @NotNull final String desc) {
        return this.fields.get(name + desc);
    }

    /**
     * Looks up a method binding by stripped name and descriptor.
     *
     * <p>The {@code name} parameter must be the <em>stripped</em> name (i.e., the mixin
     * method name after removing the {@code @Shadow} prefix).</p>
     *
     * @param name the stripped method name; must not be {@code null}
     * @param desc the JVM method descriptor (e.g., {@code (I)Ljava/lang/String;}); must not be {@code null}
     * @return the {@link ShadowBinding} for the method, or {@code null} if no binding exists
     */
    @Nullable
    public ShadowBinding method(@NotNull final String name, @NotNull final String desc) {
        return this.methods.get(name + desc);
    }
}
