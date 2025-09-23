package de.splatgames.aether.mixins.bytecode.weaver.asm.shadow;

import org.jetbrains.annotations.NotNull;

/**
 * Describes a concrete binding of a {@code @Shadow} declaration to a target class member.
 *
 * <p>A {@code ShadowBinding} captures everything a rewriter needs to transform bytecode which
 * references a shadowed member on the mixin owner into an equivalent reference on the target
 * owner. It includes the member kind (field/method), static-ness, the prefix-stripped name,
 * the JVM descriptor, and flags that indicate whether the member was actually resolved on the
 * target class and whether it was declared optional.</p>
 *
 * <h2>Typical lifecycle</h2>
 * <ol>
 *   <li>Scan the mixin class for {@code @Shadow}-annotated members and compute the
 *       <em>stripped</em> name using the configured prefix.</li>
 *   <li>Attempt to resolve the stripped name and descriptor on the target class.</li>
 *   <li>Emit a {@code ShadowBinding} per shadow, indicating {@linkplain #isResolved() resolution}
 *       and {@linkplain #isOptional() optionality}.</li>
 *   <li>Use the binding during rewriting to:
 *     <ul>
 *       <li>Replace owner/name of member references to point at the target.</li>
 *       <li>Neutralize optional-but-unresolved uses (e.g., default reads, dropped writes, elided calls).</li>
 *       <li>Choose correct opcodes depending on {@linkplain #isStatic() static-ness}.</li>
 *       <li>Validate that mutable shadows are not used in constant contexts.</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <h2>Immutability &amp; thread-safety</h2>
 * <p>This class is immutable; instances are safe to share across validation and rewrite phases.</p>
 *
 * @author Erik Pförtner
 * @see ShadowMeta
 * @see ShadowRegistry
 * @see ShadowMap
 * @see ShadowRewriter
 * @since 0.2.0
 */
public final class ShadowBinding {
    /**
     * Binding category: field or method; never {@code null}.
     */
    @NotNull
    private final Kind kind;
    /**
     * Whether the shadowed member is declared {@code static} in the mixin.
     * Affects which opcodes the rewriter must use at the call site.
     */
    private final boolean isStatic;
    /**
     * Member name after removing the configured shadow prefix.
     * This is the name to apply on the target owner during rewriting.
     */
    @NotNull
    private final String strippedName;
    /**
     * JVM descriptor of the shadowed member.
     * For fields this is a field type; for methods this is a method descriptor.
     */
    @NotNull
    private final String desc;
    /**
     * {@code true} if the shadow was declared optional
     * (missing targets are tolerated and uses should be neutralized).
     */
    private final boolean optional;
    /**
     * {@code true} if a member with the stripped name and descriptor was found on the target class.
     */
    private final boolean resolved;
    /**
     * Whether the shadowed member is mutable (a field or a non-{@code final} method).
     */
    private final boolean mutable;

    /**
     * Creates a new binding.
     *
     * @param kind         whether the shadowed member is a field or a method; never {@code null}
     * @param isStatic     {@code true} if the shadowed member is declared {@code static} in the mixin
     * @param strippedName member name after removing the configured shadow prefix; never {@code null}
     * @param desc         JVM descriptor ({@code T} for fields; {@code (args)ret} for methods); never {@code null}
     * @param optional     whether the shadow was declared optional (missing targets are tolerated)
     * @param resolved     whether a matching member was found on the target class
     * @param mutable      whether the shadowed member is mutable (a field or a non-{@code final} method)
     */
    public ShadowBinding(@NotNull final Kind kind,
                         final boolean isStatic,
                         @NotNull final String strippedName,
                         @NotNull final String desc,
                         final boolean optional,
                         final boolean resolved,
                         final boolean mutable) {
        this.kind = kind;
        this.isStatic = isStatic;
        this.strippedName = strippedName;
        this.desc = desc;
        this.optional = optional;
        this.resolved = resolved;
        this.mutable = mutable;
    }

    /**
     * Returns whether this binding describes a field or a method.
     *
     * @return the binding kind; never {@code null}
     */
    @NotNull
    public Kind getKind() {
        return this.kind;
    }

    /**
     * Returns {@code true} if the shadowed member is declared {@code static} in the mixin.
     *
     * @return {@code true} if static, {@code false} otherwise
     */
    public boolean isStatic() {
        return this.isStatic;
    }

    /**
     * Returns the target member name after removing the configured shadow prefix.
     *
     * @return the prefix-free member name; never {@code null}
     */
    @NotNull
    public String getStrippedName() {
        return this.strippedName;
    }

    /**
     * Returns the JVM descriptor of the shadowed member.
     *
     * @return the descriptor; never {@code null}
     */
    @NotNull
    public String getDesc() {
        return this.desc;
    }

    /**
     * Returns whether the shadow was declared optional.
     *
     * @return {@code true} if optional, {@code false} otherwise
     */
    public boolean isOptional() {
        return this.optional;
    }

    /**
     * Returns whether the target member could be resolved on the target class.
     *
     * @return {@code true} if the member exists on the target, otherwise {@code false}
     */
    public boolean isResolved() {
        return this.resolved;
    }

    /**
     * Returns whether the shadowed member is mutable (a field or a non-{@code final} method).
     *
     * @return {@code true} if mutable, {@code false} otherwise
     */
    public boolean isMutable() {
        return this.mutable;
    }

    /**
     * Binding category describing whether the shadow targets a field or a method.
     */
    public enum Kind {
        /**
         * The shadow describes a field reference. The {@linkplain #getDesc() descriptor} is a
         * field type (e.g., {@code I}, {@code Ljava/lang/String;}); the rewriter uses
         * {@code GETFIELD}/{@code PUTFIELD} or {@code GETSTATIC}/{@code PUTSTATIC}.
         */
        FIELD,

        /**
         * The shadow describes a method reference. The {@linkplain #getDesc() descriptor} is a
         * method descriptor (e.g., {@code (I)Z}); the rewriter uses an appropriate {@code INVOKE*}.
         */
        METHOD
    }
}
