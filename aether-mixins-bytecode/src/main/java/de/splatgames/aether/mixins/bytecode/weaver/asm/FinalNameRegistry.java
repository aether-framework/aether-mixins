package de.splatgames.aether.mixins.bytecode.weaver.asm;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for final (post-merge) method names of hook copies installed into a target class.
 *
 * <p>During pre-merge, instance and static hook methods originating in a mixin class may be
 * <em>copied</em> into the target class. When a copy would collide with an existing member,
 * the hook is deterministically renamed (e.g., due to {@code @Unique}). To allow subsequent
 * weaving stages (e.g., inject/redirect adapters) to reference the actual final name, this
 * registry records a mapping from the original symbol to the final, conflict-free symbol.</p>
 *
 * <h2>Key format</h2>
 * <p>Mappings are keyed by the triple {@code (targetOwner, originalName, desc)}. For convenience,
 * the composite key can be constructed via {@link #composeKey(String, String, String, String)} and queried
 * via {@link #lookupByCompositeKey(String)} when an adapter already has the composite form.</p>
 *
 * <h2>Semantics</h2>
 * <ul>
 *   <li><b>First wins:</b> The first registration for a given key is retained. Subsequent
 *       divergent registrations are ignored and produce a diagnostic on {@code System.err} to
 *       highlight inconsistent pre-merge behavior.</li>
 *   <li><b>Idempotent lookups:</b> If no mapping exists, {@link #lookup(String, String, String, String)}
 *       returns the original name (i.e., “no rename”).</li>
 *   <li><b>Thread-safety:</b> The registry is backed by a {@link ConcurrentHashMap} and supports
 *       concurrent read/write access during a single weaving run.</li>
 * </ul>
 *
 * <h2>Typical usage</h2>
 * <ol>
 *   <li>Pre-merge copies or renames a hook method and calls
 *       {@link #register(String, String, String, String, String)} with the final name.</li>
 *   <li>Later adapters (HEAD/TAIL injectors, redirects) query
 *       {@link #lookup(String, String, String, String)} or {@link #lookupByCompositeKey(String)}
 *       to obtain the actual name they must call on the target class.</li>
 * </ol>
 *
 * @author Erik Pförtner
 * @apiNote This registry intentionally stores only the <em>final name</em>. Ownership and descriptor
 * must be provided by the caller to avoid accidental cross-class collisions.
 * @implNote The registry logs conflicting mappings to {@code System.err} to avoid introducing a
 * dependency on a logging framework at this level.
 * @since 0.2.0
 */
public final class FinalNameRegistry {
    private static final ConcurrentHashMap<String, String> MAP = new ConcurrentHashMap<>();

    private FinalNameRegistry() {
        // utility class, prevent instantiation
    }

    /**
     * Builds the internal composite key used in the registry map from the target owner,
     * the original (pre-merge) method name, and its JVM descriptor.
     *
     * <p>The composite format is:
     * <pre>{@code
     *   targetOwner + "|" + mixinOwner + "#" + name + desc
     * }</pre>
     *
     * @param targetOwner internal JVM name (slash-separated) of the <em>target</em> class
     * @param name  original method name prior to any renaming
     * @param desc  JVM method descriptor, e.g. {@code (I)Ljava/lang/String;}
     * @return a non-null composite key string
     */
    @NotNull
    private static String key(@NotNull final String targetOwner,
                              @NotNull final String mixinOwner,
                              @NotNull final String name,
                              @NotNull final String desc) {
        return targetOwner + "|" + mixinOwner + "#" + name + desc;
    }

    /**
     * Registers the final (post-merge) method name for a hook copy installed into the given target owner.
     *
     * <p>If {@code finalName} is {@code null} or equals {@code originalName}, the call is a no-op.
     * Otherwise, the mapping is stored under the composite key
     * {@code (targetOwner, originalName, desc)} using “first wins” semantics.</p>
     *
     * <p>If a different mapping was already present for the same key, it is retained and a warning
     * is emitted to {@code System.err} to signal a potential planning inconsistency.</p>
     *
     * @param targetOwner  internal JVM name (slash-separated) of the target class receiving the hook copy
     * @param mixinOwner   internal JVM name (slash-separated) of the mixin class defining the hook
     * @param originalName original method name prior to any rename
     * @param desc         JVM method descriptor, e.g. {@code (I)V}
     * @param finalName    the conflict-free final name used in the target class; may be {@code null}
     * @implNote This method is safe for concurrent use. The first successfully published value
     * for a key is retained (via {@link ConcurrentHashMap#putIfAbsent(Object, Object)}).
     */
    public static void register(@NotNull final String targetOwner,
                                @NotNull final String mixinOwner,
                                @NotNull final String originalName,
                                @NotNull final String desc,
                                @Nullable final String finalName) {
        if (finalName == null || finalName.equals(originalName)) {
            return;
        }

        final String k = key(targetOwner, mixinOwner, originalName, desc);
        System.out.println("[FinalNameRegistry] registering final name: " + k + " -> " + finalName);
        // first-wins: keep deterministic behavior, log if a different value is attempted
        final String prev = MAP.putIfAbsent(k, finalName);
        if (prev != null && !prev.equals(finalName)) {
            System.err.println("[FinalNameRegistry] conflicting mapping for " + k + ": " + prev + " vs " + finalName);
        }
    }

    /**
     * Looks up the final (post-merge) method name associated with the given owner/name/descriptor triple.
     *
     * <p>If no mapping exists, this method returns {@code originalName} to denote that no rename
     * was applied.</p>
     *
     * @param targetOwner  internal JVM name (slash-separated) of the target class
     * @param mixinOwner   internal JVM name (slash-separated) of the mixin class defining the hook
     * @param originalName original method name prior to any rename
     * @param desc         JVM method descriptor
     * @return the final, conflict-free name if registered; otherwise {@code originalName}
     */
    @NotNull
    public static String lookup(@NotNull final String targetOwner,
                                @NotNull final String mixinOwner,
                                @NotNull final String originalName,
                                @NotNull final String desc) {
        final String m = MAP.get(key(targetOwner, mixinOwner, originalName, desc));
        return (m != null) ? m : originalName;
    }

    /**
     * Looks up the final name by a pre-built composite key.
     *
     * <p>This is a convenience for adapters that already track the composite form
     * ({@code owner + "#" + name + desc}). If no mapping exists, {@code null} is returned.</p>
     *
     * @param ownerPlusNameDesc the composite key as produced by {@link #composeKey(String, String, String, String)}
     * @return the mapped final name, or {@code null} if none is registered
     * @see #composeKey(String, String, String, String)
     */
    @Nullable
    public static String lookupByCompositeKey(@NotNull final String ownerPlusNameDesc) {
        return MAP.get(ownerPlusNameDesc);
    }

    /**
     * Produces the composite key used by this registry from the given components.
     *
     * <p>Equivalent to the internal {@link #key(String, String, String, String)} method, but public
     * for callers that need to construct keys in a consistent way.</p>
     *
     * @param targetOwner internal JVM name (slash-separated) of the target class
     * @param mixinOwner  internal JVM name (slash-separated) of the mixin class defining the hook
     * @param name  original method name prior to any rename
     * @param desc  JVM method descriptor
     * @return a non-null composite key string
     */
    @NotNull
    public static String composeKey(@NotNull final String targetOwner,
                                    @NotNull final String mixinOwner,
                                    @NotNull final String name,
                                    @NotNull final String desc) {
        return key(targetOwner, mixinOwner, name, desc);
    }

    /**
     * Clears all mappings from the registry.
     *
     * <p>Intended for unit tests to ensure isolation between test cases.</p>
     *
     * @implNote This method is package-private and annotated {@link VisibleForTesting}
     * to make its intent explicit; do not call this in production code paths.
     */
    @VisibleForTesting
    static void clear() {
        MAP.clear();
    }
}
