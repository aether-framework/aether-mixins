package de.splatgames.aether.mixins.core.config;

import de.splatgames.aether.mixins.core.config.mixins.MixinsConfig;
import de.splatgames.aether.mixins.core.config.runtime.VerifyFrames;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class YamlConfigLoaderTest {

    @TempDir
    Path tmp;

    private Path writeConfig(@NotNull final String fileName, @NotNull final String content) throws Exception {
        Path p = this.tmp.resolve(fileName);
        Files.writeString(p, content);
        return p;
    }

    @Test
    @DisplayName("Parses a minimal valid config and applies defaults")
    void parsesMinimalValidConfig() throws Exception {
        // given
        String yml = """
                version: 1
                mixins:
                  - name: core
                    files: ["mixins/core.refmap.json"]
                runtime:
                  safe_mode: true
                  verify_frames: strict
                  dump_classes_on_error: false
                """;

        // when
        MixinsConfig cfg = new YamlConfigLoader().load(writeConfig("ok.yml", yml));

        // then
        assertThat(cfg.getVersion()).isEqualTo(1);
        assertThat(cfg.getMixins()).hasSize(1);
        assertThat(cfg.getMixins().get(0).getName()).isEqualTo("core");
        assertThat(cfg.getMixins().get(0).getFiles()).isEqualTo(List.of("mixins/core.refmap.json"));

        // runtime defaults/values
        assertTrue(cfg.getRuntime().isSafeMode());
        assertThat(cfg.getRuntime().getVerifyFrames()).isEqualTo(VerifyFrames.STRICT);
        assertFalse(cfg.getRuntime().isDumpClassesOnError());

        // no unexpected errors
        assertThat(cfg.getRuntime().problems().all())
                .noneMatch(p -> p.severity() == de.splatgames.aether.mixins.core.config.problems.ConfigProblems.Severity.ERROR);
    }

    @Test
    @DisplayName("Version policy: 0 or >1 is reset to 1 and a warning is recorded")
    void versionPolicyResetsInvalidOrUnknown() throws Exception {
        // given
        String yml0 = """
                version: 0
                mixins: []
                """;
        String yml2 = """
                version: 2
                mixins: []
                """;

        // when
        MixinsConfig cfg0 = new YamlConfigLoader().load(writeConfig("v0.yml", yml0));
        MixinsConfig cfg2 = new YamlConfigLoader().load(writeConfig("v2.yml", yml2));

        // then
        assertThat(cfg0.getVersion()).isEqualTo(1);
        assertThat(cfg2.getVersion()).isEqualTo(1);

        assertThat(cfg0.getRuntime().problems().all())
                .anyMatch(p -> p.path().equals("version"));
        assertThat(cfg2.getRuntime().problems().all())
                .anyMatch(p -> p.path().equals("version"));
    }

    @Test
    @DisplayName("verify_frames: parses known values and falls back to STRICT with warning")
    void verifyFramesParsingAndFallback() throws Exception {
        // given
        String ok = """
                version: 1
                mixins: []
                runtime:
                  verify_frames: basic
                """;
        String bad = """
                version: 1
                mixins: []
                runtime:
                  verify_frames: fast
                """;

        // when
        MixinsConfig cfgOk = new YamlConfigLoader().load(writeConfig("vf-ok.yml", ok));
        MixinsConfig cfgBad = new YamlConfigLoader().load(writeConfig("vf-bad.yml", bad));

        // then
        assertThat(cfgOk.getRuntime().getVerifyFrames()).isNotEqualTo(VerifyFrames.STRICT);

        assertThat(cfgBad.getRuntime().getVerifyFrames()).isEqualTo(VerifyFrames.STRICT);
        assertThat(cfgBad.getRuntime().problems().all())
                .anyMatch(p -> p.path().equals("runtime.verify_frames"));
    }

    @Test
    @DisplayName("Mixins list: trims names, normalizes files (trim + de-dup), warns on empty or unnamed")
    void mixinsListWarningsAndNormalization() throws Exception {
        // given
        String yml = """
                version: 1
                mixins:
                  - name: "  core  "
                    files: ["  a.refmap.json  ", "a.refmap.json", " ", "", "b.refmap.json"]
                  - name: ""
                    files: []
                """;

        // when
        MixinsConfig cfg = new YamlConfigLoader().load(writeConfig("mixins.yml", yml));

        // then
        // unnamed set is skipped -> only one remains
        assertThat(cfg.getMixins()).hasSize(1);
        assertThat(cfg.getMixins().get(0).getName()).isEqualTo("core");

        // files are trimmed, empty entries removed, duplicates de-duped preserving order
        assertThat(cfg.getMixins().get(0).getFiles()).isEqualTo(List.of("a.refmap.json", "b.refmap.json"));

        // warnings recorded (for unnamed set and empty files)
        assertThat(cfg.getRuntime().problems().all())
                .isNotEmpty();
    }

    @Test
    @DisplayName("Warns when no mixins declared")
    void warnsWhenNoMixinsDeclared() throws Exception {
        // given
        String yml = """
                version: 1
                mixins: []
                """;

        // when
        MixinsConfig cfg = new YamlConfigLoader().load(writeConfig("nomix.yml", yml));

        // then
        assertThat(cfg.getMixins()).isEmpty();
        assertThat(cfg.getRuntime().problems().all())
                .anyMatch(p -> p.path().equals("mixins"));
    }
}
