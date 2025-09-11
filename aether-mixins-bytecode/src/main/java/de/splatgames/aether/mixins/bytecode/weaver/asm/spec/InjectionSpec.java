package de.splatgames.aether.mixins.bytecode.weaver.asm.spec;

import de.splatgames.aether.mixins.bytecode.weaver.hook.ResolvedHook;
import de.splatgames.aether.mixins.core.api.Inject;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a fully resolved injection specification for a mixin.
 *
 * <p>This record defines all metadata required to perform a bytecode injection
 * at a specific join point ({@link Inject.At#HEAD} or {@link Inject.At#TAIL})
 * within a target method.</p>
 *
 * <h2>Responsibilities:</h2>
 * <ul>
 *   <li>Holds the resolved hook information that points to a static {@code ()V} method
 *       which will be injected into the target method.</li>
 *   <li>Stores configuration flags that influence how the injection behaves during weaving,
 *       such as {@link #optional} and {@link #remap}.</li>
 *   <li>Contains an identifier and priority to help resolve conflicts between multiple
 *       injections applied to the same target location.</li>
 * </ul>
 *
 * <p>Instances of this record are immutable and safe to share across threads,
 * but are typically created and consumed by a single weaving pass.</p>
 *
 * @param at       The join point indicating where in the target method the injection should occur
 *                 (e.g., {@link Inject.At#HEAD} or {@link Inject.At#TAIL}); never {@code null}.
 * @param hook     The resolved static hook method to be invoked at the join point, represented
 *                 by its owner, name, and descriptor; never {@code null}.
 * @param optional If {@code true}, the injection will be treated as optional and will not fail
 *                 if the target location cannot be found during weaving.
 * @param remap    Indicates whether remapping hints should be applied to this injection during
 *                 runtime, typically for name remapping in obfuscated environments.
 * @param id       A developer-defined identifier for diagnostics and debugging purposes; never {@code null}.
 * @param priority The priority level of this injection. Higher values are processed earlier
 *                 when multiple injections target the same location.
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record InjectionSpec(
        @NotNull Inject.At at,
        @NotNull ResolvedHook hook,
        boolean optional,
        boolean remap,
        @NotNull String id,
        int priority
) {
}
