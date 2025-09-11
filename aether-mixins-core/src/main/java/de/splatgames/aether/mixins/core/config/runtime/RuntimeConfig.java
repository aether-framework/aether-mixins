package de.splatgames.aether.mixins.core.config.runtime;

import de.splatgames.aether.mixins.core.config.problems.ConfigProblems;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Defines runtime options that control verification, safety, and diagnostics
 * for the Aether Mixins engine.
 *
 * <p>All options are optional. If this section is omitted in the external
 * configuration, the runtime applies sensible defaults. In particular,
 * {@link #isSafeMode() safe mode} defaults to {@code true} and
 * {@link #getVerifyFrames() frame verification} defaults to {@link VerifyFrames#STRICT}.</p>
 *
 * <h2>Frame Verification</h2>
 * <p>The {@link #getVerifyFrames() verifyFrames} setting governs how stack map frames
 * are (re)computed and validated after bytecode transformations:
 * see {@link VerifyFrames} for details on {@link VerifyFrames#NONE NONE},
 * {@link VerifyFrames#BASIC BASIC}, and {@link VerifyFrames#STRICT STRICT} modes.</p>
 *
 * <h2>Diagnostics</h2>
 * <p>During parsing/validation, warnings and errors can be recorded via the
 * associated {@link ConfigProblems} instance exposed by {@link #problems()}.</p>
 *
 * <h3>Example (YAML)</h3>
 * <blockquote><pre>{@code
 * runtime:
 *   safe_mode: true
 *   verify_frames: strict
 *   dump_classes_on_error: false
 * }</pre></blockquote>
 *
 * @author Erik Pförtner
 * @see VerifyFrames
 * @see ConfigProblems
 * @since 0.1.0
 */
public final class RuntimeConfig {

    /**
     * Collector for configuration diagnostics (warnings and errors) originating
     * from parsing or normalization of runtime options.
     */
    private final ConfigProblems problems;

    /**
     * When {@code true}, failed weave attempts are skipped rather than causing
     * startup failure. Recommended for production unless strict startup guarantees
     * are required.
     */
    private boolean safeMode = true;

    /**
     * Strategy for stack map frame verification and (re)computation.
     *
     * <p>Defaults to {@link VerifyFrames#STRICT} for maximum safety. See
     * {@link VerifyFrames} for detailed semantics of each level.</p>
     */
    private VerifyFrames verifyFrames = VerifyFrames.STRICT;

    /**
     * When {@code true}, the runtime may dump original and transformed class bytes
     * upon errors to aid debugging and offline analysis.
     */
    private boolean dumpClassesOnError = false;

    /**
     * Creates a new {@code RuntimeConfig} bound to the given diagnostics container.
     *
     * @param problems the problem collector used to record warnings/errors, must not be {@code null}
     */
    public RuntimeConfig(@NotNull final ConfigProblems problems) {
        this.problems = problems;
    }

    /**
     * Returns the diagnostics container for configuration problems.
     *
     * @return the associated {@link ConfigProblems} instance, never {@code null}
     */
    @NotNull
    public ConfigProblems problems() {
        return this.problems;
    }

    /**
     * Returns whether safe mode is enabled.
     *
     * @return {@code true} if safe mode is enabled, {@code false} otherwise
     */
    public boolean isSafeMode() {
        return this.safeMode;
    }

    /**
     * Enables or disables safe mode.
     *
     * @param safeMode {@code true} to enable safe mode, {@code false} to disable it
     */
    public void setSafeMode(final boolean safeMode) {
        this.safeMode = safeMode;
    }

    /**
     * Returns the configured frame verification strategy.
     *
     * @return the {@link VerifyFrames} level, never {@code null}
     */
    @NotNull
    public VerifyFrames getVerifyFrames() {
        return this.verifyFrames;
    }

    /**
     * Sets the frame verification strategy.
     *
     * @param verifyFrames the desired {@link VerifyFrames} level, must not be {@code null}
     */
    public void setVerifyFrames(@NotNull final VerifyFrames verifyFrames) {
        this.verifyFrames = verifyFrames;
    }

    /**
     * Parses and applies the {@code verify_frames} option from its string representation.
     *
     * <p>Accepted values (case-insensitive): {@code "none"}, {@code "basic"}, {@code "strict"}.
     * Unknown values trigger a warning via {@link #problems()} and default to
     * {@link VerifyFrames#STRICT}.</p>
     *
     * @param verifyFramesValue string value to parse; if {@code null}, this method does nothing
     * @param path configuration path used for diagnostics (e.g., {@code "runtime.verify_frames"}), must not be {@code null}
     */
    public void setVerifyFramesString(@Nullable final String verifyFramesValue, @NotNull final String path) {
        if (verifyFramesValue == null) {
            return;
        }

        switch (verifyFramesValue.toLowerCase()) {
            case "none" -> this.verifyFrames = VerifyFrames.NONE;
            case "basic" -> this.verifyFrames = VerifyFrames.BASIC;
            case "strict" -> this.verifyFrames = VerifyFrames.STRICT;
            default -> {
                problems.warn(path, "Invalid verify_frames value '" + verifyFramesValue + "', defaulting to 'strict'");
                this.verifyFrames = VerifyFrames.STRICT;
            }
        }
    }

    /**
     * Returns whether class bytes should be dumped upon transformation errors.
     *
     * @return {@code true} if class dumps are enabled on error, {@code false} otherwise
     */
    public boolean isDumpClassesOnError() {
        return this.dumpClassesOnError;
    }

    /**
     * Enables or disables dumping of class bytes upon transformation errors.
     *
     * @param dumpClassesOnError {@code true} to enable class dumps on error
     */
    public void setDumpClassesOnError(final boolean dumpClassesOnError) {
        this.dumpClassesOnError = dumpClassesOnError;
    }

    /**
     * Returns a concise string representation for diagnostics.
     *
     * @return string containing current option values
     */
    @Override
    public String toString() {
        return "RuntimeConfig{safeMode=" + this.safeMode +
                ", verifyFrames=" + this.verifyFrames +
                ", dumpClassesOnError=" + this.dumpClassesOnError + '}';
    }

    /**
     * Computes a hash code based on option values.
     *
     * @return hash code for this configuration
     */
    @Override
    public int hashCode() {
        return Objects.hash(this.safeMode, this.verifyFrames, this.dumpClassesOnError);
    }

    /**
     * Compares this configuration with another for equality.
     *
     * @param o the object to compare with
     * @return {@code true} if both instances have equal option values; {@code false} otherwise
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof RuntimeConfig that)) return false;
        return this.safeMode == that.safeMode &&
                this.dumpClassesOnError == that.dumpClassesOnError &&
                Objects.equals(this.verifyFrames, that.verifyFrames);
    }
}
