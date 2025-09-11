package de.splatgames.aether.mixins.core.config.problems;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects configuration validation problems encountered during parsing or loading
 * of a mixin configuration file.
 *
 * <p>The {@code ConfigProblems} class acts as a central aggregator for both warnings
 * and errors. Each problem is represented by a {@link Problem} record containing
 * its {@link Severity}, the affected configuration path, and a human-readable
 * message.</p>
 *
 * <h2>Usage</h2>
 * <p>This class is typically used by configuration loaders or validators to accumulate
 * issues found while processing a configuration file. Problems can be retrieved later
 * and reported to the user or logged.</p>
 *
 * <h3>Example</h3>
 * <blockquote><pre>{@code
 * ConfigProblems problems = new ConfigProblems("mixins.yml");
 *
 * problems.warn("runtime.verify_frames",
 *     "Unrecognized value 'fast', falling back to 'strict'.");
 *
 * problems.error("mixins[0].files",
 *     "Mixin set 'core-service' has no associated files.");
 *
 * if (problems.hasErrors()) {
 *     for (ConfigProblems.Problem problem : problems.all()) {
 *         System.err.println(problem);
 *     }
 *     throw new IllegalStateException("Configuration contains errors");
 * }
 * }</pre></blockquote>
 *
 * @author Erik Pförtner
 * @see Problem
 * @since 0.1.0
 */
public final class ConfigProblems {

    /**
     * Human-readable context string describing the source of these problems,
     * such as a filename or resource identifier.
     *
     * <p>This value is typically included in diagnostic messages to help users
     * identify where the reported problems originated.</p>
     */
    private final String context;

    /**
     * Internal list of collected problems.
     */
    private final List<Problem> problems = new ArrayList<>();

    /**
     * Creates a new {@code ConfigProblems} instance with the specified context.
     *
     * @param context the descriptive context (e.g., a file path), must not be {@code null}
     */
    public ConfigProblems(@NotNull final String context) {
        this.context = context;
    }

    /**
     * Returns the context associated with this problem collection.
     *
     * @return the context string, never {@code null}
     */
    @NotNull
    public String context() {
        return this.context;
    }

    /**
     * Records a warning-level problem.
     *
     * <p>Warnings indicate non-critical issues that do not prevent the configuration
     * from being used but may lead to unexpected behavior.</p>
     *
     * @param path the configuration path where the problem occurred, must not be {@code null}
     * @param msg  a human-readable description of the problem, must not be {@code null}
     * @see Severity#WARNING
     */
    public void warn(@NotNull final String path, @NotNull final String msg) {
        this.problems.add(new Problem(Severity.WARNING, path, msg));
    }

    /**
     * Records an error-level problem.
     *
     * <p>Errors indicate critical issues that usually prevent the configuration
     * from being successfully loaded or used.</p>
     *
     * @param path the configuration path where the problem occurred, must not be {@code null}
     * @param msg  a human-readable description of the problem, must not be {@code null}
     * @see Severity#ERROR
     */
    public void error(@NotNull final String path, @NotNull final String msg) {
        this.problems.add(new Problem(Severity.ERROR, path, msg));
    }

    /**
     * Determines whether any errors have been recorded.
     *
     * @return {@code true} if at least one error exists, {@code false} otherwise
     */
    public boolean hasErrors() {
        return this.problems.stream().anyMatch(p -> p.severity == Severity.ERROR);
    }

    /**
     * Returns an immutable list of all collected problems.
     *
     * @return an unmodifiable list of problems, never {@code null}
     * @see Problem
     */
    @NotNull
    public List<Problem> all() {
        return List.copyOf(this.problems);
    }

    /**
     * Defines the severity of a configuration problem.
     */
    public enum Severity {
        /**
         * Indicates a non-critical issue that does not prevent execution.
         */
        WARNING,

        /**
         * Indicates a critical issue that typically halts execution.
         */
        ERROR
    }

    /**
     * Represents a single configuration problem entry.
     *
     * <p>Each problem includes a {@link Severity}, the affected configuration
     * {@link #path path}, and a descriptive {@link #message message}.</p>
     *
     * @param severity the severity of the problem
     * @param path     the configuration path where the problem occurred
     * @param message  a descriptive message explaining the problem
     */
    public record Problem(Severity severity, String path, String message) {

        /**
         * Returns a string representation of this problem for diagnostics.
         *
         * @return a string containing the severity, path, and message
         */
        @NotNull
        @Override
        public String toString() {
            return "[" + this.severity + "] " + this.path + " - " + this.message;
        }
    }
}
