package de.splatgames.aether.mixins.bytecode.weaver.asm;

import de.splatgames.aether.mixins.bytecode.weaver.asm.spec.InjectionSpec;
import de.splatgames.aether.mixins.bytecode.weaver.asm.spec.RedirectSpec;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates all weaving specifications for a single target class.
 *
 * <p>This container groups resolved injection and redirect specifications per
 * target <em>method signature</em> (concatenation of {@code name + descriptor}).
 * It is used by {@code AsmWeaver} to drive bytecode transformations for one class
 * at a time.</p>
 *
 * <h2>Structure</h2>
 * <ul>
 *   <li>{@link #getInternalName()} — internal JVM class name (slash separated).</li>
 *   <li>{@link #getInjects()} — map from method signature to list of {@link InjectionSpec}s.</li>
 *   <li>{@link #getRedirects()} — map from method signature to list of {@link RedirectSpec}s.</li>
 * </ul>
 *
 * <h2>Ordering</h2>
 * <p>The underlying maps are {@link LinkedHashMap}s to preserve insertion order.
 * The weaver may additionally sort the lists per method deterministically (e.g.,
 * by priority and id) before applying changes to guarantee stable output.</p>
 *
 * <h2>Mutability &amp; Thread-safety</h2>
 * <p>The returned maps are <em>live</em> and intended to be mutated by the weaver
 * during planning (e.g., {@code computeIfAbsent(...).add(...)}) prior to class
 * visitation. This class is not thread-safe and should be confined to a single
 * weaving pass.</p>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class ClassWork {

    /**
     * Internal JVM class name (slash-separated), e.g. {@code com/example/Foo}.
     */
    @NotNull
    private final String internalName;

    /**
     * Inject specs grouped by method signature ({@code name + desc}).
     */
    @NotNull
    private final Map<String, List<InjectionSpec>> injects = new LinkedHashMap<>();

    /**
     * Redirect specs grouped by method signature ({@code name + desc}).
     */
    @NotNull
    private final Map<String, List<RedirectSpec>> redirects = new LinkedHashMap<>();

    /**
     * Creates a new work container for a specific target class.
     *
     * @param internalName the internal JVM class name (slash-separated); must not be {@code null}
     */
    public ClassWork(@NotNull final String internalName) {
        this.internalName = internalName;
    }

    /**
     * Returns the internal JVM name (slash-separated) of the target class.
     *
     * @return non-null internal class name
     */
    @NotNull
    public String getInternalName() {
        return this.internalName;
    }

    /**
     * Returns the live map of injection specs grouped by method signature.
     *
     * <p>Keys are the method signature strings formed as {@code name + descriptor}
     * (e.g., {@code doWork(I)I}). Values are mutable lists of {@link InjectionSpec}s
     * associated with that method.</p>
     *
     * @return non-null, mutable map of injections by method signature
     */
    @NotNull
    public Map<String, List<InjectionSpec>> getInjects() {
        return this.injects;
    }

    /**
     * Returns the live map of redirect specs grouped by method signature.
     *
     * <p>Keys are the method signature strings formed as {@code name + descriptor}
     * (e.g., {@code doWork(I)I}). Values are mutable lists of {@link RedirectSpec}s
     * associated with that method.</p>
     *
     * @return non-null, mutable map of redirects by method signature
     */
    @NotNull
    public Map<String, List<RedirectSpec>> getRedirects() {
        return this.redirects;
    }
}
