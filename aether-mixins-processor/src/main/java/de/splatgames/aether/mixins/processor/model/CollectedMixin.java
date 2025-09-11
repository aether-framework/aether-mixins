package de.splatgames.aether.mixins.processor.model;

import org.jetbrains.annotations.NotNull;

import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.List;

/**
 * In-memory descriptor of a single {@code @Mixin}-annotated type discovered by the annotation processor.
 *
 * <p>This model aggregates the mixin's identity and metadata (targets, priority, tags) along with all
 * collected hook entries (see {@link CollectedEntry}) found on its methods. It is later serialized
 * into the refmap JSON ({@code mixins[*]}) by the processor.</p>
 *
 * <h2>Field semantics</h2>
 * <ul>
 *   <li><b>{@link #type}</b> – the compiler model element for the declaring mixin class.</li>
 *   <li><b>{@link #className}</b> – the mixin's <em>binary</em> name (dot-separated; {@code $} for nested),
 *       e.g. {@code com.example.Outer$Inner}.</li>
 *   <li><b>{@link #targets}</b> – list of target class <em>binary</em> names (dot-separated) taken from
 *       {@code @Mixin.targets()}.</li>
 *   <li><b>{@link #priority}</b> – numeric mixin priority captured from {@code @Mixin.priority()}.</li>
 *   <li><b>{@link #groups}</b>, <b>{@link #requires}</b>, <b>{@link #conflictsWith}</b> – tag sets copied
 *       from the mixin annotation for planning/selection.</li>
 *   <li><b>{@link #entries}</b> – mutable list populated with hook entries ({@code inject}/{@code redirect})
 *       discovered on the mixin's methods.</li>
 * </ul>
 *
 * <h2>Immutability &amp; thread-safety</h2>
 * <ul>
 *   <li>All reference fields are {@code final}. Constructor arguments that are collections are defensively
 *       copied via {@link List#copyOf} to prevent external mutation.</li>
 *   <li>{@link #entries} is intentionally mutable (via {@link ArrayList}) to allow the processor to append
 *       entries during scanning. Do not share {@code CollectedMixin} instances across threads without external
 *       synchronization.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * CollectedMixin cm = new CollectedMixin(
 *     typeElement,
 *     "com.example.MyMixin",
 *     List.of("com.example.TargetA", "com.example.TargetB"),
 *     1000,
 *     List.of("core"),
 *     List.of("feature-x"),
 *     List.of("feature-y")
 * );
 *
 * // While scanning methods:
 * cm.entries.add(new CollectedEntry(entryJson));
 * }</pre>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class CollectedMixin {

    /**
     * The compiler model element of the mixin class (declaring type).
     */
    @NotNull
    private final TypeElement type;

    /**
     * The mixin's binary class name (dot-separated; {@code $} for nested types),
     * e.g. {@code com.example.Outer$Inner}.
     */
    @NotNull
    private final String className;

    /**
     * Target class binary names (dot-separated) as declared in {@code @Mixin.targets()}.
     */
    @NotNull
    private final List<String> targets;

    /**
     * Mixin priority captured from {@code @Mixin.priority()}.
     */
    private final int priority;

    /**
     * Group tags associated with the mixin (from {@code @Mixin.groups()}).
     */
    @NotNull
    private final List<String> groups;

    /**
     * Required tags used during selection (from {@code @Mixin.requires()}).
     */
    @NotNull
    private final List<String> requires;

    /**
     * Tags this mixin conflicts with (from {@code @Mixin.conflictsWith()}).
     */
    @NotNull
    private final List<String> conflictsWith;

    /**
     * Hook entries ({@code inject}/{@code redirect}) discovered on the mixin's methods.
     * <p>Populated by the processor after construction.</p>
     */
    @NotNull
    private final List<CollectedEntry> entries = new ArrayList<>();

    /**
     * Creates a new collected mixin descriptor.
     *
     * @param type          the mixin's type element; must not be {@code null}
     * @param className     the binary class name (dot-separated); must not be {@code null}
     * @param targets       binary names of target classes; must not be {@code null}
     * @param priority      mixin priority
     * @param groups        group tags; must not be {@code null}
     * @param requires      required tags; must not be {@code null}
     * @param conflictsWith conflicting tags; must not be {@code null}
     */
    public CollectedMixin(@NotNull final TypeElement type,
                          @NotNull final String className,
                          @NotNull final List<String> targets,
                          final int priority,
                          @NotNull final List<String> groups,
                          @NotNull final List<String> requires,
                          @NotNull final List<String> conflictsWith) {
        this.type = type;
        this.className = className;
        this.targets = List.copyOf(targets);
        this.priority = priority;
        this.groups = List.copyOf(groups);
        this.requires = List.copyOf(requires);
        this.conflictsWith = List.copyOf(conflictsWith);
    }

    /**
     * Returns the mixin's type element.
     *
     * @return the type element; never {@code null}
     */
    @NotNull
    public TypeElement getType() {
        return this.type;
    }

    /**
     * Returns the mixin's binary class name (dot-separated; {@code $} for nested types),
     * e.g. {@code com.example.Outer$Inner}.
     *
     * @return the binary class name; never {@code null}
     */
    @NotNull
    public String getClassName() {
        return this.className;
    }

    /**
     * Returns the list of target class binary names (dot-separated) as declared in {@code @Mixin.targets()}.
     *
     * @return the target class names; never {@code null}
     */
    @NotNull
    public List<String> getTargets() {
        return this.targets;
    }

    /**
     * Returns the mixin's priority captured from {@code @Mixin.priority()}.
     *
     * @return the mixin priority
     */
    public int getPriority() {
        return this.priority;
    }

    /**
     * Returns the group tags associated with the mixin (from {@code @Mixin.groups()}).
     *
     * @return the group tags; never {@code null}
     */
    @NotNull
    public List<String> getGroups() {
        return this.groups;
    }

    /**
     * Returns the required tags used during selection (from {@code @Mixin.requires()}).
     *
     * @return the required tags; never {@code null}
     */
    @NotNull
    public List<String> getRequires() {
        return this.requires;
    }

    /**
     * Returns the tags this mixin conflicts with (from {@code @Mixin.conflictsWith()}).
     *
     * @return the conflicting tags; never {@code null}
     */
    @NotNull
    public List<String> getConflictsWith() {
        return this.conflictsWith;
    }

    /**
     * Returns the list of hook entries ({@code inject}/{@code redirect}) discovered on the mixin's methods.
     * <p>This list is mutable to allow the processor to append entries during scanning.</p>
     *
     * @return the list of collected entries; never {@code null}
     */
    @NotNull
    public List<CollectedEntry> getEntries() {
        return this.entries;
    }
}
