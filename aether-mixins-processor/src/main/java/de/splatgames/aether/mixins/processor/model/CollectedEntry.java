package de.splatgames.aether.mixins.processor.model;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Immutable wrapper for a single refmap entry represented as a {@link JsonObject}.
 *
 * <p>This model is used by the annotation processor to hold the fully materialized JSON
 * of an entry (e.g., an {@code inject} or {@code redirect} specification) before it is
 * assembled into the top-level refmap document.</p>
 *
 * <h2>Semantics</h2>
 * <ul>
 *   <li>The {@link #json()} must be a <em>complete</em> object for one entry, ready to be
 *       inserted into the refmap's {@code mixins[*].entries[]} array.</li>
 *   <li>This type imposes no schema beyond non-nullity; schema validation is handled upstream.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * JsonObject entry = new JsonObject();
 * entry.addProperty("type", "inject");
 * entry.addProperty("id", "onHead()V");
 * entry.addProperty("method", "doWork(I)V");
 * entry.addProperty("at", "HEAD");
 *
 * CollectedEntry ce = new CollectedEntry(entry);
 * // later: entriesArray.add(ce.json());
 * }</pre>
 *
 * <h2>Thread-safety</h2>
 * <p>The record itself is immutable, but note that {@link JsonObject} is mutable.
 * Callers should avoid mutating the returned {@link #json()} after construction if stable
 * output is desired.</p>
 *
 * @param json the JSON object representing a single refmap entry; must not be {@code null}
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record CollectedEntry(@NotNull JsonObject json) {
    /**
     * Creates a new {@code CollectedEntry}.
     *
     * <p>Performs a null check on the supplied JSON object.</p>
     *
     * @throws NullPointerException if {@code json} is {@code null}
     */
    public CollectedEntry {
        Objects.requireNonNull(json, "json must not be null");
    }
}
