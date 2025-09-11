package de.splatgames.aether.mixins.core.config.runtime;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the verification strategy used when recomputing and validating
 * stack map frames during mixin bytecode transformations.
 *
 * <p>Stack map frames are part of the JVM's bytecode verification process.
 * They describe the state of the operand stack and local variables at specific
 * points in the code. When bytecode is modified by injecting or redirecting code,
 * these frames must often be updated or recalculated to maintain JVM validity.</p>
 *
 * <h2>Purpose</h2>
 * <p>The {@code VerifyFrames} setting controls how strictly the runtime
 * verifies and recomputes these stack frames:</p>
 *
 * <ul>
 *   <li><b>{@link #NONE NONE}</b> – No verification or recomputation is performed.
 *       <br>This is the fastest option but may result in invalid classes if transformations
 *       introduce inconsistencies. Use only for debugging or when external tooling
 *       guarantees valid frames.</li>
 *
 *   <li><b>{@link #BASIC BASIC}</b> – Standard verification and recomputation.
 *       <br>Frames are recomputed where necessary using basic algorithms to ensure
 *       JVM compatibility. This mode provides a balance between performance and safety.</li>
 *
 *   <li><b>{@link #STRICT STRICT}</b> – Maximum verification and extra validation.
 *       <br>In addition to recomputation, strict checks are performed to detect subtle
 *       issues such as type mismatches or invalid control flow. This mode is slower
 *       but recommended for production environments where stability is critical.</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <blockquote><pre>{@code
 * VerifyFrames level = VerifyFrames.fromString("strict");
 * if (level.isAtLeast(VerifyFrames.BASIC)) {
 *     System.out.println("Performing full frame recomputation...");
 * }
 * }</pre></blockquote>
 *
 * <h2>Default Behavior</h2>
 * <p>If no explicit setting is provided in the configuration, the runtime
 * defaults to {@link #STRICT} to ensure maximum safety.</p>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public enum VerifyFrames {

    /**
     * No verification or recomputation of stack frames.
     *
     * <p>Fastest option but unsafe. Transformed classes may fail to load
     * if they contain inconsistent or invalid bytecode.</p>
     */
    NONE(0),

    /**
     * Standard verification with basic frame recomputation.
     *
     * <p>Provides a balance between performance and safety by ensuring
     * transformed classes meet JVM requirements.</p>
     */
    BASIC(1),

    /**
     * Strict verification with additional checks and assertions.
     *
     * <p>This mode is slower but offers the highest level of safety by
     * detecting subtle inconsistencies that BASIC mode may miss.</p>
     */
    STRICT(2);

    /**
     * Lowercase name of this verification mode.
     */
    private final String name = this.name().toLowerCase();

    /**
     * Numeric level of this verification mode, used for comparisons.
     */
    private final int level;

    VerifyFrames(final int level) {
        this.level = level;
    }

    /**
     * Parses a verification mode from its string representation.
     *
     * <p>The comparison is case-insensitive. If the provided string does not
     * match a known value, {@code null} is returned.</p>
     *
     * @param name the string representation (e.g., "strict", "basic", "none"), must not be {@code null}
     * @return the corresponding {@code VerifyFrames} constant, or {@code null} if unrecognized
     */
    @Nullable
    public static VerifyFrames fromString(@NotNull final String name) {
        return switch (name.toLowerCase()) {
            case "none" -> NONE;
            case "basic" -> BASIC;
            case "strict" -> STRICT;
            default -> null;
        };
    }

    /**
     * Returns the lowercase name of this verification mode.
     *
     * @return lowercase name, never {@code null}
     */
    @NotNull
    public String getName() {
        return this.name;
    }

    /**
     * Returns the numeric level of this verification mode.
     *
     * <p>Higher levels indicate stricter verification.</p>
     *
     * @return integer level, starting at 0 for {@link #NONE}
     */
    public int getLevel() {
        return this.level;
    }

    /**
     * Determines whether this verification level is at least as strict as another.
     *
     * <p>This is useful for comparing modes in runtime logic where certain
     * operations require a minimum verification level.</p>
     *
     * @param other the verification level to compare against, must not be {@code null}
     * @return {@code true} if this level is greater than or equal to {@code other}, {@code false} otherwise
     */
    public boolean isAtLeast(@NotNull final VerifyFrames other) {
        return this.level >= other.level;
    }

    /**
     * Returns the lowercase string representation of this verification mode.
     *
     * @return lowercase name of this mode
     */
    @Override
    public String toString() {
        return this.name;
    }
}
