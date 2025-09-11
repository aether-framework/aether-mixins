package de.splatgames.aether.mixins.bytecode.weaver.asm.spec;

import de.splatgames.aether.mixins.bytecode.weaver.hook.ResolvedHook;
import de.splatgames.aether.mixins.core.api.Redirect;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a fully resolved redirect specification for a mixin.
 *
 * <p>This record contains all metadata required to identify and rewrite a specific method
 * invocation inside a target method. During weaving, matching invokes are replaced with
 * a static call to a provided {@link #hook} method.</p>
 *
 * <h2>Responsibilities:</h2>
 * <ul>
 *   <li>Holds information about the original call site to match, including the
 *       {@link #owner}, {@link #name}, and {@link #desc} (JVM descriptor).</li>
 *   <li>Specifies the expected opcode kind through {@link #kind}, or allows any via
 *       {@link Redirect.InvokeKind#AUTO}.</li>
 *   <li>Determines which occurrence of the matching invoke to replace via {@link #ordinal}.</li>
 *   <li>Provides the resolved hook method that should be called instead of the original invoke.</li>
 *   <li>Includes optionality and remapping hints to guide safe mode behavior and runtime transformations.</li>
 * </ul>
 *
 * <h2>Ordinal behavior:</h2>
 * <ul>
 *   <li>If {@link #ordinal} is negative, the <em>first</em> matching call site will be rewritten.</li>
 *   <li>If {@link #ordinal} is zero or positive, only the call site with that 0-based index will be rewritten.</li>
 * </ul>
 *
 * <p>Instances of this record are immutable and safe to share across threads,
 * but are typically created and consumed by a single weaving pass.</p>
 *
 * @param owner    The internal JVM name (slash-separated) of the class that owns the method being invoked;
 *                 never {@code null}.
 * @param name     The name of the original method being invoked; never {@code null}.
 * @param desc     The JVM method descriptor of the original method being invoked; never {@code null}.
 * @param kind     The invocation kind restriction (e.g., {@link Redirect.InvokeKind#INVOKESTATIC});
 *                 use {@link Redirect.InvokeKind#AUTO} to allow any invoke kind; never {@code null}.
 * @param ordinal  A 0-based index indicating which matching occurrence to rewrite;
 *                 negative value means the first match will be rewritten.
 * @param hook     The resolved static hook method that should replace the original invoke; never {@code null}.
 * @param optional If {@code true}, the redirect is treated as optional and will not fail if no matching
 *                 call site is found during weaving.
 * @param remap    Indicates whether remapping hints should be applied to this redirect during runtime,
 *                 typically for name remapping in obfuscated environments.
 * @param id       A developer-defined identifier for diagnostics and debugging purposes; never {@code null}.
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record RedirectSpec(
        @NotNull String owner,
        @NotNull String name,
        @NotNull String desc,
        @NotNull Redirect.InvokeKind kind,
        int ordinal,
        @NotNull ResolvedHook hook,
        boolean optional,
        boolean remap,
        @NotNull String id
) {
}
