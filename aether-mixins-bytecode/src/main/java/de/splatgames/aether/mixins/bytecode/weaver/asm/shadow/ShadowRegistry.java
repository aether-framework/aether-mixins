package de.splatgames.aether.mixins.bytecode.weaver.asm.shadow;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Registry of discovered {@code @Shadow} declarations for a single mixin class.
 *
 * <p>This container exposes the shadowed members discovered during analysis in two maps:
 * one for methods and one for fields. Each map is keyed by the <em>mixin-local</em> signature
 * (i.e., the member name and descriptor as declared in the mixin, prior to any prefix
 * stripping), and the value is a {@link ShadowMeta} describing the resolution outcome and
 * usage constraints for that shadow.</p>
 *
 * <h2>Keying and resolution</h2>
 * <ul>
 *   <li><b>Keys:</b> The keys are of the form {@code name + desc} as they appear in the mixin
 *       class (e.g., {@code shadow$performTask(I)V} for methods or {@code shadow$COUNTER:I}
 *       for fields), not the stripped target name.</li>
 *   <li><b>Values:</b> Each {@link ShadowMeta} records the stripped target name (after applying
 *       the {@code @Shadow(prefix=...)} rule), whether the target member was resolved on the
 *       target class, whether it was declared {@code optional}, and additional attributes
 *       such as mutability for fields.</li>
 * </ul>
 *
 * <h2>Intended usage</h2>
 * <p>Instances of this class are typically constructed by a shadow collection routine and then
 * consulted by validators and rewriters:</p>
 * <ol>
 *   <li><em>Validation:</em> A pre-weave pass (e.g., a shadow usage validator) checks that
 *       references to shadow members are legal with respect to optionality, finality, and
 *       invocation context.</li>
 *   <li><em>Rewriting:</em> A bytecode rewriter uses the registry to replace mixin-local
 *       references (owner/name) with the resolved target owner and stripped name, or to
 *       neutralize optional-but-missing references.</li>
 * </ol>
 *
 * <h2>Thread-safety &amp; mutability</h2>
 * <p>The maps supplied to and exposed by this class are treated as owner-provided, potentially
 * mutable collections. This class does not perform defensive copying. If concurrent access
 * or immutability is required, wrap or copy the maps before constructing the registry.</p>
 *
 * @author Erik Pförtner
 * @see ShadowMeta
 * @see ShadowUsageValidator
 * @see ShadowRewriter
 * @since 0.2.0
 */
public final class ShadowRegistry {

    /**
     * Registry of method shadows keyed by mixin-local signature ({@code name + desc}).
     */
    @NotNull
    private final Map<String, ShadowMeta> methods;

    /**
     * Registry of field shadows keyed by mixin-local signature ({@code name + desc}).
     */
    @NotNull
    private final Map<String, ShadowMeta> fields;

    /**
     * Creates a new registry of shadow declarations.
     *
     * @param methods a map of mixin-local method signatures ({@code name + desc}) to their metadata; must not be {@code null}
     * @param fields  a map of mixin-local field signatures ({@code name + desc}) to their metadata; must not be {@code null}
     */
    public ShadowRegistry(@NotNull final Map<String, ShadowMeta> methods,
                          @NotNull final Map<String, ShadowMeta> fields) {
        this.methods = methods;
        this.fields = fields;
    }

    /**
     * Returns the registry of method shadows keyed by mixin-local signature.
     *
     * <p>The returned map is the same instance supplied to the constructor and may be mutable,
     * depending on the caller's choice.</p>
     *
     * @return non-{@code null} map from mixin-local method signatures to {@link ShadowMeta}
     */
    @NotNull
    public Map<String, ShadowMeta> methods() {
        return this.methods;
    }

    /**
     * Returns the registry of field shadows keyed by mixin-local signature.
     *
     * <p>The returned map is the same instance supplied to the constructor and may be mutable,
     * depending on the caller's choice.</p>
     *
     * @return non-{@code null} map from mixin-local field signatures to {@link ShadowMeta}
     */
    @NotNull
    public Map<String, ShadowMeta> fields() {
        return this.fields;
    }
}
