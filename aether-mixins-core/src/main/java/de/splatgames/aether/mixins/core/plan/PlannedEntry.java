package de.splatgames.aether.mixins.core.plan;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Redirect;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Planned hook entry resolved from a refmap entry.
 *
 * <p>Instances are immutable and guaranteed to satisfy the invariants for their
 * {@link Kind}:
 * <ul>
 *   <li>{@link Kind#INJECT}: {@code at != null}, {@code callOwner/callName/callDesc == null}</li>
 *   <li>{@link Kind#REDIRECT}: {@code callOwner/callName/callDesc != null}, {@code at == null}</li>
 * </ul>
 * </p>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class PlannedEntry {

    @NotNull
    private final Kind kind;
    @NotNull
    private final String method;   // name+desc
    @NotNull
    private final String id;       // may be empty
    private final boolean optional;
    private final boolean remap;

    // INJECT-only
    @Nullable
    private
    final Inject.At at;

    // REDIRECT-only
    @Nullable
    private final String callOwner; // internal JVM name
    @Nullable
    private final String callName;
    @Nullable
    private final String callDesc;
    @NotNull
    private final Redirect.InvokeKind invokeKind; // defaults to AUTO
    private final int ordinal; // -1 = first

    /**
     * Creates a planned entry.
     *
     * <p><b>Invariants:</b> The constructor enforces that required fields for the chosen {@link Kind}
     * are present and that mutually exclusive fields are absent.</p>
     *
     * @param kind       entry kind, must not be {@code null}
     * @param method     target method signature ({@code name+descriptor}), must not be {@code null}
     * @param id         developer-defined identifier, must not be {@code null} (may be empty)
     * @param optional   whether missing targets are tolerated in safe mode
     * @param remap      whether remapping is hinted for this entry
     * @param at         injection point (INJECT only), otherwise {@code null}
     * @param callOwner  internal owner name (REDIRECT only), otherwise {@code null}
     * @param callName   invoked method name (REDIRECT only), otherwise {@code null}
     * @param callDesc   invoked method descriptor (REDIRECT only), otherwise {@code null}
     * @param invokeKind invocation kind (REDIRECT only; {@link Redirect.InvokeKind#AUTO} is valid), must not be {@code null}
     * @param ordinal    occurrence selector for REDIRECT ({@code -1} = first match)
     * @throws IllegalArgumentException if invariants for the given {@code kind} are violated
     */
    private PlannedEntry(
            @NotNull final Kind kind,
            @NotNull final String method,
            @NotNull final String id,
            final boolean optional,
            final boolean remap,
            @Nullable final Inject.At at,
            @Nullable final String callOwner,
            @Nullable final String callName,
            @Nullable final String callDesc,
            @NotNull final Redirect.InvokeKind invokeKind,
            final int ordinal
    ) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.method = Objects.requireNonNull(method, "method");
        this.id = Objects.requireNonNull(id, "id");
        this.optional = optional;
        this.remap = remap;
        this.at = at;
        this.callOwner = callOwner;
        this.callName = callName;
        this.callDesc = callDesc;
        this.invokeKind = Objects.requireNonNull(invokeKind, "invokeKind");
        this.ordinal = ordinal;

        // Invariant checks
        switch (this.kind) {
            case INJECT -> {
                if (this.at == null) {
                    throw new IllegalArgumentException("INJECT requires non-null 'at'");
                }
                if (this.callOwner != null || this.callName != null || this.callDesc != null) {
                    throw new IllegalArgumentException("INJECT must not define callOwner/callName/callDesc");
                }
            }
            case REDIRECT -> {
                if (this.callOwner == null || this.callName == null || this.callDesc == null) {
                    throw new IllegalArgumentException("REDIRECT requires callOwner, callName, callDesc");
                }
                if (this.at != null) {
                    throw new IllegalArgumentException("REDIRECT must not define 'at'");
                }
            }
        }
    }

    /**
     * Factory for {@link Kind#INJECT}.
     *
     * @param method   target method as {@code name+descriptor}, must not be {@code null}
     * @param id       identifier (may be empty), must not be {@code null}
     * @param optional tolerate missing targets in safe mode
     * @param remap    hint that remapping may be applied
     * @param at       injection join point, must not be {@code null}
     * @return new {@code PlannedEntry} of kind INJECT, never {@code null}
     */
    @NotNull
    public static PlannedEntry inject(
            @NotNull final String method,
            @NotNull final String id,
            final boolean optional,
            final boolean remap,
            @NotNull final Inject.At at
    ) {
        return new PlannedEntry(Kind.INJECT, method, id, optional, remap,
                at, null, null, null, Redirect.InvokeKind.AUTO, -1);
    }

    /**
     * Factory for {@link Kind#REDIRECT}.
     *
     * @param method     enclosing target method as {@code name+descriptor}, must not be {@code null}
     * @param id         identifier (may be empty), must not be {@code null}
     * @param optional   tolerate missing targets in safe mode
     * @param remap      hint that remapping may be applied
     * @param callOwner  internal owner name (slash-separated), must not be {@code null}
     * @param callName   invoked method name, must not be {@code null}
     * @param callDesc   invoked method descriptor, must not be {@code null}
     * @param invokeKind invocation kind (e.g., {@code AUTO}, {@code INVOKEVIRTUAL}), must not be {@code null}
     * @param ordinal    0-based occurrence selector; {@code -1} for the first match
     * @return new {@code PlannedEntry} of kind REDIRECT, never {@code null}
     */
    @NotNull
    public static PlannedEntry redirect(
            @NotNull final String method,
            @NotNull final String id,
            final boolean optional,
            final boolean remap,
            @NotNull final String callOwner,
            @NotNull final String callName,
            @NotNull final String callDesc,
            @NotNull final Redirect.InvokeKind invokeKind,
            final int ordinal
    ) {
        return new PlannedEntry(Kind.REDIRECT, method, id, optional, remap,
                null, callOwner, callName, callDesc, invokeKind, ordinal);
    }

    /**
     * Returns the entry kind.
     *
     * @return kind, never {@code null}
     */
    @NotNull
    public Kind getKind() {
        return this.kind;
    }

    /**
     * Returns the target method signature as {@code name+descriptor}.
     *
     * @return method signature, never {@code null}
     */
    @NotNull
    public String getMethod() {
        return this.method;
    }

    /**
     * Returns the developer-defined identifier (may be empty).
     *
     * @return identifier, never {@code null}
     */
    @NotNull
    public String getId() {
        return this.id;
    }

    /**
     * Indicates whether a missing target should be tolerated in safe mode.
     *
     * @return {@code true} if missing targets are tolerated
     */
    public boolean isOptional() {
        return this.optional;
    }

    /**
     * Indicates whether remapping is hinted for this entry.
     *
     * @return {@code true} if remapping is hinted
     */
    public boolean isRemap() {
        return this.remap;
    }

    /**
     * Returns the injection join point (INJECT only).
     *
     * @return join point or {@code null} for REDIRECT entries
     */
    @Nullable
    public Inject.At getAt() {
        return this.at;
    }

    /**
     * Returns the internal owner name (REDIRECT only).
     *
     * @return owner or {@code null} for INJECT entries
     */
    @Nullable
    public String getCallOwner() {
        return this.callOwner;
    }

    /**
     * Returns the invoked method name (REDIRECT only).
     *
     * @return name or {@code null} for INJECT entries
     */
    @Nullable
    public String getCallName() {
        return this.callName;
    }

    /**
     * Returns the invoked method descriptor (REDIRECT only).
     *
     * @return descriptor or {@code null} for INJECT entries
     */
    @Nullable
    public String getCallDesc() {
        return this.callDesc;
    }

    /**
     * Returns the invocation kind (REDIRECT only).
     *
     * @return invocation kind, never {@code null}; {@link Redirect.InvokeKind#AUTO} for INJECT entries
     */
    @NotNull
    public Redirect.InvokeKind getInvokeKind() {
        return this.invokeKind;
    }

    /**
     * Returns the 0-based occurrence selector for REDIRECT.
     *
     * @return {@code -1} for first match; 0+ for explicit ordinal
     */
    public int getOrdinal() {
        return this.ordinal;
    }

    @Override
    public String toString() {
        return "PlannedEntry{" +
                "kind=" + this.kind +
                ", method='" + this.method + '\'' +
                ", id='" + this.id + '\'' +
                ", optional=" + this.optional +
                ", remap=" + this.remap +
                ", at=" + this.at +
                ", callOwner='" + this.callOwner + '\'' +
                ", callName='" + this.callName + '\'' +
                ", callDesc='" + this.callDesc + '\'' +
                ", invokeKind=" + this.invokeKind +
                ", ordinal=" + this.ordinal +
                '}';
    }

    /**
     * Entry kind.
     */
    public enum Kind {
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
