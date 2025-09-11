package de.splatgames.aether.mixins.bytecode.weaver.hook;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Resolved hook method symbol.
 *
 * <p>All fields use JVM internal/binary formats:
 * <ul>
 *   <li>{@code owner}: internal class name (e.g. {@code com/example/MyMixin})</li>
 *   <li>{@code name}: simple method name</li>
 *   <li>{@code desc}: JVM descriptor (e.g. {@code ()V}, {@code (Ljava/lang/String;I)I})</li>
 * </ul>
 * </p>
 *
 * @param owner internal class name, never {@code null}
 * @param name  method name, never {@code null}
 * @param desc  method descriptor, never {@code null}
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record ResolvedHook(@NotNull String owner, @NotNull String name, @NotNull String desc) {
    public ResolvedHook {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(desc, "desc");
    }
}
