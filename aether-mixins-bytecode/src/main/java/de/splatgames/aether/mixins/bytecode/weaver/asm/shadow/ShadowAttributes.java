package de.splatgames.aether.mixins.bytecode.weaver.asm.shadow;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Immutable value object describing the attributes declared on a {@code @Shadow} member.
 *
 * <p>This record mirrors the user-facing annotation parameters and is used during analysis
 * and rewriting to determine how a shadow should be matched and handled. In particular, it
 * captures the name {@linkplain #prefix() prefix} that must be stripped from the mixin-side
 * member name before resolution, whether symbolic names should be {@linkplain #remap() remapped},
 * and whether the shadow is {@linkplain #optional() optional} (i.e., tolerated when absent on
 * the target class).</p>
 *
 * <h2>Semantics</h2>
 * <ul>
 *   <li><strong>Prefix</strong> — If non-empty, the given string must appear at the start of the
 *       mixin member name. The prefix is removed to determine the target member name
 *       during resolution and rewrite.</li>
 *   <li><strong>Remap</strong> — Indicates whether names/descriptors should be translated
 *       through a mapping source. <em>In Aether Mixins 0.2.x this flag is reserved and does
 *       not trigger any remapping.</em></li>
 *   <li><strong>Optional</strong> — If {@code true} and the target member is missing, the shadow
 *       is considered unresolved but tolerated. Rewriters must neutralize uses appropriately
 *       (e.g., default loads, dropped writes, elided invocations) instead of failing the weave.</li>
 * </ul>
 *
 * <p>The values represented here are typically extracted from the annotation model during mixin
 * scanning and propagated to {@code ShadowMeta}/{@code ShadowBinding} and related components.</p>
 *
 * @author Erik Pförtner
 * @see ShadowMeta
 * @see ShadowBinding
 * @see ShadowRegistry
 * @since 0.2.0
 */
public record ShadowAttributes(String prefix, boolean remap, boolean optional) {
    /**
     * Creates a new attribute bundle for a {@code @Shadow} member.
     *
     * @param prefix   the name prefix to strip from the mixin member when resolving the target name;
     *                 may be an empty string to indicate an exact, unprefixed match; must not be {@code null}
     * @param remap    whether symbolic names/descriptors should be remapped via a mapping source;
     *                 <em>no-op in 0.2.x</em>
     * @param optional whether the shadow is optional; if {@code true} and resolution fails, usage
     *                 should be neutralized instead of failing the weave
     * @throws NullPointerException if {@code prefix} is {@code null}
     */
    public ShadowAttributes(@NotNull final String prefix, final boolean remap, final boolean optional) {
        this.prefix = Objects.requireNonNull(prefix, "prefix");
        this.remap = remap;
        this.optional = optional;
    }

    /**
     * Returns the name prefix that must be stripped from the mixin member name before resolving
     * the target member name.
     *
     * @return the configured prefix; never {@code null} (may be empty)
     */
    @Override
    public String prefix() {
        return this.prefix;
    }

    /**
     * Returns whether symbolic names and descriptors <em>should</em> be remapped via a mapping source.
     *
     * @return {@code true} if remapping is requested; {@code false} otherwise
     * @implNote In version 0.2.x this flag is reserved and does not trigger remapping;
     * it is retained for forward compatibility.
     */
    @Override
    public boolean remap() {
        return this.remap;
    }

    /**
     * Returns whether the shadow is optional.
     *
     * <p>When {@code true}, an unresolved shadow must not abort weaving. Instead, the rewriter is
     * responsible for neutralizing concrete uses (push default values, drop writes, remove calls).</p>
     *
     * @return {@code true} if optional; {@code false} otherwise
     */
    @Override
    public boolean optional() {
        return this.optional;
    }
}
