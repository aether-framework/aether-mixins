package de.splatgames.aether.mixins.processor.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import java.util.Map;

/**
 * Utilities for reading (optional) boolean attributes from annotation mirrors during
 * annotation processing.
 *
 * <h2>Purpose</h2>
 * <p>
 * This helper inspects annotation <em>mirrors</em> (APT model) instead of using reflection.
 * It is robust to API drift: when a boolean attribute (e.g., {@code optional}, {@code remap})
 * is not present on a particular annotation version, the lookup simply yields {@code null}
 * instead of throwing.
 * </p>
 *
 * <h2>Behavior</h2>
 * <ul>
 *   <li>Only <em>explicitly set</em> annotation values are visible. If an attribute is omitted
 *       (i.e., the annotation uses its default), {@link #getAnnotationValue(AnnotationMirror, String)}
 *       returns {@code null} and the reader methods return {@code null}.</li>
 *   <li>Lookups are by fully-qualified annotation class name and by attribute name.</li>
 *   <li>All methods are <b>null-safe</b> with respect to missing attributes or missing annotations,
 *       returning {@code null} in those cases.</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * <p>
 * This class is stateless and thread-safe. It relies solely on the APT model passed in.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // Inside an annotation processor:
 * for (ExecutableElement method : methods) {
 *     Boolean opt = AnnotationUtil.readOptional(method, Inject.class);
 *     Boolean rem = AnnotationUtil.readRemap(method, Redirect.class);
 *     // opt/rem are null if attribute not present or not explicitly set
 * }
 * }</pre>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class AnnotationUtil {

    /**
     * Non-instantiable utility class.
     */
    private AnnotationUtil() {
        // utility class, prevent instantiation
    }

    /**
     * Reads an {@code optional} boolean attribute from the given hook's annotation, if present.
     *
     * @param hook     the method element carrying the annotation, must not be {@code null}
     * @param annClass the annotation class to inspect, must not be {@code null}
     * @return {@code Boolean.TRUE}/{@code Boolean.FALSE} if explicitly set; {@code null} if
     * the annotation is absent or the attribute is not present/not explicitly set
     */
    @Nullable
    public static Boolean readOptional(@NotNull final ExecutableElement hook,
                                       @NotNull final Class<?> annClass) {
        return readBoolean(hook, annClass, "optional");
    }

    /**
     * Reads a {@code remap} boolean attribute from the given hook's annotation, if present.
     *
     * @param hook     the method element carrying the annotation, must not be {@code null}
     * @param annClass the annotation class to inspect, must not be {@code null}
     * @return {@code Boolean.TRUE}/{@code Boolean.FALSE} if explicitly set; {@code null} if
     * the annotation is absent or the attribute is not present/not explicitly set
     */
    @Nullable
    public static Boolean readRemap(@NotNull final ExecutableElement hook,
                                    @NotNull final Class<?> annClass) {
        return readBoolean(hook, annClass, "remap");
    }

    /**
     * Reads a boolean attribute by name from the given hook's annotation, if present.
     *
     * <p><b>Note:</b> Only explicitly present element values are returned by the APT API.
     * If an annotation relies on its default value and the compiler does not materialize it
     * into the element-value map, this method returns {@code null}.</p>
     *
     * @param hook     the method element carrying the annotation, must not be {@code null}
     * @param annClass the annotation class to inspect, must not be {@code null}
     * @param attr     the attribute (element) name to read, must not be {@code null}
     * @return {@code Boolean.TRUE}/{@code Boolean.FALSE} if explicitly set; {@code null} if
     * the annotation or attribute is not present or not boolean
     */
    @Nullable
    private static Boolean readBoolean(@NotNull final ExecutableElement hook,
                                       @NotNull final Class<?> annClass,
                                       @NotNull final String attr) {
        final AnnotationMirror ann = getAnnotationMirror(hook, annClass);
        if (ann == null) {
            return null;
        }
        final AnnotationValue av = getAnnotationValue(ann, attr);
        if (av == null) {
            return null;
        }
        final Object v = av.getValue();
        return (v instanceof Boolean) ? (Boolean) v : null;
    }

    /**
     * Locates the annotation mirror of the given type on an element.
     *
     * @param e        the element to inspect (type, method, field, etc.), must not be {@code null}
     * @param annClass the annotation type to locate, must not be {@code null}
     * @return the matching {@link AnnotationMirror} if present; otherwise {@code null}
     */
    @Nullable
    private static AnnotationMirror getAnnotationMirror(@NotNull final Element e,
                                                        @NotNull final Class<?> annClass) {
        final String cname = annClass.getCanonicalName();
        for (final AnnotationMirror m : e.getAnnotationMirrors()) {
            final DeclaredType t = m.getAnnotationType();
            final Element toe = t.asElement();
            if (toe instanceof TypeElement te && te.getQualifiedName().contentEquals(cname)) {
                return m;
            }
        }
        return null;
    }

    /**
     * Retrieves a specific attribute (element) value from an annotation mirror.
     *
     * <p>Only values explicitly present in the source are returned by this method. Defaults that
     * are not materialized in the element-value map will yield {@code null}.</p>
     *
     * @param ann  the annotation mirror to inspect, must not be {@code null}
     * @param name the attribute name to fetch (e.g., {@code "optional"}), must not be {@code null}
     * @return the {@link AnnotationValue} if explicitly present; otherwise {@code null}
     */
    @Nullable
    private static AnnotationValue getAnnotationValue(@NotNull final AnnotationMirror ann,
                                                      @NotNull final String name) {
        for (final Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> e : ann.getElementValues().entrySet()) {
            if (e.getKey().getSimpleName().contentEquals(name)) {
                return e.getValue();
            }
        }
        // Not explicitly set – the default may exist; we intentionally return null to mean "not present"
        return null;
    }
}
