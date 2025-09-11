package de.splatgames.aether.mixins.core.config.refmap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Redirect;
import de.splatgames.aether.mixins.core.config.problems.ConfigProblems;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Gson-backed implementation of {@link RefmapLoader}.
 *
 * <p>This loader reads a JSON refmap document and materializes it into a {@link Refmap}.
 * Content issues (missing fields, invalid values) are reported via {@link ConfigProblems};
 * only I/O failures are thrown as {@link IOException}.</p>
 *
 * <h2>Input shape (MVP)</h2>
 * <pre>{@code
 * {
 *   "schema": 1,
 *   "mixins": [
 *     {
 *       "class": "de.example.ServiceMixin",
 *       "targets": ["de.example.Service"],
 *       "priority": 100,
 *       "entries": [
 *         { "type": "inject", "id": "enter",
 *           "method": "process(Ljava/lang/String;)V", "at": "HEAD", "optional": false, "remap": true
 *         },
 *         { "type": "redirect", "id": "calc",
 *           "method": "process(Ljava/lang/String;)V",
 *           "callOwner": "com/example/Util", "callName": "calc", "callDesc": "(I)I",
 *           "kind": "INVOKESTATIC", "ordinal": 0, "optional": true, "remap": true
 *         }
 *       ],
 *       "groups": ["core"],
 *       "requires": [],
 *       "conflictsWith": []
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <h2>Parsing rules</h2>
 * <ul>
 *   <li>Unknown/invalid scalar types are reported and replaced by sensible defaults.</li>
 *   <li>String arrays are <em>trimmed</em>, empty entries are removed, and duplicates are de-duplicated
 *       while preserving order.</li>
 *   <li>Enums are case-insensitive; invalid values are reported. For {@code kind}, the effective default is {@code AUTO}.</li>
 *   <li>After parsing, {@link Refmap#validate(ConfigProblems, String)} and nested validations are invoked.</li>
 * </ul>
 *
 * <h2>Error handling</h2>
 * <ul>
 *   <li>I/O errors: throw {@link IOException}.</li>
 *   <li>Content issues: record via {@link ConfigProblems#warn(String, String)} or
 *       {@link ConfigProblems#error(String, String)} and proceed with defaults where possible.</li>
 * </ul>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class JsonRefmapLoader implements RefmapLoader {

    /**
     * Gson instance used for parsing (lenient to tolerate minor formatting issues).
     */
    private static final Gson GSON = new GsonBuilder().setLenient().create();

    /**
     * Reads an integer field from a JSON object, with defaulting and diagnostics.
     *
     * @param obj      JSON object to read from, must not be {@code null}
     * @param key      field name, must not be {@code null}
     * @param def      default value if the field is missing or invalid
     * @param problems diagnostics collector, must not be {@code null}
     * @param path     JSON path for diagnostics, must not be {@code null}
     * @return the integer value, or {@code def} if missing/invalid
     */
    private static int readInt(@NotNull final JsonObject obj, @NotNull final String key, final int def,
                               @NotNull final ConfigProblems problems, @NotNull final String path) {
        final JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) return def;
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isNumber()) {
            problems.warn(path, "Expected integer; using default " + def);
            return def;
        }
        try {
            return el.getAsInt();
        } catch (Exception ex) {
            problems.warn(path, "Invalid integer; using default " + def);
            return def;
        }
    }

    /**
     * Reads a boolean field from a JSON object, with defaulting and diagnostics.
     *
     * @param obj      JSON object to read from, must not be {@code null}
     * @param key      field name, must not be {@code null}
     * @param def      default value if the field is missing or invalid
     * @param problems diagnostics collector, must not be {@code null}
     * @param path     JSON path for diagnostics, must not be {@code null}
     * @return the boolean value, or {@code def} if missing/invalid
     */
    private static boolean readBool(@NotNull final JsonObject obj, @NotNull final String key, final boolean def,
                                    @NotNull final ConfigProblems problems, @NotNull final String path) {
        final JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) return def;
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isBoolean()) {
            problems.warn(path, "Expected boolean; using default " + def);
            return def;
        }
        return el.getAsBoolean();
    }

    /**
     * Reads a string field from a JSON object, with trimming, defaulting, and diagnostics.
     *
     * @param obj      JSON object to read from, must not be {@code null}
     * @param key      field name, must not be {@code null}
     * @param def      default value if the field is missing or invalid
     * @param required if {@code true}, a missing field is reported as an error
     * @param problems diagnostics collector, must not be {@code null}
     * @param path     JSON path for diagnostics, must not be {@code null}
     * @return the trimmed string value, or {@code def} if missing/invalid
     */
    @Nullable
    private static String readString(@NotNull final JsonObject obj, @NotNull final String key,
                                     @Nullable final String def, final boolean required,
                                     @NotNull final ConfigProblems problems, @NotNull final String path) {
        final JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) {
            if (required) problems.error(path, "Missing required string.");
            return def;
        }
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) {
            problems.warn(path, "Expected string; using default " + def);
            return def;
        }
        final String s = el.getAsString();
        return (s != null) ? s.trim() : def;
    }

    /**
     * Reads an array of strings from a JSON element, with trimming, de-duplication, and diagnostics.
     *
     * @param el       JSON element to read from, may be {@code null}
     * @param required if {@code true}, a missing element is reported as an error
     * @param problems diagnostics collector, must not be {@code null}
     * @param path     JSON path for diagnostics, must not be {@code null}
     * @return a list of trimmed, non-empty, unique strings; never {@code null}
     */
    @NotNull
    private static List<@NotNull String> readStringArray(@Nullable final JsonElement el,
                                                         final boolean required,
                                                         @NotNull final ConfigProblems problems,
                                                         @NotNull final String path) {
        if (el == null || el.isJsonNull()) {
            if (required) problems.error(path, "Missing required array.");
            return List.of();
        }
        if (!el.isJsonArray()) {
            problems.error(path, "Expected array of strings.");
            return List.of();
        }
        final JsonArray arr = el.getAsJsonArray();
        final List<String> out = new ArrayList<>(arr.size());
        final Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < arr.size(); i++) {
            final JsonElement item = arr.get(i);
            final String ip = path + "[" + i + "]";
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                problems.warn(ip, "Expected string; entry ignored.");
                continue;
            }
            final String v = Optional.ofNullable(item.getAsString()).orElse("").trim();
            if (v.isEmpty()) continue;
            if (seen.add(v)) out.add(v);
        }
        return List.copyOf(out);
    }

    /**
     * If the given key is present in the object, emits a warning with the given message.
     *
     * @param obj      JSON object to check, must not be {@code null}
     * @param key      field name to check, must not be {@code null}
     * @param problems diagnostics collector, must not be {@code null}
     * @param path     JSON path for diagnostics, must not be {@code null}
     * @param msg      warning message to emit, must not be {@code null}
     */
    private static void warnIfPresent(@NotNull final JsonObject obj, @NotNull final String key,
                                      @NotNull final ConfigProblems problems, @NotNull final String path,
                                      @NotNull final String msg) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            problems.warn(path, msg);
        }
    }

    /**
     * Parses the entry type from a string, with diagnostics.
     *
     * @param s        string to parse, may be {@code null}
     * @param problems diagnostics collector, must not be {@code null}
     * @param path     JSON path for diagnostics, must not be {@code null}
     * @return the parsed type, or {@code null} if parsing failed
     */
    @Nullable
    private static RefEntry.Type parseType(@Nullable final String s,
                                           @NotNull final ConfigProblems problems,
                                           @NotNull final String path) {
        if (s == null) return null;
        return switch (s.trim().toLowerCase(Locale.ROOT)) {
            case "inject" -> RefEntry.Type.INJECT;
            case "redirect" -> RefEntry.Type.REDIRECT;
            default -> {
                problems.error(path, "Unknown entry type: " + s);
                yield null;
            }
        };
    }

    /**
     * Parses the injection point from a string, with diagnostics.
     *
     * @param s        string to parse, may be {@code null}
     * @param problems diagnostics collector, must not be {@code null}
     * @param path     JSON path for diagnostics, must not be {@code null}
     * @return the parsed injection point, or {@code null} if parsing failed
     */
    @Nullable
    private static Inject.At parseAt(@Nullable final String s,
                                     @NotNull final ConfigProblems problems,
                                     @NotNull final String path) {
        if (s == null) return null;
        final String up = s.trim().toUpperCase(Locale.ROOT);
        try {
            return Inject.At.valueOf(up);
        } catch (IllegalArgumentException ex) {
            problems.error(path, "Unknown 'at': " + s + " (expected HEAD or TAIL)");
            return null;
        }
    }

    /**
     * Parses the redirect invocation kind from a string, with diagnostics.
     *
     * @param s        string to parse, may be {@code null}
     * @param problems diagnostics collector, must not be {@code null}
     * @param path     JSON path for diagnostics, must not be {@code null}
     * @return the parsed kind; {@code null} if input is {@code null}, otherwise {@link Redirect.InvokeKind#AUTO} after a warning if invalid
     */
    @Nullable
    private static Redirect.InvokeKind parseInvokeKind(@Nullable final String s,
                                                       @NotNull final ConfigProblems problems,
                                                       @NotNull final String path) {
        if (s == null) return null;
        final String up = s.trim().toUpperCase(Locale.ROOT);
        try {
            return Redirect.InvokeKind.valueOf(up);
        } catch (IllegalArgumentException ex) {
            problems.warn(path, "Unknown 'kind': " + s + " (defaulting to AUTO)");
            return Redirect.InvokeKind.AUTO;
        }
    }

    /**
     * Loads and parses a refmap document from the given JSON file.
     *
     * <p>The returned {@link Refmap} is always non-null. Content diagnostics are collected
     * in {@code problems}. Callers may inspect {@link ConfigProblems#hasErrors()} to decide
     * whether to proceed.</p>
     *
     * @param file     path to the JSON refmap file, must not be {@code null}
     * @param problems diagnostics collector to record warnings/errors, must not be {@code null}
     * @return a parsed {@link Refmap} instance, never {@code null}
     * @throws IOException if the file cannot be read or JSON parsing fails
     */
    @Override
    @NotNull
    public Refmap load(@NotNull final Path file, @NotNull final ConfigProblems problems) throws IOException {
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            final JsonElement rootEl = GSON.fromJson(r, JsonElement.class);
            if (rootEl == null || !rootEl.isJsonObject()) {
                throw new IOException("Refmap root must be a JSON object: " + file);
            }
            final JsonObject root = rootEl.getAsJsonObject();

            final int schema = readInt(root, "schema", Refmap.CURRENT_SCHEMA, problems, "schema");
            final Refmap refmap = new Refmap(schema);

            final List<RefMixin> mixins = parseMixins(root.get("mixins"), problems, "mixins");
            refmap.setMixins(mixins);

            refmap.validate(problems, "refmap");
            return refmap;
        } catch (JsonParseException jpe) {
            throw new IOException("Failed parsing refmap JSON: " + file + " (" + jpe.getMessage() + ")", jpe);
        }
    }

    /**
     * Parses the array of mixins from a JSON element, with diagnostics.
     *
     * @param el       JSON element to read from, may be {@code null}
     * @param problems diagnostics collector, must not be {@code null}
     * @param path     JSON path for diagnostics, must not be {@code null}
     * @return a list of parsed mixins; never {@code null}
     */
    @NotNull
    private List<RefMixin> parseMixins(@Nullable final JsonElement el,
                                       @NotNull final ConfigProblems problems,
                                       @NotNull final String path) {
        if (el == null || el.isJsonNull()) {
            problems.warn(path, "No mixins declared (missing array).");
            return List.of();
        }
        if (!el.isJsonArray()) {
            problems.error(path, "Field must be an array of objects.");
            return List.of();
        }

        final JsonArray arr = el.getAsJsonArray();
        final List<RefMixin> out = new ArrayList<>(arr.size());

        for (int i = 0; i < arr.size(); i++) {
            final JsonElement item = arr.get(i);
            final String p = path + "[" + i + "]";
            if (!item.isJsonObject()) {
                problems.error(p, "Mixin entry must be a JSON object.");
                continue;
            }
            final RefMixin mixin = parseMixin(item.getAsJsonObject(), problems, p);
            out.add(mixin);
        }
        return List.copyOf(out);
    }

    /**
     * Parses a single mixin from a JSON object, with diagnostics.
     *
     * @param obj      JSON object to read from, must not be {@code null}
     * @param problems diagnostics collector, must not be {@code null}
     * @param path     JSON path for diagnostics, must not be {@code null}
     * @return the parsed mixin; never {@code null}
     */
    @NotNull
    private RefMixin parseMixin(@NotNull final JsonObject obj,
                                @NotNull final ConfigProblems problems,
                                @NotNull final String path) {
        final RefMixin m = new RefMixin();

        // class name
        final String className = readString(obj, "class", null, true, problems, path + ".class");
        m.setClassName(className);

        // targets
        final List<String> targets = readStringArray(obj.get("targets"), true, problems, path + ".targets");
        m.setTargets(targets);

        // priority
        final int priority = readInt(obj, "priority", 0, problems, path + ".priority");
        m.setPriority(priority);

        // entries
        final List<RefEntry> entries = parseEntries(obj.get("entries"), problems, path + ".entries");
        m.setEntries(entries);

        // optional arrays
        m.setGroups(readStringArray(obj.get("groups"), false, problems, path + ".groups"));
        m.setRequires(readStringArray(obj.get("requires"), false, problems, path + ".requires"));
        m.setConflictsWith(readStringArray(obj.get("conflictsWith"), false, problems, path + ".conflictsWith"));

        // per-mixin validation (includes nested entries)
        m.validate(problems, path);
        return m;
    }

    /**
     * Parses the array of entries from a JSON element, with diagnostics.
     *
     * @param el       JSON element to read from, may be {@code null}
     * @param problems diagnostics collector, must not be {@code null}
     * @param path     JSON path for diagnostics, must not be {@code null}
     * @return a list of parsed entries; never {@code null}
     */
    @NotNull
    private List<RefEntry> parseEntries(@Nullable final JsonElement el,
                                        @NotNull final ConfigProblems problems,
                                        @NotNull final String path) {
        if (el == null || el.isJsonNull()) {
            problems.error(path, "Missing entries array.");
            return List.of();
        }
        if (!el.isJsonArray()) {
            problems.error(path, "Field must be an array of objects.");
            return List.of();
        }

        final JsonArray arr = el.getAsJsonArray();
        final List<RefEntry> out = new ArrayList<>(arr.size());

        for (int i = 0; i < arr.size(); i++) {
            final JsonElement item = arr.get(i);
            final String p = path + "[" + i + "]";
            if (!item.isJsonObject()) {
                problems.error(p, "Entry must be a JSON object.");
                continue;
            }
            final RefEntry e = parseEntry(item.getAsJsonObject(), problems, p);
            out.add(e);
        }
        return List.copyOf(out);
    }

    /**
     * Parses a single entry from a JSON object, with diagnostics.
     *
     * @param obj      JSON object to read from, must not be {@code null}
     * @param problems diagnostics collector, must not be {@code null}
     * @param path     JSON path for diagnostics, must not be {@code null}
     * @return the parsed entry; never {@code null}
     */
    @NotNull
    private RefEntry parseEntry(@NotNull final JsonObject obj,
                                @NotNull final ConfigProblems problems,
                                @NotNull final String path) {
        final String typeStr = readString(obj, "type", null, true, problems, path + ".type");
        final RefEntry.Type type = parseType(typeStr, problems, path + ".type");

        final RefEntry e = new RefEntry();
        if (type != null) e.setType(type);

        // common
        e.setId(Objects.requireNonNull(readString(obj, "id", "", false, problems, path + ".id")));
        final String method = readString(obj, "method", null, true, problems, path + ".method");
        if (method != null) e.setMethod(method);
        e.setOptional(readBool(obj, "optional", false, problems, path + ".optional"));
        e.setRemap(readBool(obj, "remap", true, problems, path + ".remap"));

        if (type == RefEntry.Type.INJECT) {
            final String atStr = readString(obj, "at", null, true, problems, path + ".at");
            final Inject.At at = parseAt(atStr, problems, path + ".at");
            if (at != null) e.setAt(at);

            // warn on redirect-only fields if present
            warnIfPresent(obj, "callOwner", problems, path + ".callOwner", "ignored for INJECT");
            warnIfPresent(obj, "callName", problems, path + ".callName", "ignored for INJECT");
            warnIfPresent(obj, "callDesc", problems, path + ".callDesc", "ignored for INJECT");
            warnIfPresent(obj, "kind", problems, path + ".kind", "ignored for INJECT");
            warnIfPresent(obj, "ordinal", problems, path + ".ordinal", "ignored for INJECT");

        } else if (type == RefEntry.Type.REDIRECT) {
            final String owner = readString(obj, "callOwner", null, true, problems, path + ".callOwner");
            final String name = readString(obj, "callName", null, true, problems, path + ".callName");
            final String desc = readString(obj, "callDesc", null, true, problems, path + ".callDesc");
            if (owner != null) e.setCallOwner(owner);
            if (name != null) e.setCallName(name);
            if (desc != null) e.setCallDesc(desc);

            final String kindStr = readString(obj, "kind", "AUTO", false, problems, path + ".kind");
            final Redirect.InvokeKind kind = parseInvokeKind(kindStr, problems, path + ".kind");
            if (kind != null) e.setKind(kind);

            final int ordinal = readInt(obj, "ordinal", -1, problems, path + ".ordinal");
            e.setOrdinal(ordinal);

            // warn on inject-only field if present
            warnIfPresent(obj, "at", problems, path + ".at", "ignored for REDIRECT");
        }

        // per-entry validation
        e.validate(problems, path);
        return e;
    }
}
