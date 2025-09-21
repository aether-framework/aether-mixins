package de.splatgames.aether.mixins.bytecode.weaver.asm;

import de.splatgames.aether.mixins.bytecode.weaver.asm.resolver.HookScanner;
import de.splatgames.aether.mixins.bytecode.weaver.hook.CandidateHook;
import de.splatgames.aether.mixins.bytecode.weaver.hook.HookResolver;
import de.splatgames.aether.mixins.bytecode.weaver.hook.ResolvedHook;
import de.splatgames.aether.mixins.core.api.Redirect;
import de.splatgames.aether.mixins.core.config.problems.ConfigProblems;
import de.splatgames.aether.mixins.core.plan.PlannedEntry;
import de.splatgames.aether.mixins.core.plan.PlannedMixin;
import de.splatgames.aether.mixins.core.weaver.spi.ClassSource;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * ASM-based {@link HookResolver} that loads a mixin class, scans its bytecode
 * for {@code @Inject}/{@code @Redirect} annotated static methods, and resolves
 * exactly one matching hook for a given {@link PlannedEntry}.
 *
 * <p>Resolution workflow:</p>
 * <ol>
 *   <li>Load class bytes via {@link ClassSource}.</li>
 *   <li>Scan for static methods carrying the required annotation (via {@link HookScanner}).</li>
 *   <li>Filter by the planned {@code id} selection policy.</li>
 *   <li>Perform minimal kind-specific validation where applicable.</li>
 * </ol>
 *
 * <h3>Example</h3>
 * <blockquote><pre>
 * ClassSource source = ...;
 * AsmHookResolver resolver = new AsmHookResolver(source);
 * Optional&lt;ResolvedHook&gt; hook =
 *     resolver.resolve(mixin, entry, problems, "planner/MyMixin#entry0");
 * </pre></blockquote>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class AsmHookResolver implements HookResolver {

    /**
     * Source used to load class bytecode for scanning.
     */
    @NotNull
    private final ClassSource source;

    /**
     * Creates a new resolver instance.
     *
     * @param source the bytecode source used to read mixin classes; must not be {@code null}
     * @throws NullPointerException if {@code source} is {@code null}
     */
    public AsmHookResolver(@NotNull final ClassSource source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    /**
     * Resolves exactly one hook for the given {@link PlannedEntry} by scanning the mixin class
     * and validating potential candidates.
     *
     * @param mixin    the planned mixin providing the binary class name to scan
     * @param entry    the planned entry containing kind, optional id, and call target details
     * @param problems diagnostics sink used to report warnings and errors during resolution
     * @param path     human-readable diagnostics path used in messages (e.g., {@code planner/MyMixin#0})
     * @return a non-empty {@link Optional} containing the resolved hook if resolution succeeds; otherwise {@link Optional#empty()}
     */
    @NotNull
    @Override
    public Optional<ResolvedHook> resolve(
            @NotNull final PlannedMixin mixin,
            @NotNull final PlannedEntry entry,
            @NotNull final ConfigProblems problems,
            @NotNull final String path
    ) {
        final String internal = toInternalName(mixin.getClassName());
        final byte[] bytes;
        try {
            bytes = this.source.getClassBytes(internal);
        } catch (final IOException ioe) {
            problems.error(path, "Failed to read mixin class bytes: " + ioe.getMessage());
            return Optional.empty();
        }
        if (bytes == null) {
            problems.error(path, "Mixin class not found: " + internal);
            return Optional.empty();
        }

        // (1) Scan candidates from the mixin class
        final HookScanner scanner = new HookScanner(entry.getKind());
        final List<CandidateHook> candidates = scanner.scan(bytes, internal, problems, path);

        // (2) Filter by planned id policy
        final List<CandidateHook> matched = filterById(candidates, entry.getId(), problems, path);
        if (matched.isEmpty()) {
            problems.error(path, "No hook method resolved in " + internal + " (kind=" + entry.getKind() +
                    (entry.getId().isEmpty() ? ", no id provided)" : ", id='" + entry.getId() + "')"));
            return Optional.empty();
        }
        if (matched.size() > 1) {
            problems.error(path, "Ambiguous hook resolution in " + internal + " for id='" + entry.getId() +
                    "': " + matched.size() + " candidates");
            return Optional.empty();
        }

        final CandidateHook mi = matched.get(0);

        // (3) Minimal validation for redirects (inject hooks are validated during weaving where full context is available)
        if (entry.getKind() != PlannedEntry.Kind.INJECT) {
            final String expected = expectedRedirectHookDesc(
                    entry.getInvokeKind(), entry.getCallOwner(), entry.getCallDesc()
            );
            if (!expected.equals(mi.desc)) {
                problems.warn(path, "Redirect hook descriptor mismatch (expected " + expected +
                        ", found " + mi.desc + ") at " + internal + "." + mi.name + mi.desc);
            }
        }

        return Optional.of(new ResolvedHook(internal, mi.name, mi.desc, mi.invocation));
    }

    /**
     * Filters a list of candidate hooks according to the planned id policy.
     *
     * <p>Behavior:</p>
     * <ul>
     *   <li>If {@code wantedId} is empty: succeed only when there is exactly one candidate; otherwise return empty.</li>
     *   <li>If {@code wantedId} is non-empty: return candidates whose annotation id equals {@code wantedId};
     *       if none match, a warning is emitted.</li>
     * </ul>
     *
     * @param candidates the scanned candidate hooks to filter
     * @param wantedId   the planned id to match (may be empty)
     * @param problems   diagnostics sink used to report a warning if no id matches
     * @param path       diagnostics path used in messages
     * @return a list of matching candidates (possibly empty)
     */
    @NotNull
    private static List<CandidateHook> filterById(
            @NotNull final List<CandidateHook> candidates,
            @NotNull final String wantedId,
            @NotNull final ConfigProblems problems,
            @NotNull final String path
    ) {
        if (wantedId.isEmpty()) {
            return candidates.size() == 1 ? List.of(candidates.get(0)) : List.of();
        }
        final List<CandidateHook> out = new ArrayList<>();
        for (final CandidateHook mi : candidates) {
            if (wantedId.equals(mi.annotationId)) {
                out.add(mi);
            }
        }
        if (out.isEmpty()) {
            problems.warn(path, "No candidate with matching id='" + wantedId + "'; found " + candidates.size() + " candidates");
        }
        return out;
    }

    /**
     * Computes the expected JVM descriptor for a {@link Redirect} hook based on the invocation kind and
     * the original call descriptor.
     *
     * <p>Rules:</p>
     * <ul>
     *   <li>For {@link Redirect.InvokeKind#INVOKESTATIC} and {@link Redirect.InvokeKind#AUTO}: the hook descriptor
     *       is identical to the original method descriptor.</li>
     *   <li>For instance calls ({@link Redirect.InvokeKind#INVOKEVIRTUAL}, {@link Redirect.InvokeKind#INVOKESPECIAL},
     *       {@link Redirect.InvokeKind#INVOKEINTERFACE}): the receiver type (owner) is prepended as the first argument,
     *       and the rest of the signature (remaining arguments and return type) must match.</li>
     * </ul>
     *
     * @param kind     the invocation kind (static/virtual/special/interface/auto)
     * @param ownerInt the internal JVM name of the owner class (e.g., {@code com/example/Util})
     * @param callDesc the original target method descriptor (e.g., {@code (I)I})
     * @return the expected JVM descriptor for the redirect hook
     */
    @NotNull
    private static String expectedRedirectHookDesc(
            @NotNull final Redirect.InvokeKind kind,
            @NotNull final String ownerInt,
            @NotNull final String callDesc
    ) {
        final int rParen = callDesc.lastIndexOf(')');
        final String args = callDesc.substring(1, rParen);
        final String ret  = callDesc.substring(rParen + 1);

        return switch (kind) {
            case INVOKESTATIC, AUTO -> "(" + args + ")" + ret;
            case INVOKEVIRTUAL, INVOKESPECIAL, INVOKEINTERFACE -> "(L" + ownerInt + ";" + args + ")" + ret;
        };
    }

    /**
     * Converts a binary class name (dot notation) to an internal JVM name (slash notation).
     *
     * @param binaryName the binary name (e.g., {@code com.example.Foo$Bar})
     * @return the internal name (e.g., {@code com/example/Foo$Bar})
     */
    @NotNull
    private static String toInternalName(@NotNull final String binaryName) {
        return binaryName.replace('.', '/');
    }
}
