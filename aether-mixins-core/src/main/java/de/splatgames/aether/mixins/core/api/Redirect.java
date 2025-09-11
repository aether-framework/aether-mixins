package de.splatgames.aether.mixins.core.api;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Declares a hook method that <em>redirects</em> a specific method invocation inside a target method
 * to the annotated method.
 *
 * <p>Methods annotated with {@link Redirect} are collected from classes marked with
 * {@link de.splatgames.aether.mixins.core.api.Mixin @Mixin}. During weaving, the runtime scans the
 * {@linkplain #method() target method} for an invocation that matches {@link #callOwner()},
 * {@link #callName()}, {@link #callDesc()} (and optionally {@link #kind()}/{@link #ordinal()}), and
 * replaces that invocation with a call to the annotated redirect method.</p>
 *
 * <h2>Usage</h2>
 * <blockquote><pre>{@code
 * @Mixin(targets = "com.example.Service", priority = 100)
 * public final class ServiceMixin {
 *
 *   // Redirect the call 'Util.calc(int) -> int' inside Service.process(String)
 *   @Redirect(
 *       method     = "process(Ljava/lang/String;)V",
 *       callOwner  = "com/example/Util",
 *       callName   = "calc",
 *       callDesc   = "(I)I",
 *       kind       = Redirect.InvokeKind.INVOKESTATIC
 *   )
 *   public static int redirectCalc(int in) {
 *     // sanitize negative values
 *     return Math.max(0, in);
 *   }
 * }
 * }</pre></blockquote>
 *
 * <p><strong>Identification of the call site:</strong>
 * <ul>
 *   <li>{@link #callOwner()} must be the internal JVM name with slashes (e.g. {@code com/example/Util}).</li>
 *   <li>{@link #callName()} is the simple method name (e.g. {@code calc}).</li>
 *   <li>{@link #callDesc()} is the JVM descriptor (e.g. {@code (I)I}, {@code (Ljava/lang/String;)V}).</li>
 *   <li>{@link #kind()} restricts matching to a specific invoke opcode (recommended for determinism).</li>
 *   <li>{@link #ordinal()} selects the nth occurrence of that invoke inside the target method (0-based).
 *       If left at {@code -1}, the backend may apply a best-effort strategy (e.g., the first match).</li>
 * </ul>
 * </p>
 *
 * <h3>Signature of the redirect method</h3>
 * <p>The annotated method must be compatible with the replaced invocation:
 * its parameters must represent the call’s receiver (for {@code INVOKEVIRTUAL}/{@code INVOKEINTERFACE}/{@code INVOKESPECIAL})
 * followed by the original invocation arguments, and its return type must be assignable to the original call’s return type.</p>
 *
 * <p>Examples:</p>
 * <ul>
 *   <li>Redirecting {@code INVOKESTATIC Util.calc(int):int} → redirect method signature: {@code (int)int}</li>
 *   <li>Redirecting {@code INVOKEVIRTUAL Repo.save(Model):void} → redirect method signature:
 *       {@code (Repo, Model)void} (receiver first, then arguments)</li>
 * </ul>
 *
 * <h3>Ordering &amp; priority</h3>
 * <p>When multiple redirects target the same call site, the declaring mixin’s
 * {@link de.splatgames.aether.mixins.core.api.Mixin#priority() priority} determines application order.
 * The runtime must choose a deterministic ordering; higher priority generally applies later in the pipeline.</p>
 *
 * <h2>Compatibility and safety</h2>
 * <ul>
 *   <li>If {@link #optional()} is {@code true}, a missing call site does not fail startup in safe mode; the redirect is skipped.</li>
 *   <li>If {@link #remap()} is {@code true}, the runtime may resolve owner/name/desc via a configured mapping in obfuscated environments.</li>
 *   <li>Weaving should recompute stack map frames and verify the transformed class.</li>
 * </ul>
 *
 * <h2>Examples</h2>
 * <p><strong>Redirect the first call to Repo.save(..) within persist(..):</strong></p>
 * <blockquote><pre>{@code
 * @Redirect(
 *     method    = "persist(Lcom/example/Model;)V",
 *     callOwner = "com/example/Repo",
 *     callName  = "save",
 *     callDesc  = "(Lcom/example/Model;)V",
 *     kind      = Redirect.InvokeKind.INVOKEVIRTUAL,
 *     ordinal   = 0,
 *     id        = "persist-save-redirect"
 * )
 * public static void redirectSave(com.example.Repo repo, com.example.Model model) {
 *   if (model.getId() == null) {
 *     // pre-persist logic...
 *   }
 *   repo.save(model);
 * }
 * }</pre></blockquote>
 *
 * @author Erik Pförtner
 * @apiNote The MVP targets direct method call redirection. Future versions may add support for
 * more fine-grained selection (instruction slicing), constructor/new-call pairs, and conditional/cancellable redirects.
 * @implSpec Backends must match on owner/name/descriptor (and kind/ordinal if provided) and replace only that instruction.
 * If multiple matches exist and {@link #ordinal()} is negative, the backend may choose the first match but must do so
 * deterministically. If weaving fails, safe-mode runtimes should leave the class unmodified and emit diagnostics.
 * @since 0.1.0
 */
@Documented
@Retention(RUNTIME)
@Target(METHOD)
public @interface Redirect {

    /**
     * Target method identified by its simple name and JVM descriptor in the form {@code name + descriptor}.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>{@code "process(Ljava/lang/String;)V"}</li>
     *   <li>{@code "doWork(I)I"}</li>
     *   <li>{@code "<init>(Ljava/lang/String;I)V"} (constructor)</li>
     * </ul>
     *
     * <p>The owner class is provided by the enclosing {@link Mixin @Mixin} declaration.</p>
     *
     * @return the target method signature
     */
    String method();

    /**
     * Internal JVM name (slash-separated) of the owner declaring the invoked method to be redirected.
     *
     * <p>Example: {@code "com/example/Util"}</p>
     *
     * @return the invocation owner in internal form
     */
    String callOwner();

    /**
     * Simple name of the invoked method to be redirected.
     *
     * <p>Example: {@code "calc"}</p>
     *
     * @return the invocation method name
     */
    String callName();

    /**
     * JVM descriptor of the invoked method to be redirected.
     *
     * <p>Examples: {@code "(I)I"}, {@code "(Lcom/example/Model;)V"}</p>
     *
     * @return the invocation method descriptor
     */
    String callDesc();

    /**
     * Invoke opcode kind to match. Specifying the kind improves determinism.
     *
     * @return the invoke kind used at the call site
     */
    InvokeKind kind() default InvokeKind.AUTO;

    /**
     * Selects the nth matching call within the target method (0-based).
     *
     * <p>If {@code -1}, the backend may select the first match. Using explicit ordinals is recommended
     * when multiple occurrences exist.</p>
     *
     * @return the ordinal of the call to redirect, or {@code -1} for the first match
     */
    int ordinal() default -1;

    /**
     * Optional developer-defined identifier for diagnostics and selective enablement.
     *
     * @return a free-form identifier
     */
    String id() default "";

    /**
     * Whether the redirect should be skipped gracefully when the call site cannot be found.
     *
     * <p>Effective in safe-mode runtimes. When {@code false}, missing call sites may be treated as errors
     * depending on configuration.</p>
     *
     * @return {@code true} to tolerate missing call sites
     */
    boolean optional() default false;

    /**
     * Hint that owner/name/desc may require remapping in obfuscated environments.
     *
     * <p>This flag does not perform remapping by itself; runtimes with mapping support may translate
     * the symbolic invocation into its runtime form.</p>
     *
     * @return {@code true} if remapping should be attempted
     */
    boolean remap() default true;

    /**
     * The invoke opcode kinds this redirect may target.
     */
    enum InvokeKind {
        /**
         * Let the backend infer the invoke kind from the target; may be ambiguous if multiple matches exist.
         */
        AUTO,

        /**
         * {@code INVOKESTATIC} — static method call.
         */
        INVOKESTATIC,

        /**
         * {@code INVOKEVIRTUAL} — virtual instance method call.
         */
        INVOKEVIRTUAL,

        /**
         * {@code INVOKESPECIAL} — special invocation (constructors, private methods, super calls).
         */
        INVOKESPECIAL,

        /**
         * {@code INVOKEINTERFACE} — interface method call.
         */
        INVOKEINTERFACE
        // Note: INVOKEDYNAMIC intentionally not supported in MVP.
    }
}
