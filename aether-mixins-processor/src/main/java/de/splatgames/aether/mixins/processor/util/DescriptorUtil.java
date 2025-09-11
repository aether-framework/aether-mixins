package de.splatgames.aether.mixins.processor.util;

import org.jetbrains.annotations.NotNull;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Utilities for deriving JVM descriptors and binary names from APT (annotation processing)
 * type/model elements.
 *
 * <h2>What this provides</h2>
 * <ul>
 *   <li>{@link #methodDescriptor(ExecutableElement, Elements, Types)} → a full JVM method descriptor,
 *       e.g. {@code (Ljava/lang/String;I)Z}.</li>
 *   <li>{@link #typeDesc(TypeMirror, Elements, Types)} → a JVM type descriptor for any {@link TypeMirror},
 *       including primitives, arrays, and reference types (erased).</li>
 *   <li>{@link #binaryName(TypeElement, Elements)} → the binary (dotted) name of a type,
 *       e.g. {@code com.example.Outer$Inner}.</li>
 * </ul>
 *
 * <h2>Notes</h2>
 * <ul>
 *   <li>Reference types are <em>erased</em> before conversion (type arguments are dropped).</li>
 *   <li>Array descriptors are constructed recursively, prefixing {@code [} per dimension.</li>
 *   <li>Nested classes use {@code $} in their binary names per JVM conventions.</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // Given: String foo(int[] a, List<String> b)
 * String desc = DescriptorUtil.methodDescriptor(methodElem, elements, types);
 * // desc == "( [I Ljava/util/List; )Ljava/lang/String;"  // (whitespace added here for readability)
 * }</pre>
 *
 * <h2>Thread-safety</h2>
 * <p>This class is stateless and thread-safe.</p>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class DescriptorUtil {

    /**
     * Utility class; no instances.
     */
    private DescriptorUtil() {
        // utility class, prevent instantiation
    }

    /**
     * Produces the JVM method descriptor for a method element.
     *
     * <p>The format follows the JVM specification:
     * {@code ( <param-descriptors> ) <return-descriptor>}.
     * Parameter and return types are derived via {@link #typeDesc(TypeMirror, Elements, Types)}.</p>
     *
     * @param method   the method element to describe; must not be {@code null}
     * @param elements {@link Elements} utility for resolving binary names; must not be {@code null}
     * @param types    {@link Types} utility for erasure and kind checks; must not be {@code null}
     * @return the JVM method descriptor string (e.g., {@code (I[Ljava/lang/Object;)V}); never {@code null}
     */
    @NotNull
    public static String methodDescriptor(@NotNull final ExecutableElement method,
                                          @NotNull final Elements elements,
                                          @NotNull final Types types) {
        final StringBuilder sb = new StringBuilder(32);
        sb.append('(');
        method.getParameters().forEach(p -> sb.append(typeDesc(p.asType(), elements, types)));
        sb.append(')');
        sb.append(typeDesc(method.getReturnType(), elements, types));
        return sb.toString();
    }

    /**
     * Produces the JVM type descriptor for a {@link TypeMirror}.
     *
     * <p>Mapping rules:</p>
     * <ul>
     *   <li>Primitives → single-letter codes ({@code Z, B, C, S, I, J, F, D}).</li>
     *   <li>Void → {@code V}.</li>
     *   <li>Arrays → {@code '['} + component descriptor (recursively).</li>
     *   <li>Declared/reference types → {@code L} + <em>internal name</em> + {@code ;}.
     *       The internal name is the binary name with dots replaced by slashes,
     *       e.g. {@code com/example/Outer$Inner}.</li>
     * </ul>
     *
     * <p>Reference types are <b>erased</b> before conversion, so generics are not included
     * in the resulting descriptor.</p>
     *
     * @param t        the type to convert; must not be {@code null}
     * @param elements {@link Elements} utility for resolving binary names; must not be {@code null}
     * @param types    {@link Types} utility for erasure and kind checks; must not be {@code null}
     * @return the JVM type descriptor (e.g., {@code Ljava/util/List;}, {@code [I}); never {@code null}
     */
    @NotNull
    public static String typeDesc(@NotNull final TypeMirror t,
                                  @NotNull final Elements elements,
                                  @NotNull final Types types) {
        switch (t.getKind()) {
            case BOOLEAN:
                return "Z";
            case BYTE:
                return "B";
            case CHAR:
                return "C";
            case SHORT:
                return "S";
            case INT:
                return "I";
            case LONG:
                return "J";
            case FLOAT:
                return "F";
            case DOUBLE:
                return "D";
            case VOID:
                return "V";
            case ARRAY: {
                final ArrayType at = (ArrayType) t;
                return "[" + typeDesc(at.getComponentType(), elements, types);
            }
            default: {
                final DeclaredType dt = (DeclaredType) types.erasure(t);
                final TypeElement te = (TypeElement) dt.asElement();
                final String binary = elements.getBinaryName(te).toString(); // e.g., com.example.Outer$Inner
                final String internal = binary.replace('.', '/');            // com/example/Outer$Inner
                return "L" + internal + ";";
            }
        }
    }

    /**
     * Returns the binary (dotted) name for a {@link TypeElement}.
     *
     * <p>The result matches the JVM notion of a binary name, including {@code $} for
     * nested types. Example: {@code com.example.Outer$Inner}.</p>
     *
     * @param type     the type element to name; must not be {@code null}
     * @param elements {@link Elements} utility used to resolve the binary name; must not be {@code null}
     * @return the binary (dotted) name; never {@code null}
     */
    @NotNull
    public static String binaryName(@NotNull final TypeElement type,
                                    @NotNull final Elements elements) {
        return elements.getBinaryName(type).toString();
    }
}
