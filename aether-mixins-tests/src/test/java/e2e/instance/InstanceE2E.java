package e2e.instance;

import de.splatgames.aether.mixins.testkit.JvmRunner;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InstanceE2E {
    // --- Inject into instance method ---
    @Test
    void injectInstanceMethod() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_inject_instance_method.yml");
        assertNotNull(url, "mixins_inject_instance_method.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.instance.InjectInstanceMethodMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        var out = r.stdout.replaceAll("\\s+", " ").trim();
        assertTrue(out.contains("INJECT-SUCCESS"), () -> "Expected INJECT-SUCCESS in output\n" + r.stdout + r.stderr);
        assertTrue(out.contains("ORIG-SUCCESS"), () -> "Expected ORIG-SUCCESS in output\n" + r.stdout);
    }

    @Test
    void instanceShadowMutableUnique_applied() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_instance_count.yml");
        assertNotNull(url, "mixins_instance_count.yml not found");
        String cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var props = Map.of("aether.mixins.config", cfg);
        var r = JvmRunner.runWithAgent("e2e.instance.CounterMain", List.of(), props);

        assertEquals(0, r.exitCode, () ->
                "Exit: " + r.exitCode + "\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);

        assertTrue(r.stdout.contains("RESULT=12"),
                () -> "Expected RESULT=12\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
    }
}
