package de.splatgames.aether.mixins.bytecode.weaver.asm.shadow;

import org.jetbrains.annotations.NotNull;

/**
 * Immutable metadata describing a single {@code @Shadow} declaration discovered in a mixin.
 *
 * <p>This value object captures the essential properties of a shadowed member (field or method)
 * after prefix processing and resolution against the target class. It is used by validation
 * and rewriting stages to decide whether a reference is legal, whether it must be rewritten
 * to the stripped target name, or whether it must be neutralized if the shadow is optional
 * and unresolved.</p>
 *
 * <h2>Components</h2>
 * <ul>
 *   <li><b>{@code staticMember}:</b> {@code true} if the member was declared {@code static}
 *       in the mixin; {@code false} for instance members.</li>
 *   <li><b>{@code strippedName}:</b> The target member name after applying the
 *       {@code @Shadow(prefix=...)} rule (i.e., the mixin-local prefix has been removed).
 *       This is the name that must be used on the target owner when rewriting bytecode.</li>
 *   <li><b>{@code optional}:</b> Whether the shadow was declared as optional; optional and
 *       unresolved shadows must not cause weaving to fail and may be neutralized at usage sites.</li>
 *   <li><b>{@code resolved}:</b> {@code true} if the corresponding member was found on the
 *       target class (name + descriptor match); {@code false} otherwise.</li>
 *   <li><b>{@code targetFinal}:</b> For fields, indicates whether the resolved target field
 *       is {@code final}. For methods this flag is inconsequential and may be {@code false}.</li>
 *   <li><b>{@code mutable}:</b> Whether the shadow carries an opt-in that allows writes to a
 *       {@code final} target field (e.g., via an {@code @Mutable} annotation) when combined
 *       with the appropriate runtime configuration.</li>
 * </ul>
 *
 * <h2>Semantics and usage</h2>
 * <ul>
 *   <li>If {@code optional == true} and {@code resolved == false}, usage sites should be
 *       <em>neutralized</em> (e.g., load default values or drop stores), rather than failing.</li>
 *   <li>If {@code targetFinal == true} and {@code mutable == false}, writes to the field are
 *       prohibited and should be reported as errors by validation.</li>
 *   <li>{@code strippedName} is only the <em>name</em>; consumers must keep using the original
 *       descriptor to address the correct member.</li>
 * </ul>
 *
 * <h2>Thread-safety</h2>
 * <p>This type is a Java {@code record} and therefore immutable and thread-safe to share.</p>
 *
 * @param staticMember whether the shadowed member is declared {@code static} in the mixin
 * @param strippedName target member name after stripping the configured shadow prefix; never {@code null}
 * @param optional     whether the shadow is optional (missing targets do not fail weaving)
 * @param resolved     whether a matching target member was found on the target class
 * @param targetFinal  for fields, whether the resolved target field is {@code final}; {@code false} for methods
 * @param mutable      whether writes to a {@code final} target field are explicitly permitted via opt-in
 *
 * @author Erik Pförtner
 * @since 0.2.0
 * @see ShadowRegistry
 * @see ShadowUsageValidator
 * @see ShadowRewriter
 */
public record ShadowMeta(
        boolean staticMember,
        @NotNull String strippedName,
        boolean optional,
        boolean resolved,
        boolean targetFinal,
        boolean mutable
) { }
