package de.splatgames.aether.mixins.core.api;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Declares a symbolic reference to an existing field or method in a target class.
 *
 * <p>{@code @Shadow} members are <em>not</em> new fields or methods added to the target class.
 * Instead, they act as placeholders or symbolic links that represent members that already exist
 * in the target class. At compile-time, this allows developers to write mixin code that
 * references those members directly, while at runtime the weaving system ensures that
 * the declared shadow matches the real target member.</p>
 *
 * <h2>Purpose</h2>
 * <ul>
 *   <li><strong>Type-safe access:</strong> Enables compile-time access to existing target members
 *       without hardcoding obfuscated or unstable names in the mixin code.</li>
 *   <li><strong>Name collision avoidance:</strong> Allows the use of prefixes to avoid naming conflicts
 *       between mixin-local methods/fields and shadow members.</li>
 *   <li><strong>Optional references:</strong> Supports optional target members that may or may not exist
 *       at runtime, with safe fallback handling if they are absent.</li>
 *   <li><strong>Validation:</strong> Ensures at weave-time that the target members actually exist and match
 *       the expected descriptor and access type.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <p>Below is an example of a mixin using {@code @Shadow} to declare references to both a static field
 * and an instance method in its target class:</p>
 *
 * <blockquote><pre>{@code
 * @Mixin(targets = "com.example.Service")
 * public final class ServiceMixin {
 *
 *   // Shadows a static constant field MY_CONSTANT in the target class
 *   @Shadow
 *   private static String shadow$MY_CONSTANT;
 *
 *   // Shadows an instance method 'performTask(int)' in the target class
 *   @Shadow
 *   private void shadow$performTask(int value);
 * }
 * }</pre></blockquote>
 *
 * <p>During weaving, the runtime validates that:</p>
 * <ul>
 *   <li>The target class contains a field named {@code MY_CONSTANT} of type {@code java.lang.String}.</li>
 *   <li>The target class contains a method named {@code performTask} with descriptor {@code (I)V}.</li>
 * </ul>
 *
 * <h2>Name resolution and prefixes</h2>
 * <p>The {@link #prefix()} attribute is used to distinguish shadow members from regular mixin members,
 * and to automatically strip this prefix when matching the target member name. This mechanism helps
 * prevent naming conflicts and makes shadows clearly identifiable.</p>
 *
 * <p>Here is how prefix behavior works:</p>
 * <blockquote><pre>{@code
 * // Will match a method "performTask" by stripping "shadow$"
 * @Shadow(prefix = "shadow$")
 * private abstract void shadow$performTask(int value);
 *
 * // Equivalent to the above, since "shadow$" is the default prefix
 * @Shadow
 * private abstract void shadow$performTask(int value);
 *
 * // Custom prefix "doit_" - matches "doWork" in the target class
 * @Shadow(prefix = "doit_")
 * private abstract void doit_doWork(int value);
 *
 * // If there is no prefix, the shadow name must exactly match the target name
 * @Shadow(prefix = "")
 * private abstract void execute(int value); // Targets a method named exactly "execute"
 * }</pre></blockquote>
 *
 * <h3>Important rules for prefixes</h3>
 * <ul>
 *   <li>The prefix must appear exactly at the start of the mixin member name.</li>
 *   <li>When stripped, the remaining name must be a valid identifier of an existing target member.</li>
 *   <li>If no prefix is provided (empty string), the full shadow name must match the target exactly.</li>
 * </ul>
 *
 * <h2>Remapping and environments</h2>
 * <p>The {@link #remap()} flag controls whether names and descriptors in the shadow
 * should be translated through a mapping system.</p>
 *
 * <ul>
 *   <li>Defaults to {@code false} because Aether Mixins targets stable enterprise code where
 *       symbols do not change between build and runtime.</li>
 *   <li>When set to {@code true}, the runtime may remap names using a configured mapping source
 *       (e.g., for environments with obfuscation or different runtime naming conventions).</li>
 *   <li>In 0.2.x this flag is a no-op placeholder for future compatibility with obfuscation mappings.</li>
 * </ul>
 *
 * <h2>Optional behavior</h2>
 * <p>By default, all shadows are considered <em>required</em>.
 * If a target member does not exist, the weaver throws a hard error and weaving is aborted.</p>
 *
 * <p>If {@link #optional()} is set to {@code true}:</p>
 * <ul>
 *   <li>Missing target members are tolerated and will not cause the runtime to fail.</li>
 *   <li>The shadow reference is simply bound to {@code null}.</li>
 *   <li>Mixin code can then check for {@code null} at runtime before using the shadow.</li>
 *   <li>This is especially useful for optional integrations or cross-version compatibility.</li>
 * </ul>
 *
 * <h2>Constraints and limitations</h2>
 * <ul>
 *   <li>{@code @Shadow} can only be applied to <b>fields</b> and <b>methods</b>.</li>
 *   <li>Static shadows may reference <b>static</b> members of the target class and can be used from static hooks.</li>
 *   <li>Instance shadows are supported when the weaving backend provides an instance context
 *       (e.g., merged hooks) or generates access bridges. Availability and accessibility depend
 *       on runtime configuration and target visibility; if neither facility is active, instance
 *       shadows are validated but not directly usable from static hooks.</li>
 *   <li>Shadow members must not contain real implementation code:
 *     <ul>
 *       <li>Methods must be empty (any bodies are ignored by the runtime).</li>
 *       <li>Field initializers are ignored.</li>
 *     </ul>
 *   </li>
 *   <li><b>Finality rules:</b>
 *     <ul>
 *       <li>If both the shadow and the target field are {@code final}, the field is treated as <em>read-only</em>.
 *           The shadow may only read the value, never write to it.</li>
 *       <li>If the shadow is non-final but the target field is {@code final}, writing to it is strictly forbidden
 *           and validated at weave-time. This preserves the original immutability of the target class.</li>
 *       <li>If the shadow is {@code final} but the target field is not, this is allowed, but effectively creates
 *           a read-only view. A warning may be logged to indicate that the shadow is stricter than the target.</li>
 *       <li>Writing to a {@code final} target field is only possible with explicit opt-in mechanisms such as
 *           {@code @Mutable} combined with a runtime configuration flag. This is extremely unsafe and should
 *           be reserved for rare edge-cases.</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h2>Summary of attribute behavior</h2>
 * <table border="1" cellspacing="0" cellpadding="3">
 *   <tr>
 *     <th>Attribute</th>
 *     <th>Default</th>
 *     <th>Purpose</th>
 *   </tr>
 *   <tr>
 *     <td>{@link #prefix()}</td>
 *     <td>{@code "shadow$"}</td>
 *     <td>Stripped from the shadow name before matching with the target member name.</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #remap()}</td>
 *     <td>{@code false}</td>
 *     <td>Indicates whether names and descriptors should be remapped via a mapping source.</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #optional()}</td>
 *     <td>{@code false}</td>
 *     <td>Controls whether a missing target member causes a hard error or is tolerated.</td>
 *   </tr>
 * </table>
 *
 * @author Erik Pförtner
 * @see Mixin
 * @see Inject
 * @see Redirect
 * @since 0.2.0
 */
@Documented
@Retention(RUNTIME)
@Target({METHOD, FIELD})
public @interface Shadow {

    /**
     * A prefix that is stripped from the shadow name when matching against target members.
     *
     * <p>This helps prevent naming conflicts within the mixin class and makes it clear
     * which members are shadows. The default is {@code "shadow$"}.</p>
     *
     * <p>Here are examples of valid prefix usage for both fields and methods:</p>
     * <blockquote><pre>{@code
     * // === Method examples ===
     *
     * // Matches target method "performTask"
     * @Shadow(prefix = "shadow$")
     * private abstract void shadow$performTask(int value);
     *
     * // Same as above, since "shadow$" is the default
     * @Shadow
     * private abstract void shadow$performTask(int value);
     *
     * // Custom prefix "doit_" matches target method "doWork"
     * @Shadow(prefix = "doit_")
     * private abstract void doit_doWork(int value);
     *
     * // No prefix - name must exactly match target member
     * @Shadow(prefix = "")
     * private abstract void execute(int value); // Matches "execute"
     *
     *
     * // === Field examples ===
     *
     * // Matches target field "MY_CONSTANT"
     * @Shadow(prefix = "shadow$")
     * private static String shadow$MY_CONSTANT;
     *
     * // Same as above, since "shadow$" is the default
     * @Shadow
     * private static String shadow$MY_CONSTANT;
     *
     * // Custom prefix "field_" matches target field "counter"
     * @Shadow(prefix = "field_")
     * private int field_counter;
     *
     * // No prefix - name must exactly match target field
     * @Shadow(prefix = "")
     * private int health; // Matches "health"
     * }</pre></blockquote>
     *
     * @return the prefix to strip when resolving shadow names
     */
    String prefix() default "shadow$";

    /**
     * Indicates whether symbolic names and descriptors should be remapped through a configured mapping source.
     *
     * <p>Defaults to {@code false}, meaning names are used as-is. When enabled, the runtime may translate
     * these names for environments where code is obfuscated or runtime symbols differ from source-level names.</p>
     *
     * <p>In 0.2.x this flag is reserved for future mapping support and does not yet trigger remapping.</p>
     *
     * @return {@code true} to enable remapping, {@code false} to use raw names
     */
    boolean remap() default false;

    /**
     * Controls whether the shadow target is required or optional.
     *
     * <p>Defaults to {@code false}, meaning the target member must exist. If the target cannot be found,
     * the runtime fails weaving for this mixin.</p>
     *
     * <p>When set to {@code true}, the runtime tolerates missing target members:
     * <ul>
     *   <li>The shadow is simply bound to {@code null}.</li>
     *   <li>No errors are thrown during weaving.</li>
     *   <li>The mixin code must check for {@code null} before using the shadow reference.</li>
     * </ul>
     * </p>
     *
     * <p>This is useful for optional integrations, multi-version support, or where target classes
     * may differ between runtime environments.</p>
     *
     * @return {@code true} to allow missing members, {@code false} to require them
     */
    boolean optional() default false;
}
