package de.splatgames.aether.mixins.core.api;

import org.jetbrains.annotations.ApiStatus;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Declares a class as an Aether <em>Mixin</em> and assigns it to one or more target classes.
 *
 * <p>A mixin is a regular Java class that contains hook methods annotated with
 * {@link de.splatgames.aether.mixins.core.api.Inject @Inject} or
 * {@link de.splatgames.aether.mixins.core.api.Redirect @Redirect}. During class loading or at runtime,
 * the Aether Mixins runtime weaves these hooks into the bytecode of the {@linkplain #targets() target} classes.</p>
 *
 * <h2>Usage</h2>
 * <blockquote><pre>{@code
 * @Mixin(
 *     targets = {"com.example.Service"},
 *     priority = 100
 * )
 * public final class ServiceMixin {
 *
 *   @Inject(method = "process(Ljava/lang/String;)V", at = Inject.At.HEAD)
 *   public static void onHead(*context args*) {
 *       // logic executed at the beginning of process(..)
 *   }
 *
 *   @Redirect(
 *       method    = "process(Ljava/lang/String;)V",
 *       callOwner = "com/example/Util",
 *       callName  = "calc",
 *       callDesc  = "(I)I"
 *   )
 *   public static int redirectCalc(int in) {
 *       return Math.max(0, in);
 *   }
 * }
 * }</pre></blockquote>
 *
 * <p><strong>Naming of target classes:</strong> Use the binary name (e.g. {@code com.example.Foo}) for classes and
 * JVM internal name with slashes only where explicitly documented for descriptors (e.g. {@code com/example/Foo} in
 * {@code callOwner}). Method descriptors must follow the JVM descriptor format (e.g. {@code (I)I}).</p>
 *
 * <h3>Weaving order and priorities</h3>
 * <p>When multiple mixins apply to the same method, the runtime uses {@link #priority()} to sort them.
 * A higher value means the mixin is applied later, i.e. it runs <em>after</em> lower-priority mixins at
 * {@link de.splatgames.aether.mixins.core.api.Inject.At#HEAD HEAD} and <em>before</em> them at
 * {@link de.splatgames.aether.mixins.core.api.Inject.At#TAIL TAIL}. The exact ordering is deterministic but may be
 * refined in future versions (e.g., by explicit dependency constraints).</p>
 *
 * <h3>Optional targets</h3>
 * <p>If {@link #optional()} is {@code true}, missing target classes do not fail startup in
 * {@code safe} mode; the mixin is simply skipped for that target and a diagnostic entry is emitted.</p>
 *
 * <h2>Constraints</h2>
 * <ul>
 *   <li>Mixin classes should be {@code final} and hook methods {@code static} unless documented otherwise.</li>
 *   <li>Hook methods must match the expected calling convention of the weaving backend (see {@link Inject} and {@link Redirect}).</li>
 *   <li>Mixin classes must be present on the application class path when the agent or in-app transformer runs.</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <p>While {@code @Mixin} declares compile-time intent, resolution of symbols typically happens via a
 * <em>refmap</em> provided in external configuration (e.g., YAML/JSON). See project documentation for the refmap
 * schema and runtime flags.</p>
 *
 * @author Erik Pförtner
 * @apiNote The set of attributes is intentionally small for the MVP. Future versions may add explicit dependency
 * declarations and conflict resolution policies. The semantics of {@link #priority()} are stable.
 * @since 0.1.0
 */
@Documented
@Retention(RUNTIME)
@Target(TYPE)
public @interface Mixin {

    /**
     * Fully-qualified binary names of classes this mixin should apply to.
     *
     * <p>Example: {@code "com.example.Service"}</p>
     *
     * <p>If multiple targets are specified, the same mixin class is considered for each target. The runtime may
     * skip individual targets if {@link #optional()} is {@code true} and the class is not found.</p>
     *
     * @return array of target class names (must not be empty)
     */
    String[] targets();

    /**
     * Controls the relative application order of mixins that affect the same join points.
     *
     * <p>Higher values indicate higher priority (applied later overall). The default is {@code 0}.
     * The valid range is not restricted by the annotation; consumers may impose limits.</p>
     *
     * <p>Example: to ensure this mixin runs after library defaults, choose a value like {@code 100}.</p>
     *
     * @return the priority value used for deterministic ordering
     */
    int priority() default 0;

    /**
     * When {@code true}, the runtime treats missing targets as non-fatal in safe mode and skips weaving for them.
     *
     * <p>This is useful for optional integrations (e.g., when a dependency might not be present at runtime).
     * Diagnostics will still be emitted to help with troubleshooting.</p>
     *
     * @return whether missing targets should be tolerated
     */
    boolean optional() default false;

    /**
     * If {@code true}, subtypes of the declared {@link #targets() targets} can be considered as additional weave
     * candidates where supported by the backend.
     *
     * <p><strong>Note:</strong> This is a best-effort feature. Exact behavior depends on class loading and
     * the weaving backend. If deterministic behavior is required, list all explicit targets instead.</p>
     *
     * @return whether subtypes of targets may also receive this mixin
     * @deprecated At the time of writing, no supported backend implements this feature reliably. Use explicit targets instead.
     */
    @Deprecated
    @ApiStatus.Experimental
    boolean applyToSubtypes() default false;

    /**
     * Optional free-form labels to group mixins (e.g., {@code "spring"}, {@code "debug"}).
     * Groups can be referenced by configuration to enable/disable sets of mixins.
     *
     * @return labels for grouping and selection
     * @implNote Group semantics are interpreted by the runtime configuration layer and do not affect bytecode generation
     * directly.
     */
    String[] groups() default {};

    /**
     * Declares external requirements (e.g., modules or artifacts) that should be present for this mixin to activate.
     *
     * <p>Convention examples:
     * <blockquote><pre>{@code
     * "org.springframework:spring-context:[6,)"
     * "com.example:feature-x:1.2+"
     * }</pre></blockquote>
     * The format is interpreted by the configuration layer. If a requirement is not satisfied, the mixin may be
     * disabled gracefully.</p>
     *
     * @return list of requirement coordinates
     */
    String[] requires() default {};

    /**
     * Declares known conflicts (by mixin class name or group label). When a conflict is detected at runtime,
     * the resolver may apply a deterministic policy (e.g., prefer higher {@link #priority()} or disable both).
     *
     * @return identifiers of conflicting mixins or groups
     */
    String[] conflictsWith() default {};

    /**
     * Indicates whether symbolic names in this mixin should be remapped via external mappings (e.g., obfuscated ↔ deobfuscated).
     *
     * <p>This flag does not perform any mapping by itself; it is a hint for runtimes that support remapping through
     * a configured mapping source. If remapping is unsupported or disabled, this flag is ignored.</p>
     *
     * @return {@code true} if the runtime should attempt to remap symbols used by this mixin
     */
    boolean remap() default true;
}
