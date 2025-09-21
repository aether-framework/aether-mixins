package de.splatgames.aether.mixins.core.api;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Indicates that a member method or field declared in a mixin must <b>never</b>
 * overwrite a matching member in the target class. The member is only ever
 * <em>added</em>. For public fields, this annotation has no effect.
 * <p>
 * <b>Collision behaviour:</b>
 * </p>
 * <dl>
 *   <dt>public methods</dt>
 *   <dd>If a matching target method exists, the mixin method is <b>discarded</b>.
 *   A warning is logged unless {@link #silent()} is {@code true}.</dd>
 *
 *   <dt>private/protected methods</dt>
 *   <dd>If a matching target method exists, the mixin method is <b>renamed</b>
 *   (uniquified) so it can still be added without overwriting the target.</dd>
 * </dl>
 *
 * <p><b>Notes</b></p>
 * <ul>
 *   <li>Applying {@code @Unique} on the mixin <em>type</em> marks all methods unique.</li>
 *   <li>Uniqueness can also be derived per-interface via an interface binding facility.</li>
 * </ul>
 *
 * <h2>Examples</h2>
 * <h3>Public method (discard on conflict)</h3>
 * <blockquote><pre>
 * public class ExampleMixin {
 *     &#64;Unique
 *     public void helper() { /* ... *\/ }
 * }
 * </pre></blockquote>
 *
 * <h3>Non-public method (rename on conflict)</h3>
 * <blockquote><pre>
 * public class ExampleMixin {
 *     &#64;Unique
 *     private void computeInternal() { /* ... *\/ }
 * }
 * </pre></blockquote>
 *
 * <h3>Suppress warning on public discard</h3>
 * <blockquote><pre>
 * public class ExampleMixin {
 *     &#64;Unique(silent = true)
 *     public void helper() { /* ... *\/ }
 * }
 * </pre></blockquote>
 *
 * @author Erik Pförtner
 * @since 0.2.0
 */
@Documented
@Retention(RUNTIME)
@Target({ METHOD, FIELD, TYPE })
public @interface Unique {

    /**
     * Suppresses the warning that would be logged when a <b>public</b> method is
     * discarded due to a matching target method.
     *
     * @return {@code true} to suppress the warning when a public method is discarded
     */
    boolean silent() default false;
}
