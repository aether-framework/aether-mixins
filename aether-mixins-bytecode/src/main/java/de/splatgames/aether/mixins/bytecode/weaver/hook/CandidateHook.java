package de.splatgames.aether.mixins.bytecode.weaver.hook;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a single hook candidate discovered by bytecode scanning.
 *
 * <p>Field semantics:</p>
 * <ul>
 *   <li>{@link #name}: the method name (never {@code null})</li>
 *   <li>{@link #desc}: the JVM method descriptor (never {@code null})</li>
 *   <li>{@link #annotationId}: the optional id captured from the hook annotation
 *       <ul>
 *         <li>{@code null}: annotation not present (should not occur in scanner results)</li>
 *         <li>empty string {@code ""}: annotation present but no explicit {@code id} attribute</li>
 *         <li>non-empty string: explicit {@code id} attribute value</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class CandidateHook {

    /**
     * The method name.
     */
    @NotNull
    private final String name;

    /**
     * The JVM method descriptor, e.g., {@code (I)V}.
     */
    @NotNull
    private final String desc;

    /**
     * The invocation type, either {@link HookInvocation#STATIC static} or {@link HookInvocation#INSTANCE instance} method.
     * <p>Determined by the weaver based on the method's {@code static} modifier.</p>
     *
     * @since 0.2.0
     */
    @NotNull
    private final HookInvocation invocation;

    /**
     * Optional annotation {@code id} value captured from the hook annotation.
     */
    @Nullable
    private String annotationId;


    /**
     * Creates a new candidate hook representation with the given method data.
     *
     * @param name       the method name; must not be {@code null}
     * @param desc       the JVM method descriptor; must not be {@code null}
     * @param invocation the invocation type; must not be {@code null}
     */
    public CandidateHook(@NotNull final String name, @NotNull final String desc,
                         @NotNull final HookInvocation invocation) {
        this.name = name;
        this.desc = desc;
        this.invocation = invocation;
    }

    /**
     * Returns the method name of this candidate.
     *
     * @return the method name; never {@code null}
     * @since 0.2.0
     */
    @NotNull
    public String getName() {
        return this.name;
    }

    /**
     * Returns the JVM method descriptor of this candidate.
     *
     * @return the method descriptor; never {@code null}
     * @since 0.2.0
     */
    @NotNull
    public String getDesc() {
        return this.desc;
    }

    /**
     * Returns the invocation type of this candidate, either {@link HookInvocation#STATIC static}
     * or {@link HookInvocation#INSTANCE instance} method.
     *
     * @return the invocation type; never {@code null}
     * @since 0.2.0
     */
    @NotNull
    public HookInvocation getInvocation() {
        return this.invocation;
    }

    /**
     * Returns the optional annotation {@code id} value captured from the hook annotation.
     *
     * @return the annotation id; may be {@code null} if the annotation was not present,
     * empty string {@code ""} if no explicit {@code id} attribute was given,
     * or a non-empty string with the explicit {@code id} value
     * @since 0.2.0
     */
    @Nullable
    public String getAnnotationId() {
        return this.annotationId;
    }

    /**
     * Sets the optional annotation {@code id} value captured from the hook annotation.
     *
     * @param annotationId the annotation id; may be {@code null} if the annotation was not present,
     *                     empty string {@code ""} if no explicit {@code id} attribute was given,
     *                     or a non-empty string with the explicit {@code id} value
     * @since 0.2.0
     */
    public void setAnnotationId(@Nullable final String annotationId) {
        this.annotationId = annotationId;
    }

    /**
     * Returns a human-readable representation of this candidate,
     * including name, descriptor, and the current {@code annotationId} value.
     *
     * @return a string useful for debugging and diagnostics
     */
    @NotNull
    @Override
    public String toString() {
        return "CandidateHook{name='" + this.name + "', desc='" + this.desc + "', annotationId=" + this.annotationId + '}';
    }
}
