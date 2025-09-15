package de.splatgames.aether.mixins.core.api;

/**
 * Marks a shadowed field as explicitly allowed to weaken or override {@code final} semantics.
 *
 * <p>This annotation enables controlled mutation of target fields that are declared {@code final}.
 * Normally, writing to a {@code final} field is strictly prohibited to preserve the immutability
 * and safety guarantees of the original class and the Java Memory Model (JMM). By applying
 * {@code @Mutable}, developers can opt-in to a special weaving mode that removes this restriction,
 * but <strong>only</strong> when combined with an explicit enablement in the
 * <em>Mixin Runtime Sections</em>.</p>
 *
 * <h2>Purpose</h2>
 * <ul>
 *   <li>Allows fixing defects or working around limitations in third-party libraries where necessary fields
 *       were declared {@code final} and cannot be modified through normal means.</li>
 *   <li>Provides a formal, auditable mechanism to break finality rather than relying on unsafe reflection
 *       or other hacks that may be JVM-dependent and undefined.</li>
 *   <li>Prevents accidental mutation of immutable state by requiring both an annotation and a runtime-section
 *       switch before the weaving backend allows modification.</li>
 * </ul>
 *
 * <h2>Activation requirements</h2>
 * <p>The presence of {@code @Mutable} alone is not sufficient to enable writing to a final target field.</p>
 * <ol>
 *   <li>The shadowed field in the target class must be declared {@code final} and non-constant
 *       (i.e., it must not have a {@code ConstantValue} attribute such as compile-time constant primitives or strings).</li>
 *   <li>The mixin shadow must explicitly declare {@code @Mutable} on the field with a human-readable
 *       {@link #reason()} describing why this dangerous action is required.</li>
 *   <li>The corresponding capability must be <strong>enabled in the Mixin Runtime Sections</strong>
 *       (via runtime.allow_final_field_weakening). If the runtime section
 *       does not enable this capability for the active environment/profile, mutation remains disabled.</li>
 * </ol>
 *
 * <h2>Runtime behavior</h2>
 * <ul>
 *   <li>When both {@code @Mutable} and the relevant runtime-section capability are active, the weaver may generate
 *       access bridges or use low-level mechanisms (e.g., {@code VarHandle}, {@code Unsafe}) to allow modification
 *       of the final field in controlled hook contexts.</li>
 *   <li>If either condition is missing, writing to the field results in a weave-time validation error and no mutation occurs.</li>
 *   <li>All successful mutations should be audited by the runtime (owner class, field name, descriptor, and {@link #reason()}).</li>
 * </ul>
 *
 * <h2>Risks and warnings</h2>
 * <p>Weakening {@code final} semantics is <strong>extremely unsafe</strong> and violates
 * core principles of object-oriented programming and the JVM:</p>
 * <ul>
 *   <li>Final fields may be treated as constants by the JIT compiler and inlined into optimized code paths,
 *       leading to inconsistent behavior after mutation.</li>
 *   <li>Breaking immutability can cause severe thread-safety and memory visibility issues.</li>
 *   <li>This feature should only be used in rare, well-documented edge cases, never as a general-purpose tool.</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <blockquote><pre>{@code
 * @Mixin(targets = "com.example.LegacyService")
 * public final class LegacyServiceMixin {
 *
 *     @Shadow @Mutable(reason = "Reset internal state after reload")
 *     private int shadow$state;
 *
 *     @Inject(method = "reload()V", at = Inject.At.TAIL)
 *     public static void onReload(* context args *) {
 *         // controlled write to shadow$state if enabled via Mixin Runtime Sections
 *     }
 * }
 * }</pre></blockquote>
 *
 *
 * <p><b>Implementation note:</b> The exact key/flag name is runtime-configurable and may be defined
 * per section/environment (e.g., dev/test/prod) to ensure dangerous capabilities remain disabled by default.</p>
 *
 * @author Erik Pförtner
 * @see Shadow
 * @since 0.2.0
 */
public @interface Mutable {

    /**
     * A human-readable explanation of why this field must be mutable.
     * <p>This reason is included in audit logs and error reports so that reviewers
     * and maintainers can understand the justification for breaking finality.</p>
     *
     * @return a short description of the motivation behind using {@code @Mutable}
     */
    String reason() default "";
}
