package it;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigDiscoveryIT {

    private static final String YAML_B = """
            version: 1
            mixins:
              - name: wd
                classes: [ "it.config.mixins.HeadB" ]
            runtime: { safe_mode: false, verify_frames: strict }
            """;
    private static final String YAML_C = """
            version: 1
            mixins:
              - name: sys
                classes: [ "it.config.mixins.HeadC" ]
            runtime: { safe_mode: false, verify_frames: strict }
            """;

    private static int launch(final String agentJar, final File wd, final Path cfg, final Path outFile) throws Exception {
        var pb = new ProcessBuilder(
                javaBin(),
                "-javaagent:" + agentJar,
                "-cp", testClasspath(),
                cfg != null ? "-Daether.mixins.config=" + cfg.toAbsolutePath() : "",
                "it.config.AppMain"
        );
        pb.command().removeIf(String::isBlank);
        pb.directory(wd);
        pb.redirectErrorStream(true);
        pb.redirectOutput(outFile.toFile());
        return pb.start().waitFor();
    }

    private static String javaBin() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    private static String testClasspath() {
        return System.getProperty("java.class.path");
    }

    @Test
    void precedence_classpath_then_workingDir_then_systemProp() throws Exception {
        String agentJar = System.getProperty("agent.jar") != null
                ? System.getProperty("agent.jar")
                : System.getenv("agent.jar");
        assertNotNull(agentJar);

        Path wdA = Files.createTempDirectory("it-cp");
        Path outA = wdA.resolve("out.txt");
        int exitA = launch(agentJar, wdA.toFile(), null, outA);
        String outStrA = Files.readString(outA);
        assertEquals(0, exitA, outStrA);
        assertTrue(outStrA.contains("PATCH-A: ORIG"), "Expected PATCH-A, got:\n" + outStrA);

        Path wdB = Files.createTempDirectory("it-wd");
        Files.writeString(wdB.resolve("mixins.yml"), YAML_B, StandardCharsets.UTF_8);
        Path outB = wdB.resolve("out.txt");
        int exitB = launch(agentJar, wdB.toFile(), null, outB);
        String outStrB = Files.readString(outB);
        assertEquals(0, exitB, outStrB);
        assertTrue(outStrB.contains("PATCH-B: ORIG"), "Expected PATCH-B, got:\n" + outStrB);

        Path wdC = Files.createTempDirectory("it-sys");
        Path cfgC = wdC.resolve("mixinsC.yml");
        Files.writeString(cfgC, YAML_C, StandardCharsets.UTF_8);
        Path outC = wdC.resolve("out.txt");
        int exitC = launch(agentJar, wdC.toFile(), cfgC, outC);
        String outStrC = Files.readString(outC);
        assertEquals(0, exitC, outStrC);
        assertTrue(outStrC.contains("PATCH-C: ORIG"), "Expected PATCH-C, got:\n" + outStrC);
    }
}
