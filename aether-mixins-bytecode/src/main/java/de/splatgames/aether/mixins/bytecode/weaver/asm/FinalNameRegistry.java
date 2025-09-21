package de.splatgames.aether.mixins.bytecode.weaver.asm;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;

public final class FinalNameRegistry {
    private static final ConcurrentHashMap<String, String> MAP = new ConcurrentHashMap<>();

    private FinalNameRegistry() {
        // prevent instantiation for utility class
    }

    @NotNull
    private static String key(@NotNull final String owner, @NotNull final String name, @NotNull final String desc) {
        return owner + "#" + name + desc;
    }

    public static void register(@NotNull final String targetOwner, @NotNull final String name, @NotNull final String desc, @Nullable final String finalName) {
        if (finalName != null && !finalName.equals(name)) {
            MAP.put(key(targetOwner, name, desc), finalName);
        }
    }

    @Nullable
    public static String lookup(@NotNull final String ownerPlusNameDesc, @NotNull final String _unused) {
        return MAP.get(ownerPlusNameDesc);
    }
}
