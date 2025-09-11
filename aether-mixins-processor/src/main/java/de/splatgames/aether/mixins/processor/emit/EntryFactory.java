package de.splatgames.aether.mixins.processor.emit;

import com.google.gson.JsonObject;
import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Redirect;
import de.splatgames.aether.mixins.processor.model.CollectedEntry;
import de.splatgames.aether.mixins.processor.util.AnnotationUtil;
import de.splatgames.aether.mixins.processor.util.DescriptorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Factory for creating {@link CollectedEntry} instances (JSON-backed) for refmap emission.
 *
 * <p>This utility produces entry objects for both {@code @Inject} and {@code @Redirect} hooks
 * discovered by the annotation processor. Each entry conforms to refmap <b>schema 1</b> and
 * contains the fields needed by the runtime (e.g., method id, target method, and hook metadata).</p>
 *
 * <h2>Identifier strategy</h2>
 * <p>The {@code id} of a hook entry is computed as {@code name + descriptor} of the hook method
 * (for example, {@code onHead()V}). This guarantees stability and uniqueness within a single
 * mixin class and allows the runtime resolver to select the correct hook deterministically.</p>
 *
 * <h2>Descriptor calculation</h2>
 * <p>JVM method and type descriptors are derived using {@link DescriptorUtil}, which relies on
 * {@link Elements} and {@link Types} from the annotation processing environment.</p>
 *
 * <h2>Optional attributes</h2>
 * <p>Flags like {@code optional} and {@code remap} are read leniently via
 * {@link AnnotationUtil}. If the attribute is not present on the annotation (e.g., older
 * versions), it is omitted from the JSON instead of being defaulted.</p>
 *
 * <h2>Thread-safety</h2>
 * <p>The class is stateless and thread-safe. All methods are pure functions of their inputs.</p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * CollectedEntry e1 = EntryFactory.injectEntry(hookMethod, injectAnn, elements, types);
 * CollectedEntry e2 = EntryFactory.redirectEntry(hookMethod, redirectAnn, elements, types);
 * }</pre>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class EntryFactory {
    /**
     * Utility class should not be instantiated.
     */
    private EntryFactory() {
        // utility class, prevent instantiation
    }

    /**
     * Builds a refmap entry (schema 1) for a {@code @Inject}-annotated hook method.
     *
     * <p>The resulting JSON has the following fields:</p>
     * <ul>
     *   <li>{@code type}: {@code "inject"}</li>
     *   <li>{@code id}: hook identifier ({@code name + descriptor}, e.g., {@code onHead()V})</li>
     *   <li>{@code method}: target method signature from {@link Inject#method()}</li>
     *   <li>{@code at}: insertion point from {@link Inject#at()}</li>
     *   <li>{@code optional} (optional): included only if present on the annotation</li>
     *   <li>{@code remap} (optional): included only if present on the annotation</li>
     * </ul>
     *
     * @param hook     the hook method element annotated with {@link Inject}, must not be {@code null}
     * @param inj      the {@link Inject} annotation instance, must not be {@code null}
     * @param elements element utilities used to derive JVM descriptors, must not be {@code null}
     * @param types    type utilities used to derive JVM descriptors, must not be {@code null}
     * @return a {@link CollectedEntry} wrapping the generated JSON, never {@code null}
     */
    @NotNull
    public static CollectedEntry injectEntry(@NotNull final ExecutableElement hook,
                                             @NotNull final Inject inj,
                                             @NotNull final Elements elements,
                                             @NotNull final Types types) {
        final String hookName = hook.getSimpleName().toString();
        final String hookDesc = DescriptorUtil.methodDescriptor(hook, elements, types);
        final String id = hookName + hookDesc;

        final JsonObject e = new JsonObject();
        e.addProperty("type", "inject");
        e.addProperty("id", id);
        e.addProperty("method", inj.method());
        e.addProperty("at", inj.at().name());
        tryAddBoolean(e, "optional", AnnotationUtil.readOptional(hook, Inject.class));
        tryAddBoolean(e, "remap", AnnotationUtil.readRemap(hook, Inject.class));
        return new CollectedEntry(e);
    }

    /**
     * Builds a refmap entry (schema 1) for a {@code @Redirect}-annotated hook method.
     *
     * <p>The resulting JSON has the following fields:</p>
     * <ul>
     *   <li>{@code type}: {@code "redirect"}</li>
     *   <li>{@code id}: hook identifier ({@code name + descriptor}, e.g., {@code onCall(Ljava/lang/String;)I})</li>
     *   <li>{@code method}: target method signature from {@link Redirect#method()}</li>
     *   <li>{@code callOwner}: internal owner name from {@link Redirect#callOwner()}</li>
     *   <li>{@code callName}: method name from {@link Redirect#callName()}</li>
     *   <li>{@code callDesc}: JVM descriptor from {@link Redirect#callDesc()}</li>
     *   <li>{@code kind}: invoke kind from {@link Redirect#kind()}</li>
     *   <li>{@code ordinal}: 0-based occurrence selector from {@link Redirect#ordinal()}</li>
     *   <li>{@code optional} (optional): included only if present on the annotation</li>
     *   <li>{@code remap} (optional): included only if present on the annotation</li>
     * </ul>
     *
     * @param hook     the hook method element annotated with {@link Redirect}, must not be {@code null}
     * @param red      the {@link Redirect} annotation instance, must not be {@code null}
     * @param elements element utilities used to derive JVM descriptors, must not be {@code null}
     * @param types    type utilities used to derive JVM descriptors, must not be {@code null}
     * @return a {@link CollectedEntry} wrapping the generated JSON, never {@code null}
     */
    @NotNull
    public static CollectedEntry redirectEntry(@NotNull final ExecutableElement hook,
                                               @NotNull final Redirect red,
                                               @NotNull final Elements elements,
                                               @NotNull final Types types) {
        final String hookName = hook.getSimpleName().toString();
        final String hookDesc = DescriptorUtil.methodDescriptor(hook, elements, types);
        final String id = hookName + hookDesc;

        final JsonObject e = new JsonObject();
        e.addProperty("type", "redirect");
        e.addProperty("id", id);
        e.addProperty("method", red.method());
        e.addProperty("callOwner", red.callOwner());
        e.addProperty("callName", red.callName());
        e.addProperty("callDesc", red.callDesc());
        e.addProperty("kind", red.kind().name());
        e.addProperty("ordinal", red.ordinal());
        tryAddBoolean(e, "optional", AnnotationUtil.readOptional(hook, Redirect.class));
        tryAddBoolean(e, "remap", AnnotationUtil.readRemap(hook, Redirect.class));
        return new CollectedEntry(e);
    }

    /**
     * Adds a boolean property to a JSON object when the provided value is non-{@code null}.
     *
     * <p>This helper is used to include optional attributes only when they are explicitly
     * present on the annotation (backward-compatible behavior when attributes are absent).</p>
     *
     * @param o   JSON object to modify, must not be {@code null}
     * @param key property name to add, must not be {@code null}
     * @param val property value to add; if {@code null}, the property is omitted
     */
    private static void tryAddBoolean(@NotNull final JsonObject o,
                                      @NotNull final String key,
                                      @Nullable final Boolean val) {
        if (val != null) {
            o.addProperty(key, val);
        }
    }
}
