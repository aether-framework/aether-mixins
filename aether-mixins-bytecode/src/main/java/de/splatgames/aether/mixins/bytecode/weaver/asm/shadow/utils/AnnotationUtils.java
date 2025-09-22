package de.splatgames.aether.mixins.bytecode.weaver.asm.shadow.utils;

import de.splatgames.aether.mixins.bytecode.weaver.asm.shadow.ShadowAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AnnotationNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AnnotationUtils {
    private static final String SHADOW_DESC = "Lde/splatgames/aether/mixins/core/api/Shadow;";

    private AnnotationUtils() {
        // utility class
    }

    @Nullable
    public static ShadowAttributes getShadowAnnotation(@Nullable final List<AnnotationNode> visibleAnnotations,
                                                       @Nullable final List<AnnotationNode> invisibleAnnotations) {
        AnnotationNode node = findAnnotation(visibleAnnotations, SHADOW_DESC);
        if (node == null) {
            node = findAnnotation(invisibleAnnotations, SHADOW_DESC);
        }
        return node != null ? parseShadow(node) : null;
    }

    @Nullable
    private static AnnotationNode findAnnotation(@Nullable final List<AnnotationNode> list, @NotNull final String desc) {
        if (list == null) {
            return null;
        }
        for (AnnotationNode a : list) {
            if (desc.equals(a.desc)) {
                return a;
            }
        }
        return null;
    }

    @NotNull
    private static ShadowAttributes parseShadow(@NotNull final AnnotationNode node) {
        Map<String, Object> values = toValueMap(node);

        // default values from @Shadow definition
        String prefix = (String) values.getOrDefault("prefix", "shadow$");
        boolean remap = (boolean) values.getOrDefault("remap", false);
        boolean optional = (boolean) values.getOrDefault("optional", false);

        return new ShadowAttributes(prefix, remap, optional);
    }

    @NotNull
    private static Map<String, Object> toValueMap(@NotNull final AnnotationNode node) {
        Map<String, Object> map = new HashMap<>();
        if (node.values == null) {
            return map;
        }
        for (int i = 0; i < node.values.size(); i += 2) {
            String key = (String) node.values.get(i);
            Object val = node.values.get(i + 1);
            map.put(key, val);
        }
        return map;
    }
}
