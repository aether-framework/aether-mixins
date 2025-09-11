package de.splatgames.aether.mixins.core.config.refmap;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Redirect;
import de.splatgames.aether.mixins.core.config.problems.ConfigProblems;
import de.splatgames.aether.mixins.core.config.problems.ConfigProblems.Severity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

final class JsonRefmapLoaderTest {

    @TempDir
    Path tmp;

    private Path writeJson(@NotNull final String name, @NotNull final String json) throws IOException {
        Path p = tmp.resolve(name);
        Files.writeString(p, json);
        return p;
    }

    private boolean hasProblem(@NotNull final ConfigProblems problems, @NotNull final  Severity sev, @Nullable final String pathContains, @Nullable final String msgContains) {
        return problems.all().stream().anyMatch(pr ->
            pr.severity() == sev &&
            (pathContains == null || pr.path().contains(pathContains)) &&
            (msgContains == null || pr.message().contains(msgContains))
        );
    }

    @Test
    void load_valid_inject_and_redirect() throws IOException {
        // given
        String json = """
        {
          "schema": 1,
          "mixins": [{
            "class": "com.example.ServiceMixin",
            "targets": ["com.example.Service", "com.example.Service", "com.example.Other"],
            "priority": 100,
            "entries": [
              { "type": "inject", "id": "enter",
                "method": "process(Ljava/lang/String;)V", "at": "HEAD", "optional": false, "remap": true
              },
              { "type": "redirect", "id": "calc",
                "method": "process(Ljava/lang/String;)V",
                "callOwner": "com/example/Util", "callName": "calc", "callDesc": "(I)I",
                "kind": "INVOKESTATIC", "ordinal": 0, "optional": true, "remap": true
              }
            ],
            "groups": ["core"],
            "requires": [],
            "conflictsWith": []
          }]
        }
        """;
        Path file = writeJson("refmap.json", json);
        ConfigProblems problems = new ConfigProblems(file.toString());
        JsonRefmapLoader loader = new JsonRefmapLoader();

        // when
        Refmap refmap = loader.load(file, problems);

        // then
        assertThat(refmap.getSchema()).isEqualTo(1);
        assertThat(refmap.getMixins()).hasSize(1);

        RefMixin m = refmap.getMixins().get(0);
        assertThat(m.getClassName()).isEqualTo("com.example.ServiceMixin");
        assertThat(m.getPriority()).isEqualTo(100);

        // dedup + order preserved
        assertThat(m.getTargets()).containsExactly("com.example.Service", "com.example.Other");

        assertThat(m.getEntries()).hasSize(2);
        RefEntry e0 = m.getEntries().get(0);
        assertThat(e0.getType()).isEqualTo(RefEntry.Type.INJECT);
        assertThat(e0.getMethod()).isEqualTo("process(Ljava/lang/String;)V");
        assertThat(e0.getAt()).isEqualTo(Inject.At.HEAD);
        assertThat(e0.isOptional()).isFalse();
        assertThat(e0.isRemap()).isTrue();

        RefEntry e1 = m.getEntries().get(1);
        assertThat(e1.getType()).isEqualTo(RefEntry.Type.REDIRECT);
        assertThat(e1.getMethod()).isEqualTo("process(Ljava/lang/String;)V");
        assertThat(e1.getCallOwner()).isEqualTo("com/example/Util");
        assertThat(e1.getCallName()).isEqualTo("calc");
        assertThat(e1.getCallDesc()).isEqualTo("(I)I");
        assertThat(e1.getKind()).isEqualTo(Redirect.InvokeKind.INVOKESTATIC);
        assertThat(e1.getOrdinal()).isEqualTo(0);
        assertThat(e1.isOptional()).isTrue();

        assertThat(problems.hasErrors()).isFalse();
    }

    @Test
    void schema_unrecognized_emits_warning() throws IOException {
        String json = """
        {
          "schema": 2,
          "mixins": [{
            "class": "x.M",
            "targets": ["x.T"],
            "entries": [{ "type": "inject", "method": "m()V", "at": "TAIL" }]
          }]
        }
        """;
        Path file = writeJson("refmap_schema2.json", json);
        ConfigProblems problems = new ConfigProblems(file.toString());

        Refmap refmap = new JsonRefmapLoader().load(file, problems);

        assertThat(refmap.getSchema()).isEqualTo(2);
        assertThat(hasProblem(problems, Severity.WARNING, "refmap.schema", "Unrecognized schema")).isTrue();
        assertThat(problems.hasErrors()).isFalse();
    }

    @Test
    void missing_required_fields_creates_errors() throws IOException {
        // missing 'method' and missing 'entries' array -> both should error
        String json = """
        {
          "schema": 1,
          "mixins": [{
            "class": "x.M",
            "targets": ["x.T"],
            "entries": [
              { "type": "inject", "at": "HEAD" }
            ]
          }]
        }
        """;
        Path file = writeJson("refmap_missing_fields.json", json);
        ConfigProblems problems = new ConfigProblems(file.toString());

        Refmap refmap = new JsonRefmapLoader().load(file, problems);

        assertThat(refmap.getMixins()).hasSize(1);
        // method missing -> error on entries[0].method
        assertThat(hasProblem(problems, Severity.ERROR, ".entries[0].method", "required")).isTrue();
        assertThat(problems.hasErrors()).isTrue();
    }

    @Test
    void invalid_kind_defaults_to_auto_with_warning() throws IOException {
        String json = """
        {
          "schema": 1,
          "mixins": [{
            "class": "x.M",
            "targets": ["x.T"],
            "entries": [{
              "type": "redirect",
              "method": "m()V",
              "callOwner": "x/Y",
              "callName": "z",
              "callDesc": "()V",
              "kind": "NOT_A_KIND"
            }]
          }]
        }
        """;
        Path file = writeJson("refmap_invalid_kind.json", json);
        ConfigProblems problems = new ConfigProblems(file.toString());

        Refmap refmap = new JsonRefmapLoader().load(file, problems);

        RefEntry e = refmap.getMixins().get(0).getEntries().get(0);
        assertThat(e.getKind()).isEqualTo(Redirect.InvokeKind.AUTO);
        assertThat(hasProblem(problems, Severity.WARNING, ".kind", "defaulting to AUTO")).isTrue();
    }

    @Test
    void inject_with_redirect_fields_emits_warnings() throws IOException {
        String json = """
        {
          "schema": 1,
          "mixins": [{
            "class": "x.M",
            "targets": ["x.T"],
            "entries": [{
              "type": "inject",
              "method": "m()V",
              "at": "HEAD",
              "callOwner": "x/Y",
              "callName": "z",
              "callDesc": "()V",
              "kind": "INVOKESTATIC",
              "ordinal": 0
            }]
          }]
        }
        """;
        Path file = writeJson("refmap_inject_with_redirect_fields.json", json);
        ConfigProblems problems = new ConfigProblems(file.toString());

        new JsonRefmapLoader().load(file, problems);

        assertThat(hasProblem(problems, Severity.WARNING, ".callOwner", "ignored for INJECT")).isTrue();
        assertThat(hasProblem(problems, Severity.WARNING, ".callName", "ignored for INJECT")).isTrue();
        assertThat(hasProblem(problems, Severity.WARNING, ".callDesc", "ignored for INJECT")).isTrue();
        assertThat(hasProblem(problems, Severity.WARNING, ".kind", "ignored for INJECT")).isTrue();
        assertThat(hasProblem(problems, Severity.WARNING, ".ordinal", "ignored for INJECT")).isTrue();
    }

    @Test
    void redirect_with_at_field_emits_warning() throws IOException {
        String json = """
        {
          "schema": 1,
          "mixins": [{
            "class": "x.M",
            "targets": ["x.T"],
            "entries": [{
              "type": "redirect",
              "method": "m()V",
              "callOwner": "x/Y",
              "callName": "z",
              "callDesc": "()V",
              "at": "HEAD"
            }]
          }]
        }
        """;
        Path file = writeJson("refmap_redirect_with_at.json", json);
        ConfigProblems problems = new ConfigProblems(file.toString());

        new JsonRefmapLoader().load(file, problems);

        assertThat(hasProblem(problems, Severity.WARNING, ".at", "ignored for REDIRECT")).isTrue();
    }

    @Test
    void trims_and_dedups_string_arrays() throws IOException {
        String json = """
        {
          "schema": 1,
          "mixins": [{
            "class": "x.M",
            "targets": ["  a.A  ", "a.A", "", "b.B", "b.B"],
            "entries": [{ "type": "inject", "method": "m()V", "at": "TAIL" }],
            "groups": [" g ", "g", "h"],
            "requires": ["R1", "R1", "R2"],
            "conflictsWith": ["C1", "C1"]
          }]
        }
        """;
        Path file = writeJson("refmap_trim_dedup.json", json);
        ConfigProblems problems = new ConfigProblems(file.toString());

        Refmap refmap = new JsonRefmapLoader().load(file, problems);
        RefMixin m = refmap.getMixins().get(0);

        assertThat(m.getTargets()).containsExactly("a.A", "b.B");
        assertThat(m.getGroups()).containsExactly("g", "h");
        assertThat(m.getRequires()).containsExactly("R1", "R2");
        assertThat(m.getConflictsWith()).containsExactly("C1");

        assertThat(problems.hasErrors()).isFalse();
    }

    @Test
    void ordinal_negative_warns() throws IOException {
        String json = """
        {
          "schema": 1,
          "mixins": [{
            "class": "x.M",
            "targets": ["x.T"],
            "entries": [{
              "type": "redirect",
              "method": "m()V",
              "callOwner": "x/Y",
              "callName": "z",
              "callDesc": "()V",
              "ordinal": -2
            }]
          }]
        }
        """;
        Path file = writeJson("refmap_ordinal_warn.json", json);
        ConfigProblems problems = new ConfigProblems(file.toString());

        new JsonRefmapLoader().load(file, problems);

        assertThat(hasProblem(problems, Severity.WARNING, ".ordinal", "should be -1")).isTrue();
    }

    @Test
    void lenient_parsing_allows_comments() throws IOException {
        String json = """
    {
      // comment allowed by lenient Gson
      "schema": 1,
      "mixins": [
        {
          "class": "x.M",
          "targets": ["x.T"],
          "entries": [{ "type": "inject", "method": "m()V", "at": "HEAD" }]
        }
      ]
    }
    """;
        Path file = writeJson("refmap_lenient.json", json);
        ConfigProblems problems = new ConfigProblems(file.toString());

        Refmap refmap = new JsonRefmapLoader().load(file, problems);

        assertThat(refmap.getMixins()).hasSize(1);
        assertThat(refmap.getMixins().get(0).getTargets()).containsExactly("x.T");
        assertThat(problems.hasErrors()).isFalse();
    }
}
