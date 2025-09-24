package e2e.instance;

import de.splatgames.aether.mixins.testkit.JvmRunner;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class InstanceE2E {

    @Test
    void injectInstanceMethod() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_instance_inject_method.yml");
        assertNotNull(url, "mixins_instance_inject_method.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.instance.HeadInstanceMethodMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        var out = r.stdout.replaceAll("\\s+", " ").trim();
        assertTrue(out.contains("INJECT-SUCCESS"), () -> "Expected INJECT-SUCCESS in output\n" + r.stdout + r.stderr);
        assertTrue(out.contains("ORIG-SUCCESS"), () -> "Expected ORIG-SUCCESS in output\n" + r.stdout);
    }

    @Test
    void instanceShadowMutableUnique_applied_shadowPrefix() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_instance_count.yml");
        assertNotNull(url, "mixins_instance_count.yml not found");
        String cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.instance.CounterMain", List.of(), Map.of("aether.mixins.config", cfg));

        assertEquals(0, r.exitCode, () ->
                "Exit: " + r.exitCode + "\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
        assertTrue(r.stdout.contains("RESULT=12"),
                () -> "Expected RESULT=12\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
    }

    @Test
    void instanceShadowMutableUnique_applied_noPrefix() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_instance_count_noprefix.yml");
        assertNotNull(url, "mixins_instance_count_noprefix.yml not found");
        String cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.instance.CounterMain", List.of(), Map.of("aether.mixins.config", cfg));

        assertEquals(0, r.exitCode, r.stderr);
        assertTrue(r.stdout.contains("RESULT=12"),
                () -> "Expected RESULT=12\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
    }

    @Test
    void instanceShadowMutableUnique_applied_customPrefix() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_instance_count_customprefix.yml");
        assertNotNull(url, "mixins_instance_count_customprefix.yml not found");
        String cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.instance.CounterMain", List.of(), Map.of("aether.mixins.config", cfg));

        assertEquals(0, r.exitCode, r.stderr);
        assertTrue(r.stdout.contains("RESULT=12"),
                () -> "Expected RESULT=12\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
    }

    @Test
    void instanceShadow_optional_missing_neutralized() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_instance_optional.yml");
        assertNotNull(url, "mixins_instance_optional.yml not found");
        String cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.instance.OptionalMain", List.of(), Map.of("aether.mixins.config", cfg));

        assertEquals(0, r.exitCode, r.stderr);
        // expected behavior: no crash, output proves execution continued
        assertTrue(r.stdout.contains("OPTIONAL-OK"),
                () -> "Expected OPTIONAL-OK\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
    }

    @Test
    void unique_hook_collision_renamed_and_invoked() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_instance_unique_collision.yml");
        assertNotNull(url, "mixins_instance_unique_collision.yml not found");
        String cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.instance.UniqueCollisionMain", List.of(), Map.of("aether.mixins.config", cfg));

        assertEquals(0, r.exitCode, r.stderr);
        // Injection prints, and the @Unique hook body is called from within the injector
        assertTrue(r.stdout.contains("INJECT-CALL"),
                () -> "Expected INJECT-CALL\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
        assertTrue(r.stdout.contains("UNIQUE-HOOK"),
                () -> "Expected UNIQUE-HOOK (renamed private copy executed)\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
        assertTrue(r.stdout.contains("ORIG-CALL"),
                () -> "Expected ORIG-CALL\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
    }
}
