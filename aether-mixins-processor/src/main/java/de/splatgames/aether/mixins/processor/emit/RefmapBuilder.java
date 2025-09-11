package de.splatgames.aether.mixins.processor.emit;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.splatgames.aether.mixins.processor.model.CollectedEntry;
import de.splatgames.aether.mixins.processor.model.CollectedMixin;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Builds a refmap JSON document from {@link CollectedMixin} models.
 *
 * <p>The refmap captures, per mixin class, its targets, priority, entries (inject/redirect),
 * and grouping/requirement metadata. The resulting JSON has the following high-level shape:</p>
 *
 * <pre>{@code
 * {
 *   "schema": 1,
 *   "mixins": [
 *     {
 *       "class": "com.example.MyMixin",
 *       "targets": ["com.example.Target"],
 *       "priority": 1000,
 *       "entries": [ { ... }, { ... } ],
 *       "groups": [],
 *       "requires": [],
 *       "conflictsWith": []
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <h2>Schema</h2>
 * <p>The {@code schema} value is written as provided by the caller. This builder does not
 * enforce a particular version beyond recording the integer. Callers should pass
 * the currently supported schema version (e.g., {@code 1}).</p>
 *
 * <h2>Ordering</h2>
 * <ul>
 *   <li>Mixin objects are emitted in the iteration order of the provided {@code mixins} collection.</li>
 *   <li>Mixin entries are emitted in the iteration order of {@link CollectedMixin#getEntries()}.</li>
 * </ul>
 *
 * <h2>Validation</h2>
 * <p>This builder assumes that {@link CollectedMixin} and {@link CollectedEntry} instances are
 * already well-formed. No additional validation is performed here.</p>
 *
 * <h2>Thread-safety</h2>
 * <p>Stateless utility; all methods are thread-safe.</p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * JsonObject refmap = RefmapBuilder.build(collectedMixins, 1);
 * String json = new GsonBuilder().setPrettyPrinting().create().toJson(refmap);
 * }</pre>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class RefmapBuilder {
    /**
     * Private constructor to prevent instantiation.
     */
    private RefmapBuilder() {
        // utility class, prevent instantiation
    }

    /**
     * Builds the top-level refmap JSON object for the given mixin collection.
     *
     * <p>Fields written:</p>
     * <ul>
     *   <li>{@code schema}: the schema version provided by {@code schema}.</li>
     *   <li>{@code mixins}: an array where each element contains the mixin metadata and entries:
     *     <ul>
     *       <li>{@code class}: binary class name (dots), e.g. {@code com.example.MyMixin}</li>
     *       <li>{@code targets}: array of binary target class names (dots)</li>
     *       <li>{@code priority}: integer priority</li>
     *       <li>{@code entries}: array of entry JSON objects (as collected)</li>
     *       <li>{@code groups}, {@code requires}, {@code conflictsWith}: string arrays</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * @param mixins collection of collected mixins to serialize; must not be {@code null}
     * @param schema schema version number to record (e.g., {@code 1})
     * @return a populated JSON object representing the refmap; never {@code null}
     */
    @NotNull
    public static JsonObject build(@NotNull final Collection<CollectedMixin> mixins,
                                   final int schema) {
        final JsonObject root = new JsonObject();
        root.addProperty("schema", schema);

        final JsonArray arr = new JsonArray();
        for (final CollectedMixin cm : mixins) {
            final JsonObject m = new JsonObject();
            m.addProperty("class", cm.getClassName());
            m.add("targets", toArray(cm.getTargets()));
            m.addProperty("priority", cm.getPriority());
            m.add("entries", entriesArray(cm.getEntries()));
            m.add("groups", toArray(cm.getGroups()));
            m.add("requires", toArray(cm.getRequires()));
            m.add("conflictsWith", toArray(cm.getConflictsWith()));
            arr.add(m);
        }
        root.add("mixins", arr);
        return root;
    }

    /**
     * Serializes an iterable of strings into a {@link JsonArray}.
     *
     * <p>The iteration order of {@code values} is preserved in the resulting array.</p>
     *
     * @param values iterable of strings to serialize; must not be {@code null}
     * @return a JSON array containing each string value; never {@code null}
     */
    @NotNull
    private static JsonArray toArray(@NotNull final Iterable<String> values) {
        final JsonArray arr = new JsonArray();
        for (final String v : values) arr.add(v);
        return arr;
    }

    /**
     * Serializes an iterable of {@link CollectedEntry} into a {@link JsonArray}.
     *
     * <p>Each entry contributes its underlying {@link JsonObject} via {@link CollectedEntry#json()}.
     * The iteration order of {@code entries} is preserved in the resulting array.</p>
     *
     * @param entries iterable of collected entries; must not be {@code null}
     * @return a JSON array containing each entry's JSON object; never {@code null}
     */
    @NotNull
    private static JsonArray entriesArray(@NotNull final Iterable<CollectedEntry> entries) {
        final JsonArray arr = new JsonArray();
        for (final CollectedEntry ce : entries) {
            arr.add(ce.json());
        }
        return arr;
    }
}
