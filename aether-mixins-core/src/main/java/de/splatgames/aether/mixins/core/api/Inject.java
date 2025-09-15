package de.splatgames.aether.mixins.core.api;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Declares a hook method to be injected into a target method at a specific join point.
 *
 * <p>Methods annotated with {@link Inject} are collected from classes marked with
 * {@link de.splatgames.aether.mixins.core.api.Mixin @Mixin} and woven into the bytecode of the
 * specified target method. An {@code @Inject} method is typically {@code static} and may receive a
 * backend-defined set of context parameters (e.g., {@code self}, original arguments, or a context object).</p>
 *
 * <h2>Usage</h2>
 * <blockquote><pre>{@code
 * @Mixin(targets = "com.example.Service")
 * public final class ServiceMixin {
 *
 *   // Run logic at the beginning of the method
 *   @Inject(method = "process(Ljava/lang/String;)V", at = Inject.At.HEAD)
 *   public static void onEnter(* context args *) {
 *     // e.g., log, metrics, guards
 *   }
 *
 *   // Run logic at the end of the method (after user code, before return)
 *   @Inject(method = "process(Ljava/lang/String;)V", at = Inject.At.TAIL)
 *   public static void onExit(* context args *) {
 *     // e.g., finalize, tracing
 *   }
 * }
 * }</pre></blockquote>
 *
 * <p><strong>Method identification:</strong> {@link #method()} must be given as a JVM signature combining the
 * simple method name and its descriptor, e.g. {@code "process(Ljava/lang/String;)V"}. For constructors, use
 * {@code "<init>(...)V"}. Descriptors follow the JVM format (e.g., {@code (I)I}, {@code (Ljava/lang/String;)V}).</p>
 *
 * <h3>Join points</h3>
 * <p>The {@link #at()} attribute selects a well-defined injection point.
 * The supported points are:
 * {@link Inject.At#HEAD HEAD} (first instruction) and {@link Inject.At#TAIL TAIL} (just before any return).</p>
 *
 * <h3>Ordering &amp; priority</h3>
 * <p>When multiple injections target the same join point of the same method, their overall order is determined by the
 * declaring mixin's {@link de.splatgames.aether.mixins.core.api.Mixin#priority() Mixin.priority()}. Higher priority
 * is applied later at HEAD (thus runs after lower-priority code) and earlier at TAIL (thus runs before lower-priority
 * code). Ordering is deterministic.</p>
 *
 * <h2>Compatibility and safety</h2>
 * <ul>
 *   <li>If {@link #optional()} is {@code true}, missing target methods do not fail startup in safe mode; the injection
 *       is skipped and a diagnostic entry is emitted.</li>
 *   <li>If {@link #remap()} is {@code true}, the runtime may resolve owner/name/desc via a configured mapping source
 *       when running in obfuscated environments.</li>
 * </ul>
 *
 * <h2>Examples</h2>
 * <p><strong>Inject at method head:</strong></p>
 * <blockquote><pre>{@code
 * @Inject(method = "doWork(I)I", at = Inject.At.HEAD, id = "enterDoWork")
 * public static void enterDoWork(* ctx *) { * ... * }
 * }</pre></blockquote>
 *
 * <p><strong>Inject at method tail:</strong></p>
 * <blockquote><pre>{@code
 * @Inject(method = "doWork(I)I", at = Inject.At.TAIL, id = "exitDoWork")
 * public static void exitDoWork(* ctx *) { * ... * }
 * }</pre></blockquote>
 *
 * @author Erik Pförtner
 * @apiNote Support for {@link At#HEAD} and {@link At#TAIL}. Future versions may introduce finer-grained points
 * (e.g., INVOKE, LINE, RETURN) and additional attributes for argument/variable capture and cancellation.
 * @implSpec Backends must guarantee bytecode verification (e.g., stack map frame recomputation) and adhere to the
 * deterministic ordering rules described above. If weaving fails, a safe-mode runtime should leave the class
 * unmodified and log diagnostics.
 * @since 0.1.0
 */
@Documented
@Retention(RUNTIME)
@Target(METHOD)
public @interface Inject {

    /**
     * Target method identified by its simple name and JVM descriptor.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>{@code "process(Ljava/lang/String;)V"}</li>
     *   <li>{@code "compute(II)I"}</li>
     *   <li>{@code "<init>(Ljava/lang/String;I)V"} (constructor)</li>
     * </ul>
     *
     * <p>The owner class is provided by the enclosing {@link Mixin @Mixin} declaration.</p>
     *
     * @return the target method signature in the form {@code name + descriptor}
     */
    String method();

    /**
     * Join point where the hook should be injected.
     *
     * <p>Currently supported values:</p>
     * <ul>
     *   <li>{@link At#HEAD} — before the first instruction of the target method.</li>
     *   <li>{@link At#TAIL} — just before any return instruction(s) of the target method.</li>
     * </ul>
     *
     * @return the injection join point
     */
    At at();

    /**
     * Optional developer-defined identifier for diagnostics and selective enablement.
     *
     * <p>When non-empty, this value may appear in logs and diagnostic dumps to help locate a particular injection.</p>
     *
     * @return a free-form identifier
     */
    String id() default "";

    /**
     * Whether the injection should be skipped gracefully when the target method cannot be found.
     *
     * <p>Effective in safe-mode runtimes. When {@code false}, missing targets may be treated as errors depending on
     * configuration.</p>
     *
     * @return {@code true} to tolerate missing targets, {@code false} otherwise
     */
    boolean optional() default false;

    /**
     * Hint to the runtime that symbolic names and descriptors may require remapping in obfuscated environments.
     *
     * <p>This flag does not perform remapping by itself; it allows runtimes with mapping support to translate
     * the symbolic method into its runtime form.</p>
     *
     * @return {@code true} if remapping should be attempted; {@code false} to disable remapping
     */
    boolean remap() default true;

    /**
     * Cancellation support for this injection point.
     *
     * <p>Callbacks marked {@code cancellable = true} may signal cancellation via a backend-defined mechanism
     * (e.g., a {@link CallbackInfo} or {@link CallbackInfoReturnable} parameter).
     * When a callback cancels, the original target method must abort
     * its execution as soon as possible.</p>
     *
     * @return {@code true} if the callback is allowed to cancel the target method; {@code false} otherwise
     */
    boolean cancellable() default false;

    /**
     * Well-known injection points.
     */
    enum At {
        /**
         * Inject at the very beginning of the target method body.
         */
        HEAD,

        /**
         * Inject just before any return instruction(s) of the target method.
         */
        TAIL
    }
}
