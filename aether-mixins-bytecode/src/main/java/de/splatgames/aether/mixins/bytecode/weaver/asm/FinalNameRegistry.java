package de.splatgames.aether.mixins.bytecode.weaver.asm;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.concurrent.ConcurrentHashMap;

public final class FinalNameRegistry {
    private static final ConcurrentHashMap<String, String> MAP = new ConcurrentHashMap<>();

    private FinalNameRegistry() {
    }

    @NotNull
    private static String key(@NotNull final String owner,
                              @NotNull final String name,
                              @NotNull final String desc) {
        return owner + "#" + name + desc;
    }

    public static void register(@NotNull final String targetOwner,
                                @NotNull final String originalName,
                                @NotNull final String desc,
                                @Nullable final String finalName) {
        if (finalName == null || finalName.equals(originalName)) return;

        final String k = key(targetOwner, originalName, desc);
        // first-wins: keep deterministic behavior, log if a different value is attempted
        final String prev = MAP.putIfAbsent(k, finalName);
        if (prev != null && !prev.equals(finalName)) {
            System.err.println("[FinalNameRegistry] conflicting mapping for " + k + ": " + prev + " vs " + finalName);
        }
    }

    @NotNull
    public static String lookup(@NotNull final String targetOwner,
                                @NotNull final String originalName,
                                @NotNull final String desc) {
        final String m = MAP.get(key(targetOwner, originalName, desc));
        return (m != null) ? m : originalName;
    }

    @Nullable
    public static String lookupByCompositeKey(@NotNull final String ownerPlusNameDesc) {
        return MAP.get(ownerPlusNameDesc);
    }

    @NotNull
    public static String composeKey(@NotNull final String owner,
                                    @NotNull final String name,
                                    @NotNull final String desc) {
        return key(owner, name, desc);
    }

    @VisibleForTesting
    static void clear() {
        MAP.clear();
    }
}
