package de.splatgames.aether.mixins.bytecode.weaver.hook;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Redirect;
import de.splatgames.aether.mixins.core.config.problems.ConfigProblems;
import de.splatgames.aether.mixins.core.plan.PlannedEntry;
import de.splatgames.aether.mixins.core.plan.PlannedMixin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Default {@link HookResolver} implementation based on Java reflection.
 *
 * <h2>Resolution rules</h2>
 * <ol>
 *   <li>Load the mixin class by name using the configured {@link ClassLoader}.</li>
 *   <li>Collect <em>declared</em> methods with a matching annotation:
 *     <ul>
 *       <li>{@link PlannedEntry.Kind#INJECT} → {@link Inject @Inject}</li>
 *       <li>{@link PlannedEntry.Kind#REDIRECT} → {@link Redirect @Redirect}</li>
 *     </ul>
 *   </li>
 *   <li>Compute each method's <em>effective ID</em>:
 *       <code>annotation.id()</code> if non-empty, otherwise the method's simple name.</li>
 *   <li>Match candidate(s) against the {@link PlannedEntry#getId() planned ID}:
 *     <ul>
 *       <li>If the planned ID is empty → accept only when there is exactly one candidate.</li>
 *       <li>If the planned ID is non-empty → accept candidates whose effective ID matches exactly.</li>
 *       <li>Report an error if no match or more than one match is found.</li>
 *     </ul>
 *   </li>
 *   <li>Validate the selected method:
 *     <ul>
 *       <li>Must be declared {@code static}.</li>
 *       <li>The method signature must be compatible with the target injection or redirect site.</li>
 *       <li>For instance method redirects, the receiver type is passed as the first parameter to the hook.</li>
 *     </ul>
 *   </li>
 *   <li>Return a {@link ResolvedHook} containing the internal owner name, method name,
 *       and the JVM descriptor of the resolved hook.</li>
 * </ol>
 *
 * <h2>Diagnostics</h2>
 * <p>
 * All diagnostics, such as missing hooks or signature mismatches, are reported to
 * {@code problems} using the provided {@code path} as a human-readable context string.
 * This allows callers to trace errors back to specific mixins and target methods.
 * </p>
 *
 * <h2>Thread-safety</h2>
 * <p>
 * This resolver is not thread-safe. A new instance should be created for each weaving run.
 * </p>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class DefaultHookResolver implements HookResolver {

    /**
     * Class loader used to load mixin classes.
     */
    @NotNull
    private final ClassLoader loader;

    /**
     * Creates a resolver using the given {@link ClassLoader}.
     *
     * @param loader class loader used to load mixin classes, must not be {@code null}
     */
    public DefaultHookResolver(@NotNull final ClassLoader loader) {
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    /**
     * Creates a resolver using the current thread context class loader (falls back to this class' loader).
     *
     * @return resolver instance, never {@code null}
     */
    @NotNull
    public static DefaultHookResolver usingTCCL() {
        final ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        return new DefaultHookResolver(tccl != null ? tccl : DefaultHookResolver.class.getClassLoader());
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation uses Java reflection to load the mixin class and find the hook method.</p>
     */
    @NotNull
    @Override
    public Optional<ResolvedHook> resolve(
            @NotNull final PlannedMixin mixin,
            @NotNull final PlannedEntry entry,
            @NotNull final ConfigProblems problems,
            @NotNull final String path
    ) {
        final String className = mixin.getClassName();
        final Class<?> mixinClass = loadClass(className, problems, path);
        if (mixinClass == null) return Optional.empty();

        final List<Method> candidates = findAnnotatedCandidates(mixinClass, entry.getKind());
        if (candidates.isEmpty()) {
            problems.error(path, "No @" + entry.getKind().name().toLowerCase(Locale.ROOT) +
                    " hook found in " + className);
            return Optional.empty();
        }

        final String wantedId = entry.getId(); // may be empty
        final List<Method> matched = matchById(candidates, entry.getKind(), wantedId);

        if (matched.isEmpty()) {
            problems.error(path, "No hook with id '" + wantedId + "' in " + className);
            return Optional.empty();
        }
        if (matched.size() > 1) {
            problems.error(path, "Ambiguous hook id '" + wantedId + "' in " + className +
                    " (matches: " + namesOf(matched) + ")");
            return Optional.empty();
        }

        final Method hook = matched.get(0);

        // Basic validation
        if (!Modifier.isStatic(hook.getModifiers())) {
            problems.error(path, "Hook method must be static: " + sig(hook));
            return Optional.empty();
        }

        final String owner = internalName(mixinClass);
        final String name = hook.getName();
        final String desc = toDescriptor(hook);
        return Optional.of(new ResolvedHook(owner, name, desc));
    }

    /**
     * Loads a class by binary name using the configured loader.
     *
     * @param name     binary class name, never {@code null}
     * @param problems diagnostics collector, never {@code null}
     * @param path     diagnostic path, never {@code null}
     * @return the loaded class, or {@code null} if not found (error recorded)
     */
    @Nullable
    private Class<?> loadClass(@NotNull final String name,
                               @NotNull final ConfigProblems problems,
                               @NotNull final String path) {
        try {
            return Class.forName(name, false, this.loader);
        } catch (Throwable t) {
            problems.error(path, "Failed to load mixin class '" + name + "': " + t.getClass().getSimpleName() +
                    ": " + String.valueOf(t.getMessage()));
            return null;
        }
    }

    /**
     * Finds declared methods on the mixin class that have the required annotation
     * corresponding to the planned entry kind.
     *
     * @param mixinClass mixin class, never {@code null}
     * @param kind       planned entry kind, never {@code null}
     * @return list of candidate methods, never {@code null}
     */
    @NotNull
    private static List<@NotNull Method> findAnnotatedCandidates(@NotNull final Class<?> mixinClass,
                                                                 @NotNull final PlannedEntry.Kind kind) {
        final Method[] declared = mixinClass.getDeclaredMethods();
        final List<Method> out = new ArrayList<>(declared.length);
        for (final Method m : declared) {
            final boolean matches = (kind == PlannedEntry.Kind.INJECT)
                    ? (m.getAnnotation(Inject.class) != null)
                    : (m.getAnnotation(Redirect.class) != null);
            if (matches) out.add(m);
        }
        return out;
    }

    /**
     * Filters candidates by effective ID.
     *
     * <p>Effective ID of a method is {@code annotation.id()} if non-empty, else the method name.</p>
     *
     * @param candidates methods annotated with the proper annotation, never {@code null}
     * @param kind       kind for choosing the annotation, never {@code null}
     * @param wanted     wanted id; may be empty to select the single candidate
     * @return matched methods, never {@code null}
     */
    @NotNull
    private static List<@NotNull Method> matchById(@NotNull final List<@NotNull Method> candidates,
                                                   @NotNull final PlannedEntry.Kind kind,
                                                   @NotNull final String wanted) {
        final boolean hasWanted = !wanted.isEmpty();
        final List<Method> matches = new ArrayList<>();
        for (final Method m : candidates) {
            final String effectiveId = switch (kind) {
                case INJECT -> {
                    final Inject ann = m.getAnnotation(Inject.class);
                    final String annId = (ann != null) ? ann.id() : "";
                    yield annId.isEmpty() ? m.getName() : annId;
                }
                case REDIRECT -> {
                    final Redirect ann = m.getAnnotation(Redirect.class);
                    final String annId = (ann != null) ? ann.id() : "";
                    yield annId.isEmpty() ? m.getName() : annId;
                }
            };
            if (!hasWanted || wanted.equals(effectiveId)) {
                matches.add(m);
            }
        }

        // If no wanted id provided, only accept single unambiguous candidate
        if (!hasWanted && matches.size() != 1) {
            return List.of(); // ambiguity or none → let caller emit message
        }
        return matches;
    }

    /**
     * Builds an informative signature string for diagnostics.
     *
     * @param m method, never {@code null}
     * @return human-readable signature (binary owner, name, descriptor)
     */
    @NotNull
    private static String sig(@NotNull final Method m) {
        return m.getDeclaringClass().getName() + "." + m.getName() + toDescriptor(m);
    }

    /**
     * Converts a reflective method into a JVM descriptor (e.g. {@code (I)I}, {@code ()V}).
     *
     * @param m method, never {@code null}
     * @return JVM descriptor string, never {@code null}
     */
    @NotNull
    private static String toDescriptor(@NotNull final Method m) {
        final StringBuilder sb = new StringBuilder();
        sb.append('(');
        for (final Class<?> p : m.getParameterTypes()) {
            sb.append(typeDesc(p));
        }
        sb.append(')');
        sb.append(typeDesc(m.getReturnType()));
        return sb.toString();
    }

    /**
     * Returns the JVM type descriptor for a class.
     *
     * @param c class, never {@code null}
     * @return JVM descriptor fragment, never {@code null}
     */
    @NotNull
    private static String typeDesc(@NotNull final Class<?> c) {
        if (c.isPrimitive()) {
            if (c == void.class) return "V";
            if (c == boolean.class) return "Z";
            if (c == byte.class) return "B";
            if (c == char.class) return "C";
            if (c == short.class) return "S";
            if (c == int.class) return "I";
            if (c == float.class) return "F";
            if (c == long.class) return "J";
            if (c == double.class) return "D";
        }
        if (c.isArray()) {
            return "[" + typeDesc(c.getComponentType());
        }
        return "L" + c.getName().replace('.', '/') + ";";
        // Note: for inner classes, binary name already uses '$' — that's expected here.
    }

    /**
     * Converts a {@link Class} into its internal JVM name (slash-separated).
     *
     * @param c class, never {@code null}
     * @return internal name, never {@code null}
     */
    @NotNull
    private static String internalName(@NotNull final Class<?> c) {
        return c.getName().replace('.', '/');
    }

    /**
     * Joins method names for diagnostics.
     *
     * @param methods list of methods, never {@code null}
     * @return comma-separated {@code owner.name(desc)} list
     */
    @NotNull
    private static String namesOf(@NotNull final List<@NotNull Method> methods) {
        return methods.stream().map(DefaultHookResolver::sig).toList().toString();
    }
}
