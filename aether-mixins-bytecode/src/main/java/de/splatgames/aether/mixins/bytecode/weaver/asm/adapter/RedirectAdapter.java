package de.splatgames.aether.mixins.bytecode.weaver.asm.adapter;

import de.splatgames.aether.mixins.bytecode.weaver.asm.FinalNameRegistry;
import de.splatgames.aether.mixins.bytecode.weaver.hook.ResolvedHook;
import de.splatgames.aether.mixins.core.api.Redirect;
import de.splatgames.aether.mixins.core.config.problems.ConfigProblems;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.util.Objects;
import java.util.function.BiFunction;

import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;

/**
 * Method visitor that rewrites a specific method invocation to an {@code INVOKESTATIC}
 * call to a resolved hook method according to a redirect specification.
 *
 * <p>Matching rules:</p>
 * <ul>
 *   <li>The current invoke must match the configured <em>owner</em> (internal name),
 *       <em>name</em>, and <em>descriptor</em>.</li>
 *   <li>If a {@link Redirect.InvokeKind} other than {@link Redirect.InvokeKind#AUTO} is set,
 *       the opcode must match the requested kind.</li>
 *   <li>If {@code ordinal} is {@code &lt; 0}, the <em>first</em> matching call site is rewritten;
 *       otherwise, the {@code ordinal}-th matching occurrence (0-based) is rewritten.</li>
 * </ul>
 *
 * <p>Semantics:</p>
 * <ul>
 *   <li>On a successful match, the adapter emits {@code INVOKESTATIC} to {@link #hook} and
 *       invokes {@link #markChanged}.</li>
 *   <li>If no call site is rewritten by the end of visitation, a non-optional redirect
 *       throws an {@link IllegalStateException} in {@link #visitEnd()}.</li>
 *   <li>The descriptor compatibility between the original invocation and the {@link #hook}
 *       is assumed to be validated upstream (not enforced here).</li>
 * </ul>
 *
 * <p>JDK quirk handling:</p>
 * <ul>
 *   <li>To accommodate JDK 11+ behavior where private methods in the same class can be
 *       invoked using {@code INVOKEVIRTUAL}, a redirect that requests
 *       {@link Redirect.InvokeKind#INVOKESPECIAL} will also accept {@code INVOKEVIRTUAL}
 *       when the invocation owner equals the current class.</li>
 * </ul>
 *
 * <p>Thread-safety: instances are not thread-safe and must be used by a single ASM visitation thread.</p>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class RedirectAdapter extends MethodVisitor {

    /**
     * Internal JVM name (slash-separated) of the original invocation owner to match.
     */
    @NotNull
    private final String owner;

    /**
     * Method name of the original invocation to match.
     */
    @NotNull
    private final String name;

    /**
     * JVM method descriptor of the original invocation to match.
     */
    @NotNull
    private final String desc;

    /**
     * Opcode restriction for the invocation kind (or {@link Redirect.InvokeKind#AUTO} to allow any).
     */
    @NotNull
    private final Redirect.InvokeKind kind;

    /**
     * 0-based occurrence selector; negative value means "first match".
     */
    private final int ordinal;

    /**
     * Resolved hook (owner/name/desc) to call instead of the original invocation.
     */
    @NotNull
    private final ResolvedHook hook;

    /**
     * Whether the redirect may be missing without raising an exception.
     */
    private final boolean optional;

    /**
     * Developer-defined identifier used in diagnostics and error messages.
     */
    @NotNull
    private final String id;

    /**
     * Callback invoked once a bytecode modification is applied.
     */
    @NotNull
    private final Runnable markChanged;

    /**
     * Diagnostics sink (currently unused by this adapter, reserved for future reporting).
     */
    @NotNull
    @SuppressWarnings("unused, FieldCanBeLocal")
    private final ConfigProblems problems;

    /**
     * Human-readable context (class and method signature) for diagnostics.
     */
    @NotNull
    @SuppressWarnings("unused, FieldCanBeLocal")
    private final String ctx;
    /**
     * Internal JVM name (slash-separated) of the current class for JDK quirk handling.
     */
    @NotNull
    private final String thisClass;
    /**
     * Number of matching occurrences seen so far at this call site.
     */
    private int seen = 0;
    /**
     * Tracks whether any call site has been rewritten.
     */
    private boolean applied = false;
    /**
     * Internal JVM name (slash-separated) of the target class for instance call context hooks.
     * Set via {@link #withInstanceCallContext(String, BiFunction)} before visiting method instructions.
     */
    private String targetOwnerInternalName;

    /**
     * Lookup function for final method names after merging (only when collisions caused a rename).
     * Can be null if no renames are expected (e.g., when using unique names).
     *
     * <p>
     * The function is called with two parameters:
     * <ol>
     *     <li>String owner+name+desc (e.g., com/example/F
     *     oo#bar(I)V)</li>
     *     <li>String unused (reserved for future use)</li>
     *     </ol>
     * It must return the final name (e.g., bar$1) or null if no rename occurred.
     * <b>Note:</b> the function must not throw exceptions, as it
     * is called during bytecode weaving.
     * </p>
     */
    private BiFunction<String, String, String> finalNameLookup = null;


    /**
     * Constructs a new redirect adapter that rewrites qualifying invokes to the given hook.
     *
     * @param api         ASM API level to use
     * @param mv          downstream method visitor to delegate to; must not be {@code null}
     * @param owner       internal JVM owner name (slash-separated) to match; must not be {@code null}
     * @param name        method name to match; must not be {@code null}
     * @param desc        JVM method descriptor to match; must not be {@code null}
     * @param kind        opcode restriction; {@link Redirect.InvokeKind#AUTO} accepts any invoke kind; must not be {@code null}
     * @param ordinal     0-based occurrence selector; negative value selects the first match
     * @param hook        resolved hook to invoke via {@code INVOKESTATIC}; must not be {@code null}
     * @param optional    whether to tolerate no rewritten call sites without failing
     * @param id          developer-defined identifier used in diagnostics; must not be {@code null}
     * @param markChanged callback invoked when a rewrite is performed; must not be {@code null}
     * @param problems    diagnostics sink for potential future reporting; must not be {@code null}
     * @param cls         internal JVM class name for diagnostics (e.g., {@code com/example/Foo}); must not be {@code null}
     * @param sig         method signature {@code name+desc} for diagnostics (e.g., {@code bar(I)V}); must not be {@code null}
     */
    public RedirectAdapter(final int api,
                           @NotNull final MethodVisitor mv,
                           @NotNull final String owner,
                           @NotNull final String name,
                           @NotNull final String desc,
                           @NotNull final Redirect.InvokeKind kind,
                           final int ordinal,
                           @NotNull final ResolvedHook hook,
                           final boolean optional,
                           @NotNull final String id,
                           @NotNull final Runnable markChanged,
                           @NotNull final ConfigProblems problems,
                           @NotNull final String cls,
                           @NotNull final String sig) {
        super(api, mv);
        this.owner = owner;
        this.name = name;
        this.desc = desc;
        this.kind = kind;
        this.ordinal = ordinal;
        this.hook = hook;
        this.optional = optional;
        this.id = id;
        this.markChanged = markChanged;
        this.problems = problems;
        this.ctx = "redirect " + cls + "." + sig + " id=" + id;
        this.thisClass = cls;
    }

    public RedirectAdapter withInstanceCallContext(@NotNull final String targetOwner,
                                                   @NotNull final BiFunction<String, String, String> lookup) {
        this.targetOwnerInternalName = targetOwner;
        this.finalNameLookup = lookup;
        return this;
    }

    /**
     * Intercepts method invocation instructions, and when the current instruction matches
     * the configured redirect specification (see {@link #matches(int, String, String, String, boolean)}),
     * emits an {@code INVOKESTATIC} to the configured {@link #hook} instead.
     *
     * @param opcode      the current invoke opcode (e.g., {@code INVOKEVIRTUAL}, {@code INVOKESTATIC})
     * @param owner       the internal owner name of the invocation being visited
     * @param name        the method name of the invocation being visited
     * @param descriptor  the JVM descriptor of the invocation being visited
     * @param isInterface whether the invocation owner is an interface (not used for matching here)
     */
    @Override
    public void visitMethodInsn(final int opcode,
                                @NotNull final String owner,
                                @NotNull final String name,
                                @NotNull final String descriptor,
                                final boolean isInterface) {
        if (this.matches(opcode, owner, name, descriptor, isInterface)) {
            // TODO: Support non-self instance redirects (Sponge-style).
            // Approach: keep redirect handlers STATIC and pass original receiver as first arg.
            // Steps: validate handler descriptor (receiver + args), always INVOKESTATIC to hook,
            // then remove the self-call guard here once implemented.
            final int current = this.seen++;
            if (this.ordinal < 0 || this.ordinal == current) {

                if (this.hook.invocation().isStatic()) {
                    // Always call the premerged copy on the TARGET owner
                    final String resolvedOwner = (this.targetOwnerInternalName != null)
                            ? this.targetOwnerInternalName
                            : this.thisClass; // defensive fallback
                    String callName = FinalNameRegistry
                            .lookup(resolvedOwner, this.hook.owner(), this.hook.name(), this.hook.desc());
                    if (this.finalNameLookup != null && this.targetOwnerInternalName != null) {
                        final String k = this.targetOwnerInternalName + "|" + this.hook.owner() + "#" + this.hook.name() + this.hook.desc();
                        final String resolved = this.finalNameLookup.apply(k, null);
                        if (resolved != null) callName = resolved;
                    }

                    final boolean handlerIsStatic = this.hook.invocation().isStatic();
                    validateRedirectSignatureOrThrow(
                            this.ctx,
                            opcode,
                            handlerIsStatic,
                            this.owner,
                            this.desc,
                            this.hook.desc(),
                            this.id
                    );

                    super.visitMethodInsn(INVOKESTATIC, resolvedOwner, callName, this.hook.desc(), false);
                } else {
                    // We only support rewriting self-calls, because the merged hook lives in the target class.
                    if (this.targetOwnerInternalName == null) {
                        throw new IllegalStateException("Instance redirect requires target owner context");
                    }
                    if (!owner.equals(this.thisClass)) {
                        // Not a self-call: the original receiver type ≠ target class, cannot invoke merged instance hook.
                        // Either fail hard or degrade gracefully. We fail to avoid silent miscompiles.
                        throw new IllegalStateException(
                                "Instance redirect only supported for self calls: call owner=" + owner +
                                        ", target=" + this.thisClass + ", id=" + this.id);
                    }

                    // Use potentially renamed final method name (if @Unique caused a rename during pre-merge)
                    String callName = FinalNameRegistry
                            .lookup(this.targetOwnerInternalName, this.hook.owner(), this.hook.name(), this.hook.desc());
                    if (this.finalNameLookup != null) {
                        final String k = this.targetOwnerInternalName + "|" + this.hook.owner() + "#" + this.hook.name() + this.hook.desc();
                        final String resolved = this.finalNameLookup.apply(k, null);
                        if (resolved != null) callName = resolved;
                    }

                    final boolean handlerIsStatic = this.hook.invocation().isStatic();
                    validateRedirectSignatureOrThrow(
                            this.ctx,
                            opcode,
                            handlerIsStatic,
                            this.owner,
                            this.desc,
                            this.hook.desc(),
                            this.id
                    );

                    // Stack note:
                    // For a self-call, the original receiver 'this' is already on the stack.
                    // We just replace the call site to invoke our merged method on the same 'this'.
                    super.visitMethodInsn(INVOKESPECIAL, this.targetOwnerInternalName, callName, this.hook.desc(), false);
                }

                this.markChanged.run();
                this.applied = true;
                return;
            }
        }
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
    }


    /**
     * Ensures that at least one call site was rewritten unless the redirect is marked as optional.
     *
     * @throws IllegalStateException if no call site was rewritten and {@link #optional} is {@code false}
     */
    @Override
    public void visitEnd() {
        if (!this.applied && !this.optional) {
            throw new IllegalStateException(
                    "Redirect not applied (call site not found): id=" + this.id +
                            " owner=" + this.owner + " name=" + this.name + " desc=" + this.desc +
                            " ordinal=" + this.ordinal);
        }
        super.visitEnd();
    }

    /**
     * Checks whether the current invoke matches the redirect specification for owner/name/desc and kind.
     *
     * <p>When {@link #kind} is {@link Redirect.InvokeKind#AUTO}, the opcode check is skipped. For
     * {@link Redirect.InvokeKind#INVOKESPECIAL}, this method also accepts {@code INVOKEVIRTUAL}
     * invocations that target the same class (JDK 11+ private method behavior).</p>
     *
     * @param opcode      the current invoke opcode
     * @param owner       the internal owner name of the invocation being visited
     * @param name        the method name of the invocation being visited
     * @param desc        the JVM descriptor of the invocation being visited
     * @param isInterface whether the invocation owner is an interface (not used for matching)
     * @return {@code true} if the current invocation matches the redirect specification; otherwise {@code false}
     */
    private boolean matches(final int opcode,
                            @NotNull final String owner,
                            @NotNull final String name,
                            @NotNull final String desc,
                            final boolean isInterface) {
        if (!Objects.equals(owner, this.owner)) {
            return false;
        }
        if (!Objects.equals(name, this.name)) {
            return false;
        }
        if (!Objects.equals(desc, this.desc)) {
            return false;
        }

        boolean opcodeOk = switch (this.kind) {
            case AUTO -> true;
            case INVOKESTATIC -> opcode == INVOKESTATIC;
            case INVOKEVIRTUAL -> opcode == INVOKEVIRTUAL;
            case INVOKESPECIAL -> opcode == INVOKESPECIAL;
            case INVOKEINTERFACE -> opcode == INVOKEINTERFACE;
        };

        // Special case: since Java 11,
        // private methods in the same class are invoked with INVOKEVIRTUAL instead of INVOKESPECIAL.
        // To allow redirecting such calls, we accept INVOKEVIRTUAL calls to self as INVOKESPECIAL.
        // This guarantees backward compatibility to pre-Java 11 behavior.
        // (Even though we didn't support java versions prior to 17)
        if (!opcodeOk
                && this.kind == Redirect.InvokeKind.INVOKESPECIAL
                && opcode == INVOKEVIRTUAL
                && owner.equals(this.thisClass)) {
            opcodeOk = true;
        }

        return opcodeOk;
    }

    /**
     * Validates that the redirect handler signature is compatible with the original call site.
     *
     * <p>Rules:</p>
     * <ul>
     *   <li>The return types must match exactly.</li>
     *   <li>If the handler is static and declares an owner parameter, it must be a reference type
     *       (no primitive or array) and the remaining parameters must match the original call's
     *       parameters exactly.</li>
     *   <li>If the handler is static and does not declare an owner parameter, its parameters
     *       must match the original call's parameters exactly.</li>
     *   <li>If the handler is an instance method, it must not declare an owner parameter,
     *       and its parameters must match the original call's parameters exactly.</li>
     * </ul>
     *
     * @param ctx             human-readable context for diagnostics
     * @param handlerIsStatic whether the redirect handler is static
     * @param callOwner      internal JVM name of the original call owner (for diagnostics only)
     * @param callDesc       JVM descriptor of the original call (for diagnostics only)
     * @param hookDesc       JVM descriptor of the redirect handler (to validate)
     * @param id             developer-defined identifier for diagnostics
     * @throws IllegalStateException if the signatures are incompatible
     * @since 0.2.0
     */
    void validateRedirectSignatureOrThrow(@NotNull final String ctx,
                                          final int originalOpcode,
                                          final boolean handlerIsStatic,
                                          @NotNull final String callOwner,
                                          @NotNull final String callDesc,
                                          @NotNull final String hookDesc,
                                          @NotNull final String id) {
        Type[] callArgs = Type.getArgumentTypes(callDesc);
        Type[] hookArgs = Type.getArgumentTypes(hookDesc);
        Type callRet = Type.getReturnType(callDesc);
        Type hookRet = Type.getReturnType(hookDesc);

        boolean hasOwnerParam = hookArgs.length > 0 && hookArgs[0].getSort() == Type.OBJECT;

        // Return type must match (keep it strict)
        if (!hookRet.equals(callRet)) {
            throw new IllegalStateException(ctx + ": return type mismatch [id=" + id + "]");
        }

        if (handlerIsStatic) {
            if (hasOwnerParam) {
                // allow any reference type as owner param (don't force exact callOwner)
                if (hookArgs.length - 1 != callArgs.length) {
                    throw new IllegalStateException(ctx + ": arg count mismatch (static with owner) [id=" + id + "]");
                }
                // compare tail args with call args
                for (int i = 0; i < callArgs.length; i++) {
                    if (!hookArgs[i + 1].equals(callArgs[i])) {
                        throw new IllegalStateException(ctx + ": arg type mismatch @" + i + " (static with owner) [id=" + id + "]");
                    }
                }
            } else {
                // no owner param in static handler
                // Always OK for INVOKESTATIC callsites
                // For VIRTUAL/INTERFACE/SPECIAL: still OK if legacy allowed; recommend allow to keep backward compat
                if (hookArgs.length != callArgs.length) {
                    throw new IllegalStateException(ctx + ": arg count mismatch (static without owner) [id=" + id + "]");
                }
                for (int i = 0; i < callArgs.length; i++) {
                    if (!hookArgs[i].equals(callArgs[i])) {
                        throw new IllegalStateException(ctx + ": arg type mismatch @" + i + " (static without owner) [id=" + id + "]");
                    }
                }
            }
        } else {
            // instance handler
            if (hasOwnerParam) {
                throw new IllegalStateException(
                        ctx + ": instance redirect handler must NOT declare Owner param; either remove it or make handler static [id=" + id + "]"
                );
            }
            if (hookArgs.length != callArgs.length) {
                throw new IllegalStateException(ctx + ": arg count mismatch (instance) [id=" + id + "]");
            }
            for (int i = 0; i < callArgs.length; i++) {
                if (!hookArgs[i].equals(callArgs[i])) {
                    throw new IllegalStateException(ctx + ": arg type mismatch @" + i + " (instance) [id=" + id + "]");
                }
            }
        }
    }

}
