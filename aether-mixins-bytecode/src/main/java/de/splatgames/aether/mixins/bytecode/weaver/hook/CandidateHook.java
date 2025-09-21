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
    public final String name;

    /**
     * The JVM method descriptor, e.g., {@code (I)V}.
     */
    @NotNull
    public final String desc;

    /**
     * The invocation type, either {@link HookInvocation#STATIC static} or {@link HookInvocation#INSTANCE instance} method.
     * <p>Determined by the weaver based on the method's {@code static} modifier.</p>
     *
     * @since 0.2.0
     */
    @NotNull
    public final HookInvocation invocation;

    /**
     * Optional annotation {@code id} value captured from the hook annotation.
     */
    @Nullable
    public String annotationId;


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
