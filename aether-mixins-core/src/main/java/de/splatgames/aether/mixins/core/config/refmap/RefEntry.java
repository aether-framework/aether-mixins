package de.splatgames.aether.mixins.core.config.refmap;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Redirect;
import de.splatgames.aether.mixins.core.config.problems.ConfigProblems;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Describes a single refmap entry that binds a mixin hook to a concrete target.
 *
 * <p>An entry is either of {@link Type#INJECT INJECT} or {@link Type#REDIRECT REDIRECT} kind.
 * Depending on the {@linkplain #getType() type}, different attributes are required:</p>
 *
 * <ul>
 *   <li><b>INJECT</b>:
 *     <ul>
 *       <li>{@link #getMethod() method} (required) — target method as {@code name+descriptor}, e.g. {@code "doWork(I)I"}</li>
 *       <li>{@link #getAt() at} (required) — join point ({@link Inject.At#HEAD} / {@link Inject.At#TAIL})</li>
 *     </ul>
 *   </li>
 *   <li><b>REDIRECT</b>:
 *     <ul>
 *       <li>{@link #getMethod() method} (required) — enclosing target method as {@code name+descriptor}</li>
 *       <li>{@link #getCallOwner() callOwner} (required) — internal JVM name, e.g. {@code com/example/Util}</li>
 *       <li>{@link #getCallName() callName} (required) — simple method name, e.g. {@code calc}</li>
 *       <li>{@link #getCallDesc() callDesc} (required) — JVM descriptor, e.g. {@code (I)I}</li>
 *       <li>{@link #getKind() kind} (optional, default {@link Redirect.InvokeKind#AUTO}) — invoke opcode restriction</li>
 *       <li>{@link #getOrdinal() ordinal} (optional, default {@code -1}) — 0-based occurrence selector</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>Common attributes:</p>
 * <ul>
 *   <li>{@link #getId() id} — optional developer-defined identifier (default empty string)</li>
 *   <li>{@link #isOptional() optional} — when {@code true}, missing targets are tolerated in safe mode</li>
 *   <li>{@link #isRemap() remap} — hint that symbolic names may be remapped in obfuscated environments (default {@code true})</li>
 * </ul>
 *
 * <p><b>Note:</b> This model intentionally reuses {@link Inject.At} and {@link Redirect.InvokeKind}
 * from the public API to avoid enum duplication.</p>
 *
 * @see de.splatgames.aether.mixins.core.api.Mixin
 * @see de.splatgames.aether.mixins.core.api.Inject
 * @see de.splatgames.aether.mixins.core.api.Redirect
 * @since 0.1.0
 * @author Erik Pförtner
 */
public final class RefEntry {

    /**
     * Entry kind: {@link Type#INJECT} or {@link Type#REDIRECT}.
     */
    @Nullable
    private Type type;

    /**
     * Optional identifier for diagnostics and selective enablement (default empty).
     */
    @NotNull
    private String id = "";

    /**
     * Target method as {@code name+descriptor}, e.g. {@code "doWork(I)I"} (required).
     */
    @Nullable
    private String method;

    /**
     * Whether missing targets are tolerated in safe mode (default {@code false}).
     */
    private boolean optional = false;

    /**
     * Hint that remapping (obf ↔ deobf) may be applied (default {@code true}).
     */
    private boolean remap = true;

    /**
     * Injection join point for {@link Type#INJECT} entries (required for INJECT).
     */
    @Nullable
    private Inject.At at;

    /**
     * Internal owner name for {@link Type#REDIRECT} (required for REDIRECT), e.g. {@code com/example/Util}.
     */
    @Nullable
    private String callOwner;

    /**
     * Invoked method name for {@link Type#REDIRECT} (required for REDIRECT).
     */
    @Nullable
    private String callName;

    /**
     * Invoked method descriptor for {@link Type#REDIRECT} (required for REDIRECT), e.g. {@code (I)I}.
     */
    @Nullable
    private String callDesc;

    /**
     * Invoke opcode restriction for {@link Type#REDIRECT} (optional, default {@link Redirect.InvokeKind#AUTO}).
     */
    @NotNull
    private Redirect.InvokeKind kind = Redirect.InvokeKind.AUTO;

    /**
     * 0-based occurrence selector for {@link Type#REDIRECT} (default {@code -1} → first match).
     */
    private int ordinal = -1;

    /**
     * Creates an {@code INJECT} entry with the required attributes.
     *
     * @param method target method as {@code name+descriptor}, e.g. {@code "process(Ljava/lang/String;)V"}, must not be {@code null}
     * @param at     join point (HEAD/TAIL), must not be {@code null}
     * @return a new {@code RefEntry} configured for {@link Type#INJECT}
     */
    @NotNull
    public static RefEntry inject(@NotNull final String method, @NotNull final Inject.At at) {
        final RefEntry e = new RefEntry();
        e.type = Type.INJECT;
        e.method = method;
        e.at = at;
        return e;
    }

    /**
     * Creates a {@code REDIRECT} entry with the required attributes.
     *
     * @param method enclosing target method as {@code name+descriptor}, must not be {@code null}
     * @param owner  internal owner name (slash-separated), e.g. {@code "com/example/Util"}, must not be {@code null}
     * @param name   invoked method name, must not be {@code null}
     * @param desc   invoked method descriptor, must not be {@code null}
     * @return a new {@code RefEntry} configured for {@link Type#REDIRECT}
     */
    @NotNull
    public static RefEntry redirect(@NotNull final String method,
                                    @NotNull final String owner,
                                    @NotNull final String name,
                                    @NotNull final String desc) {
        final RefEntry e = new RefEntry();
        e.type = Type.REDIRECT;
        e.method = method;
        e.callOwner = owner;
        e.callName = name;
        e.callDesc = desc;
        return e;
    }

    /**
     * Checks whether a string is null, empty, or only whitespace.
     *
     * @param s the string to check, may be {@code null}
     * @return {@code true} if the string is null, empty, or only whitespace
     */
    private static boolean isBlank(@Nullable final String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * Simple heuristic to check whether a string looks like a method signature.
     *
     * @param s the string to check, must not be {@code null}
     * @return {@code true} if the string looks like a method signature
     */
    private static boolean looksLikeMethodSig(@NotNull final String s) {
        // name(<args>)<ret>; ensure ')' exists and there's at least one char after ')'
        final int lp = s.indexOf('(');
        final int rp = s.indexOf(')', lp + 1);
        return lp > 0 && rp > lp && rp < s.length() - 1;
    }

    /**
     * Simple heuristic to check whether a string looks like a JVM descriptor.
     *
     * @param s the string to check, must not be {@code null}
     * @return {@code true} if the string looks like a JVM descriptor
     */
    private static boolean looksLikeDesc(@NotNull final String s) {
        // simple check: "(...)" with at least one ')'
        return s.startsWith("(") && s.contains(")");
    }

    /**
     * Validates this entry and records problems into the given collector.
     *
     * <p>This method does <em>not</em> throw; use collected diagnostics to decide whether to proceed.
     * The {@code path} is used as a prefix for problem locations, e.g. {@code "mixins[0].entries[3]"}.</p>
     *
     * @param problems diagnostics collector, must not be {@code null}
     * @param path     human-readable configuration path used in diagnostics, must not be {@code null}
     */
    public void validate(@NotNull final ConfigProblems problems, @NotNull final String path) {
        if (this.type == null) {
            problems.error(path + ".type", "Entry type must be set (INJECT or REDIRECT).");
            return;
        }

        if (isBlank(this.method)) {
            problems.error(path + ".method", "Target method (name+descriptor) is required.");
        } else if (!looksLikeMethodSig(this.method)) {
            problems.warn(path + ".method", "Method should be in form name+descriptor, e.g. doWork(I)I");
        }

        switch (this.type) {
            case INJECT -> {
                if (this.at == null) {
                    problems.error(path + ".at", "Inject requires a join point (HEAD or TAIL).");
                }
                if (!isBlank(this.callOwner) || !isBlank(this.callName) || !isBlank(this.callDesc)) {
                    problems.warn(path, "INJECT entry defines redirect fields (callOwner/callName/callDesc) which will be ignored.");
                }
            }
            case REDIRECT -> {
                if (isBlank(this.callOwner)) {
                    problems.error(path + ".callOwner", "Redirect requires an internal owner name (e.g. com/example/Util).");
                } else if (this.callOwner.indexOf('.') >= 0) {
                    problems.warn(path + ".callOwner", "Owner should be the internal JVM name with slashes, not dots.");
                }
                if (isBlank(this.callName)) {
                    problems.error(path + ".callName", "Redirect requires a method name.");
                }
                if (isBlank(this.callDesc)) {
                    problems.error(path + ".callDesc", "Redirect requires a JVM descriptor (e.g. (I)I).");
                } else if (!looksLikeDesc(this.callDesc)) {
                    problems.warn(path + ".callDesc", "Descriptor format looks unusual; expected like (Args)Ret.");
                }
                if (this.ordinal < -1) {
                    problems.warn(path + ".ordinal", "Ordinal should be -1 (first match) or >= 0.");
                }
                if (this.at != null) {
                    problems.warn(path + ".at", "REDIRECT entry defines 'at' which will be ignored.");
                }
            }
        }
    }

    /**
     * @return the entry type, or {@code null} if not set
     */
    @Nullable
    public Type getType() {
        return this.type;
    }

    /**
     * Sets the entry type.
     *
     * @param type the type to set, must not be {@code null}
     */
    public void setType(@NotNull final Type type) {
        this.type = type;
    }

    /**
     * Returns the developer-defined identifier.
     *
     * @return non-null identifier (may be empty)
     */
    @NotNull
    public String getId() {
        return this.id;
    }

    /**
     * Sets the developer-defined identifier.
     *
     * @param id non-null identifier (use empty string if not needed)
     */
    public void setId(@NotNull final String id) {
        this.id = id;
    }

    /**
     * Returns the target method signature as {@code name+descriptor}.
     *
     * @return the method signature, or {@code null} if not set (required for both types)
     */
    @Nullable
    public String getMethod() {
        return this.method;
    }

    /**
     * Sets the target method signature as {@code name+descriptor}.
     *
     * @param method non-null signature, e.g. {@code "process(Ljava/lang/String;)V"}
     */
    public void setMethod(@NotNull final String method) {
        this.method = method;
    }

    /**
     * Indicates whether a missing target should be tolerated in safe mode.
     *
     * @return {@code true} if missing targets are tolerated; {@code false} otherwise
     */
    public boolean isOptional() {
        return this.optional;
    }

    /**
     * Sets whether missing targets should be tolerated in safe mode.
     *
     * @param optional {@code true} to tolerate missing targets
     */
    public void setOptional(final boolean optional) {
        this.optional = optional;
    }

    /**
     * Indicates whether remapping may be applied for symbolic names and descriptors.
     *
     * @return {@code true} if remapping is enabled; {@code false} otherwise
     */
    public boolean isRemap() {
        return this.remap;
    }

    /**
     * Sets whether remapping may be applied for symbolic names and descriptors. (obf ↔ deobf).
     *
     * @param remap {@code true} to enable remapping
     */
    public void setRemap(final boolean remap) {
        this.remap = remap;
    }

    /**
     * Returns the injection join point for {@link Type#INJECT} entries.
     *
     * @return {@link Inject.At#HEAD} or {@link Inject.At#TAIL}, or {@code null} if not set (required for INJECT)
     */
    @Nullable
    public Inject.At getAt() {
        return this.at;
    }

    /**
     * Sets the injection join point for {@link Type#INJECT} entries.
     *
     * @param at non-null join point
     */
    public void setAt(@NotNull final Inject.At at) {
        this.at = at;
    }

    /**
     * Returns the internal owner name of the redirected invocation for {@link Type#REDIRECT}.
     *
     * @return internal owner name (slash-separated), or {@code null} if not set (required for REDIRECT)
     */
    @Nullable
    public String getCallOwner() {
        return this.callOwner;
    }

    /**
     * Sets the internal owner name of the redirected invocation for {@link Type#REDIRECT}.
     *
     * @param callOwner non-null internal name, e.g. {@code "com/example/Util"}
     */
    public void setCallOwner(@NotNull final String callOwner) {
        this.callOwner = callOwner;
    }

    /**
     * Returns the method name of the redirected invocation for {@link Type#REDIRECT}.
     *
     * @return simple method name, or {@code null} if not set (required for REDIRECT)
     */
    @Nullable
    public String getCallName() {
        return this.callName;
    }

    /**
     * Sets the method name of the redirected invocation for {@link Type#REDIRECT}.
     *
     * @param callName non-null simple method name, e.g. {@code "calc"}
     */
    public void setCallName(@NotNull final String callName) {
        this.callName = callName;
    }

    /**
     * Returns the descriptor of the redirected invocation for {@link Type#REDIRECT}.
     *
     * @return JVM descriptor, or {@code null} if not set (required for REDIRECT)
     */
    @Nullable
    public String getCallDesc() {
        return this.callDesc;
    }

    /**
     * Sets the descriptor of the redirected invocation for {@link Type#REDIRECT}.
     *
     * @param callDesc non-null JVM descriptor, e.g. {@code "(I)I"}
     */
    public void setCallDesc(@NotNull final String callDesc) {
        this.callDesc = callDesc;
    }

    /**
     * Returns the invoke opcode restriction for {@link Type#REDIRECT}.
     *
     * @return non-null invoke kind (default {@link Redirect.InvokeKind#AUTO})
     */
    @NotNull
    public Redirect.InvokeKind getKind() {
        return this.kind;
    }

    /**
     * Sets the invoke opcode restriction for {@link Type#REDIRECT}.
     *
     * @param kind non-null invoke kind
     */
    public void setKind(@NotNull final Redirect.InvokeKind kind) {
        this.kind = kind;
    }

    /**
     * Returns the 0-based occurrence selector for {@link Type#REDIRECT}.
     *
     * <p>A value of {@code -1} selects the first matching invocation.</p>
     *
     * @return ordinal, or {@code -1} for the first match
     */
    public int getOrdinal() {
        return this.ordinal;
    }

    /**
     * Sets the 0-based occurrence selector for {@link Type#REDIRECT}.
     *
     * @param ordinal ordinal index, or {@code -1} for the first match
     */
    public void setOrdinal(final int ordinal) {
        this.ordinal = ordinal;
    }

    /**
     * Helper to check whether this entry is of type {@link Type#INJECT}.
     *
     * @return {@code true} if this entry is of type {@link Type#INJECT}.
     */
    public boolean isInject() {
        return this.type == Type.INJECT;
    }

    /**
     * Helper to check whether this entry is of type {@link Type#REDIRECT}.
     *
     * @return {@code true} if this entry is of type {@link Type#REDIRECT}.
     */
    public boolean isRedirect() {
        return this.type == Type.REDIRECT;
    }

    @Override
    public String toString() {
        return "RefEntry{" +
                "type=" + this.type +
                ", id='" + this.id + '\'' +
                ", method='" + this.method + '\'' +
                ", optional=" + this.optional +
                ", remap=" + this.remap +
                ", at=" + this.at +
                ", callOwner='" + this.callOwner + '\'' +
                ", callName='" + this.callName + '\'' +
                ", callDesc='" + this.callDesc + '\'' +
                ", kind=" + this.kind +
                ", ordinal=" + this.ordinal +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof RefEntry that)) return false;
        return this.optional == that.optional &&
                this.remap == that.remap &&
                this.ordinal == that.ordinal &&
                this.type == that.type &&
                Objects.equals(this.id, that.id) &&
                Objects.equals(this.method, that.method) &&
                this.at == that.at &&
                Objects.equals(this.callOwner, that.callOwner) &&
                Objects.equals(this.callName, that.callName) &&
                Objects.equals(this.callDesc, that.callDesc) &&
                this.kind == that.kind;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                this.type,
                this.id,
                this.method,
                this.optional,
                this.remap,
                this.at,
                this.callOwner,
                this.callName,
                this.callDesc,
                this.kind,
                this.ordinal
        );
    }

    /**
     * Entry kinds supported by the refmap.
     */
    public enum Type {
        /**
         * Injection at a well-defined join point of a target method.
         */
        INJECT,
        /**
         * Redirection of a specific invocation inside a target method.
         */
        REDIRECT
    }
}
