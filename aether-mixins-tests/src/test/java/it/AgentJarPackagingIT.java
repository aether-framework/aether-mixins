package it;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AgentJarPackagingIT {

    private static String javaBin() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    private static String testClasspath() {
        return System.getProperty("java.class.path");
    }

    @Test
    void agentJarHasManifestAndRuns() throws Exception {
        String agentJar = System.getProperty("agent.jar") != null ?
                System.getProperty("agent.jar") :
                System.getenv("agent.jar");
        assertNotNull(agentJar, "System property 'agent.jar' or env var 'agent.jar' must be set for integration tests");
        assertTrue(Files.isRegularFile(Path.of(agentJar)), "Agent JAR missing: " + agentJar);

        try (JarFile jf = new JarFile(agentJar)) {
            var a = jf.getManifest().getMainAttributes();
            assertEquals("de.splatgames.aether.mixins.agent.MixinsAgent", a.getValue("Premain-Class"));
            assertEquals("de.splatgames.aether.mixins.agent.MixinsAgent", a.getValue("Agent-Class"));
            assertEquals("true", a.getValue("Can-Redefine-Classes"));
            assertEquals("true", a.getValue("Can-Retransform-Classes"));
        }

        Path wd = Files.createTempDirectory("it-packaging");
        Path cfg = wd.resolve("mixins.yml");
        Files.writeString(cfg, """
                version: 1
                mixins:
                  - name: it-packaging
                    classes: [ "it.smoke.mixins.HeadMixin" ]
                runtime:
                  safe_mode: false
                  verify_frames: strict
                """, StandardCharsets.UTF_8);

        Path outFile = wd.resolve("out.txt");
        Process p = new ProcessBuilder(
                javaBin(),
                "-javaagent:" + agentJar,
                "-cp", testClasspath(),
                "-Daether.mixins.config=" + cfg.toAbsolutePath(),
                "it.smoke.AppMain"
        )
                .directory(wd.toFile())
                .redirectOutput(outFile.toFile())
                .redirectErrorStream(true)
                .start();

        int exit = p.waitFor();
        String out = Files.readString(outFile);
        assertEquals(0, exit, "Child failed:\n" + out);
        assertTrue(out.contains("PATCHED: ORIG"), "Expected 'PATCHED: ORIG' in output:\n" + out);
    }
}
