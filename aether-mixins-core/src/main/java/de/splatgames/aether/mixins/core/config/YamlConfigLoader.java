package de.splatgames.aether.mixins.core.config;

import de.splatgames.aether.mixins.core.config.mixins.MixinSet;
import de.splatgames.aether.mixins.core.config.mixins.MixinsConfig;
import de.splatgames.aether.mixins.core.config.problems.ConfigProblems;
import de.splatgames.aether.mixins.core.config.runtime.RuntimeConfig;
import de.splatgames.aether.mixins.core.configuration.Configuration;
import de.splatgames.aether.mixins.core.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * YAML-backed implementation of {@link ConfigLoader} with validation and diagnostics.
 *
 * <p>This loader parses a {@code mixins.yml} (or similarly named) file using the project's
 * YAML API and materializes a {@link MixinsConfig}. It applies defaults, performs basic
 * validation, and records warnings/errors via {@link ConfigProblems}.</p>
 *
 * <h2>Behavior</h2>
 * <ul>
 *   <li>Reads the root configuration and the optional schema {@code version} (default: {@code 1}).</li>
 *   <li>Parses the {@code runtime} section into a {@link RuntimeConfig} (safe defaults applied).</li>
 *   <li>Parses the {@code mixins} list into {@link MixinSet} entries.</li>
 *   <li>Diagnostics are collected in {@link ConfigProblems} rather than written to STDERR.</li>
 * </ul>
 *
 * <h2>Example (YAML)</h2>
 * <blockquote><pre>{@code
 * version: 1
 *
 * mixins:
 *   - name: core-service
 *     files:
 *       - mixins/service.refmap.json
 *     classes:
 *       - com.example.ServiceMixin
 *
 * runtime:
 *   safe_mode: true
 *   verify_frames: strict
 *   dump_classes_on_error: false
 * }</pre></blockquote>
 *
 * <h2>Diagnostics</h2>
 * <p>Warnings (e.g., empty {@code mixins}, missing {@code files}, missing {@code classes}, invalid {@code verify_frames})
 * are recorded in the associated {@link ConfigProblems} instance accessible via
 * {@link RuntimeConfig#problems()}. Callers may inspect these to decide whether to proceed.</p>
 *
 * @author Erik Pförtner
 * @see MixinsConfig
 * @see RuntimeConfig
 * @see ConfigProblems
 * @since 0.1.0
 */
public final class YamlConfigLoader implements ConfigLoader {

    /**
     * Trims the input string and returns {@code null} if the result is empty or if the input is {@code null}.
     *
     * @param s the input string, may be {@code null}
     * @return trimmed string or {@code null}
     */
    @Nullable
    private static String trimOrNull(@Nullable String s) {
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    /**
     * Loads and parses the mixins configuration from a YAML file.
     *
     * <p>Applies defaults for missing optional fields and records diagnostics. Throws
     * an {@link IllegalStateException} if the root document is missing.</p>
     * <p><b>Version policy:</b> This loader currently supports {@code version = 1}.
     * Higher values are treated as unrecognized and will be reset to {@code 1} with a warning.</p>
     *
     * @param location path to the YAML configuration file, must not be {@code null}
     * @return parsed {@link MixinsConfig}, never {@code null}
     * @throws IllegalStateException if the root configuration is missing or empty
     */
    @NotNull
    @Override
    public MixinsConfig load(@NotNull final Path location) {
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(location.toFile());
        final String source = Objects.requireNonNullElse(yaml.getSourceDescription(), location.toString());
        final Configuration root = yaml.getRoot();

        if (root == null) {
            throw new IllegalStateException("Root configuration is missing or empty: " + location);
        }

        // Prepare diagnostics early so sub-parsers can record findings.
        final ConfigProblems problems = new ConfigProblems(source);

        // Parse version (schema) with sanity check.
        int version = root.getInt(YamlConfigurationConstants.K_VERSION, 1);
        if (version <= 0) {
            problems.warn(YamlConfigurationConstants.K_VERSION, "Non-positive version '" + version + "'; falling back to 1");
            version = 1;
        } else if (version > 1) {
            problems.warn(YamlConfigurationConstants.K_VERSION, "Unrecognized version '" + version + "'; defaulting to 1");
            version = 1;
        }

        final MixinsConfig cfg = new MixinsConfig(version);
        cfg.setRuntime(parseRuntime(root, problems));
        cfg.setMixins(parseMixins(root, problems));

        return cfg;
    }

    /**
     * Parses the {@code runtime} section, applying defaults and recording diagnostics.
     *
     * @param root     root configuration node
     * @param problems diagnostics collector
     * @return initialized {@link RuntimeConfig}
     */
    @NotNull
    private RuntimeConfig parseRuntime(@NotNull final Configuration root, @NotNull final ConfigProblems problems) {
        final RuntimeConfig rt = new RuntimeConfig(problems);

        rt.setSafeMode(root.getBoolean(YamlConfigurationConstants.K_RUNTIME_SAFE, true));
        rt.setVerifyFramesString(root.getString(YamlConfigurationConstants.K_RUNTIME_VERIFY, "strict"), YamlConfigurationConstants.K_RUNTIME_VERIFY);
        rt.setDumpClassesOnError(root.getBoolean(YamlConfigurationConstants.K_RUNTIME_DUMP, false));

        return rt;
    }

    /**
     * Normalizes a value into a list of unique, trimmed strings.
     *
     * <p>If the input is not a list, returns an empty list. Trims each element and
     * removes duplicates while preserving order.</p>
     *
     * @param value the value to normalize; may be {@code null}
     * @return a list of unique, trimmed strings; never {@code null}
     */
    private List<String> normalizeStrings(@Nullable final Object value) {
        if (!(value instanceof List<?> raw)) return List.of();
        List<String> out = new ArrayList<>(raw.size());
        for (Object o : raw) {
            String s = trimOrNull(o == null ? null : o.toString());
            if (s != null && !out.contains(s)) out.add(s);
        }
        return out;
    }

    /**
     * Parses the {@code mixins} list, validating required fields and recording diagnostics.
     *
     * @param root     root configuration node
     * @param problems diagnostics collector
     * @return list of {@link MixinSet} (may be empty but never {@code null})
     */
    private List<MixinSet> parseMixins(@NotNull final Configuration root, @NotNull final ConfigProblems problems) {
        List<? extends Map<?, ?>> rawMixins = root.getMapList(YamlConfigurationConstants.K_MIXINS);
        List<MixinSet> sets = new ArrayList<>();
        if (rawMixins.isEmpty()) {
            problems.warn(YamlConfigurationConstants.K_MIXINS, "No mixins declared.");
            return sets;
        }

        for (int i = 0; i < rawMixins.size(); i++) {
            Map<?, ?> entry = rawMixins.get(i);
            String path = "mixins[" + i + "]";
            String name = trimOrNull(valueOrNull(entry.get(YamlConfigurationConstants.K_NAME)));
            if (name == null) {
                problems.warn(path + "." + YamlConfigurationConstants.K_NAME, "Unnamed mixin set encountered; entry will be skipped.");
                continue;
            }

            List<String> files = normalizeStrings(entry.get(YamlConfigurationConstants.K_FILES));
            List<String> classes = normalizeStrings(entry.get(YamlConfigurationConstants.K_CLASSES));

            if (files.isEmpty() && classes.isEmpty()) {
                problems.warn(path, "Mixin set '" + name + "' declares neither files nor classes.");
            }

            MixinSet set = new MixinSet();
            set.setName(name);
            set.setFiles(files);
            set.setClasses(classes); // NEW
            sets.add(set);
        }
        return sets;
    }

    /**
     * Returns {@code value.toString()} or {@code null} if {@code value} is {@code null}.
     *
     * @param value any value, may be {@code null}
     * @return string value or {@code null}
     */
    @Nullable
    private String valueOrNull(@Nullable final Object value) {
        return (value != null) ? value.toString() : null;
    }

    /**
     * Normalizes a value into a list of unique, trimmed strings.
     *
     * <p>If the input is not a list, returns an empty list. Trims each element and
     * removes duplicates while preserving order.</p>
     *
     * @param value the value to normalize; may be {@code null}
     * @return a list of unique, trimmed strings; never {@code null}
     */
    @NotNull
    private List<String> normalizeFiles(@Nullable final Object value) {
        if (!(value instanceof List<?> rawList)) return List.of();
        final List<String> out = new ArrayList<>(rawList.size());
        for (Object o : rawList) {
            final String s = trimOrNull(o == null ? null : o.toString());
            if (s != null && !out.contains(s)) { // de-dup
                out.add(s);
            }
        }
        return out;
    }
}
