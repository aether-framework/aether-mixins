package de.splatgames.aether.mixins.bytecode.weaver.asm.shadow.utils;

import de.splatgames.aether.mixins.bytecode.weaver.asm.shadow.ShadowAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AnnotationNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utilities for reading and interpreting ASM {@link AnnotationNode} instances related to
 * the Aether Mixins {@code @Shadow} annotation.
 *
 * <p>This helper provides a single, focused entry-point
 * {@link #getShadowAnnotation(List, List)} which searches both the visible and invisible
 * annotation lists of a class member (field or method), and—if present—parses the
 * annotation's element values into a strongly-typed {@link ShadowAttributes} record.</p>
 *
 * <h2>Parsing rules</h2>
 * <ul>
 *   <li>Both visible and invisible annotations are considered; visible has priority.</li>
 *   <li>Missing elements fall back to the defaults defined by the {@code @Shadow} API:
 *       <ul>
 *         <li>{@code prefix = "shadow$"}</li>
 *         <li>{@code remap = false}</li>
 *         <li>{@code optional = false}</li>
 *       </ul>
 *   </li>
 *   <li>Only the {@code @Shadow} annotation is recognized; other annotations are ignored.</li>
 * </ul>
 *
 * <p>The returned {@link ShadowAttributes} is suitable for downstream validation and
 * rewrite steps (for example, building {@code ShadowMeta} and {@code ShadowBinding}
 * structures, or deciding how to neutralize optional but missing shadows).</p>
 *
 * @author Erik Pförtner
 * @since 0.2.0
 * @see ShadowAttributes
 */
public final class AnnotationUtils {
    /**
     * Descriptor of the Aether Mixins @Mutable annotation.
     * Used to detect whether a shadowed member is mutable (field or non-final method).
     */
    private static final String MUTABLE_DESC = "Lde/splatgames/aether/mixins/core/api/Mutable;";

    /**
     * Descriptor literal for the Aether Mixins {@code @Shadow} annotation.
     *
     * <p>Used to match {@link AnnotationNode#desc} values when scanning visible or
     * invisible annotation lists.</p>
     */
    private static final String SHADOW_DESC = "Lde/splatgames/aether/mixins/core/api/Shadow;";

    /**
     * Non-instantiable utility class.
     */
    private AnnotationUtils() {
        // utility class
    }

    /**
     * Searches the given annotation lists for a {@code @Shadow} annotation and, if found,
     * parses its element values into a {@link ShadowAttributes} instance.
     *
     * <p>Visible annotations are checked first; if not present there, the invisible list
     * is consulted. If neither list contains a matching annotation, {@code null} is returned.</p>
     *
     * @param visibleAnnotations   the list of visible annotations on a member, or {@code null}
     * @param invisibleAnnotations the list of invisible annotations on a member, or {@code null}
     * @return a non-null {@link ShadowAttributes} parsed from the annotation if present; {@code null} otherwise
     */
    @Nullable
    public static ShadowAttributes getShadowAnnotation(@Nullable final List<AnnotationNode> visibleAnnotations,
                                                       @Nullable final List<AnnotationNode> invisibleAnnotations) {
        AnnotationNode node = findAnnotation(visibleAnnotations, SHADOW_DESC);
        if (node == null) {
            node = findAnnotation(invisibleAnnotations, SHADOW_DESC);
        }
        return node != null ? parseShadow(node) : null;
    }

    /**
     * Locates the first annotation with the specified descriptor in the provided list.
     *
     * @param list the annotation list to search, or {@code null}
     * @param desc the expected descriptor (e.g., {@link #SHADOW_DESC}); must not be {@code null}
     * @return the matching {@link AnnotationNode}, or {@code null} if no match is found
     */
    @Nullable
    private static AnnotationNode findAnnotation(@Nullable final List<AnnotationNode> list,
                                                 @NotNull final String desc) {
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

    /**
     * Parses a {@code @Shadow} {@link AnnotationNode} into a {@link ShadowAttributes} value object.
     *
     * <p>Any elements not present on the annotation node are substituted with defaults defined
     * by the Aether Mixins {@code @Shadow} API.</p>
     *
     * @param node the annotation node to parse; must not be {@code null}
     * @return a non-null {@link ShadowAttributes} populated from the annotation values
     */
    @NotNull
    private static ShadowAttributes parseShadow(@NotNull final AnnotationNode node) {
        Map<String, Object> values = toValueMap(node);

        // defaults as defined by the @Shadow annotation contract
        String prefix = (String) values.getOrDefault("prefix", "shadow$");
        boolean remap = (boolean) values.getOrDefault("remap", false);
        boolean optional = (boolean) values.getOrDefault("optional", false);

        return new ShadowAttributes(prefix, remap, optional);
    }

    /**
     * Converts the raw {@link AnnotationNode#values} list into a name-&rarr;value map for easier lookup.
     *
     * <p>ASM represents annotation element pairs in a flat {@code List} as alternating
     * {@code name, value} entries. This helper normalizes the representation into a
     * {@link Map} where the keys are element names and values are the corresponding raw objects.</p>
     *
     * @param node the annotation node to convert; must not be {@code null}
     * @return a non-null map from element names to raw values (may be empty if {@code node.values == null})
     */
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

    /**
     * Determines whether a member (field or method) is annotated with {@code @Mutable}.
     *
     * <p>This is used to decide whether a shadowed member is mutable (a field or a
     * non-{@code final} method).</p>
     *
     * @param vis   the list of visible annotations on the member, or {@code null}
     * @param invis the list of invisible annotations on the member, or {@code null}
     * @return {@code true} if the member has a {@code @Mutable} annotation; {@code false} otherwise
     */
    public static boolean hasMutableAnnotation(@Nullable final List<AnnotationNode> vis,
                                               @Nullable final List<AnnotationNode> invis) {
        if (vis != null && vis.stream().anyMatch(a -> MUTABLE_DESC.equals(a.desc))) {
            return true;
        }
        return invis != null && invis.stream().anyMatch(a -> MUTABLE_DESC.equals(a.desc));
    }
}
