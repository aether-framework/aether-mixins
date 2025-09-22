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

public final class ShadowMap {
    private final Map<String, ShadowBinding> fields = new HashMap<>();
    private final Map<String, ShadowBinding> methods = new HashMap<>();

    private ShadowMap() {
        // utility class, not instantiable
    }

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

                String prefix = shadow.prefix().isBlank() ? "" : shadow.prefix();
                if (!field.name.startsWith(prefix)) {
                    problems.error(contextPath, "@Shadow field name '" + field.name + "' does not start with prefix '" + prefix + "'");
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

                map.fields.put(stripped + field.desc, new ShadowBinding(
                        ShadowBinding.Kind.FIELD,
                        (field.access & Opcodes.ACC_STATIC) != 0,
                        stripped,
                        field.desc,
                        shadow.optional(),
                        resolved
                ));
            }
        }

        if (mixin.methods != null) {
            for (MethodNode method : mixin.methods) {
                var shadow = AnnotationUtils.getShadowAnnotation(method.visibleAnnotations, method.invisibleAnnotations);
                if (shadow == null) {
                    continue;
                }

                String prefix = shadow.prefix().isBlank() ? "shadow$" : shadow.prefix();
                if (!method.name.startsWith(prefix)) {
                    problems.error(contextPath, "@Shadow method name '" + method.name + "' does not start with prefix '" + prefix + "'");
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

                map.methods.put(stripped + method.desc, new ShadowBinding(
                        ShadowBinding.Kind.METHOD,
                        (method.access & Opcodes.ACC_STATIC) != 0,
                        stripped,
                        method.desc,
                        shadow.optional(),
                        resolved
                ));
            }
        }

        return map;
    }

    @Nullable
    public ShadowBinding field(@NotNull final String name, @NotNull final String desc) {
        return this.fields.get(name + desc);
    }

    @Nullable
    public ShadowBinding method(@NotNull final String name, @NotNull final String desc) {
        return this.methods.get(name + desc);
    }
}
