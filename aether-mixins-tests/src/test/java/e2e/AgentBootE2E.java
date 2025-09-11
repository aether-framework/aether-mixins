package e2e;

import de.splatgames.aether.mixins.testkit.JvmRunner;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AgentBootE2E {
    @Test
    void childJvmStartsWithAgent() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_run.yml");
        assertNotNull(url, "Test resource mixins_run.yml not found");
        String cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var props = Map.of("aether.mixins.config", cfg);
        JvmRunner.Result r = JvmRunner.runWithAgent("e2e.GreeterMain", List.of(), props);
        assertEquals(0, r.exitCode, () ->
                "Exit: " + r.exitCode + "\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr
        );
        assertTrue(r.stdout.contains("ORIGINAL"), "Program should run and print ORIGINAL (pre-patch)");
    }

    @Test
    void redirectApplied() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_patched.yml");
        assertNotNull(url, "Test resource mixins_patched.yml not found");
        String cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var props = Map.of("aether.mixins.config", cfg);
        JvmRunner.Result r = JvmRunner.runWithAgent("e2e.GreeterMain", List.of(), props);

        assertEquals(0, r.exitCode, () ->
                "Exit: " + r.exitCode + "\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
        assertTrue(r.stdout.contains("PATCHED"), () ->
                "Output should contain PATCHED (post-patch)\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
    }

    @Test
    void injectHeadApplied() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_inject_head.yml");
        assertNotNull(url);
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();
        var r = JvmRunner.runWithAgent("e2e.GreeterMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, () -> "Exit: " + r.exitCode + "\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
        assertTrue(r.stdout.contains("PATCHED: "), () -> "Expected 'PATCHED: ' in output\n--- STDOUT ---\n" + r.stdout);
    }

    @Test
    void injectTailApplied() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_inject_tail.yml");
        assertNotNull(url);
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();
        var r = JvmRunner.runWithAgent("e2e.GreeterMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, () -> "Exit: " + r.exitCode + "\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
        assertTrue(r.stdout.contains("TAIL PATCHED!"), () -> "Expected 'TAIL PATCHED!' in output\n--- STDOUT ---\n" + r.stdout);
    }

    @Test
    void redirectOrdinalSecondCall() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_ordinal.yml");
        assertNotNull(url, "mixins_ordinal.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.GreeterTwiceMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, () -> "Exit: " + r.exitCode + "\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);

        assertTrue(r.stdout.contains("ORIGINAL PATCHED-2"),
                () -> "Expected 'ORIGINAL PATCHED-2'\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
    }

    @Test
    void injectHeadPriorityOrdering() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_priority_head.yml");
        assertNotNull(url, "mixins_priority_head.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.GreeterMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, () -> "Exit: " + r.exitCode + "\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);

        String out = r.stdout.replaceAll("\\s+", "");
        assertTrue(out.contains("[LOW][HIGH]ORIGINAL"),
                () -> "Expected '[LOW][HIGH]ORIGINAL' in order\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
    }

    @Test
    void redirectInvokeVirtualApplied() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_virtual.yml");
        assertNotNull(url);
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.GreeterVirtualMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, () -> "Exit: " + r.exitCode + "\n--- STDOUT ---\n" + r.stdout);
        assertTrue(r.stdout.contains("PATCHED-V"), () ->
                "Expected 'PATCHED-V' in output\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
    }

    @Test
    void redirectInvokeVirtualSingleClassApplied() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_virtual_single_class.yml");
        assertNotNull(url);
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.GreeterVirtualMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, () -> "Exit: " + r.exitCode + "\n--- STDOUT ---\n" + r.stdout);
        assertTrue(r.stdout.contains("PATCHED-SINGLE-V"), () ->
                "Expected 'PATCHED-SINGLE-V' in output\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
    }

    @Test
    void redirectInvokeInterfaceApplied() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_iface.yml");
        assertNotNull(url);
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.UseInterfaceMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode);
        assertTrue(r.stdout.contains("PATCHED-IF"), () ->
                "Expected 'PATCHED-IF' in output\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
    }

    @Test
    void optionalMissingCallSiteInSafeModeKeepsOriginal() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_optional.yml");
        assertNotNull(url);
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.GreeterMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode);
        assertTrue(r.stdout.contains("ORIGINAL"),
                () -> "Safe mode + optional=true should leave behavior unchanged\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
    }

    @Test
    void injectCtorHeadAndTail() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_ctor.yml");
        assertNotNull(url);
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();
        var r = JvmRunner.runWithAgent("e2e.CtorMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, () -> r.stderr);
        String out = r.stdout.replaceAll("\\s+", "");
        assertTrue(out.contains("[HEAD]"), out);
        assertTrue(out.contains("[TAIL]"), out);
        assertTrue(out.contains("CTOR:X"), out);
    }

    @Test
    void safeModeOffCausesFailureOnMissingCallSite() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_strict_fail.yml");
        assertNotNull(url);
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();
        var r = JvmRunner.runWithAgent("e2e.GreeterMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertTrue(r.exitCode != 0, () ->
                "Expected non-zero exit when safe_mode=false and redirect fails\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
    }

    @Test
    void invokestaticApplied() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_static.yml");
        assertNotNull(url, "mixins_static.yml not found");
        var cfg = java.nio.file.Paths.get(url.toURI()).toString();

        var r = JvmRunner.runWithAgent("e2e.StaticCallerMain", List.of(),
                Map.of("aether.mixins.config", cfg));

        assertEquals(0, r.exitCode, r.stderr);
        assertTrue(r.stdout.contains("PATCHED-S"),
                () -> "Expected 'PATCHED-S'\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
    }

    @Test
    void invokespecialPrivateApplied() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_special_private.yml");
        assertNotNull(url, "mixins_special_private.yml not found");
        var cfg = java.nio.file.Paths.get(url.toURI()).toString();

        var r = JvmRunner.runWithAgent(
                "e2e.PrivateSpecialMain", List.of(),
                Map.of("aether.mixins.config", cfg)
        );

        assertEquals(0, r.exitCode, r.stderr);
        assertTrue(r.stdout.contains("PATCHED-PRIV"),
                () -> "Expected 'PATCHED-PRIV'\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
    }

    @Test
    void invokespecialSuperCallApplied() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_super_special.yml");
        var cfg = java.nio.file.Paths.get(url.toURI()).toString();
        var r = JvmRunner.runWithAgent("e2e.SubSuperMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        assertTrue(r.stdout.contains("PATCHED-SUPER"), r.stdout);
    }

    @Test
    void injectTailPriorityOrdering() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_tail_prio.yml");
        var cfg = java.nio.file.Paths.get(url.toURI()).toString();
        var r = JvmRunner.runWithAgent("e2e.TailOrderMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        assertTrue(r.stdout.contains("ORIG[HIGH][LOW]"),
                () -> "Expected ORIG[HIGH][LOW], got: " + r.stdout);
    }

    @Test
    void redirectPriorityHighWins() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_redir_prio.yml");
        var cfg = java.nio.file.Paths.get(url.toURI()).toString();
        var r = JvmRunner.runWithAgent("e2e.RedirPrioMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        assertTrue(r.stdout.contains("PATCH-HIGH"),
                () -> "Higher-priority redirect should win. Out: " + r.stdout);
    }

    @Test
    void injectTailMultiReturn() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_tail_multireturn.yml");
        assertNotNull(url);
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();
        var r = JvmRunner.runWithAgent("e2e.MultiTailMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        String out = r.stdout.replaceAll("\\s+", " ").trim();
        assertTrue(out.contains("NEG[T]") && out.contains("POS[T]"), r.stdout);
    }

    @Test
    void redirectAndInjectOrder() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_order_combo.yml");
        assertNotNull(url);
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.OrderPlayMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);

        String out = r.stdout.replace("\r", "");
        String[] lines = out.split("\n");
        assertTrue(lines[0].contains("[HEAD]BODY-[TAIL]"),
                () -> "Expected first line to contain [HEAD]BODY-[TAIL], got: " + lines[0]);
        assertTrue(lines.length > 1 && lines[1].contains("REDIR"),
                () -> "Expected second line to contain REDIR, got: " + out);
    }

    @Test
    void tailInjectWithTryCatchStrictFrames() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_frames_trycatch.yml");
        assertNotNull(url);
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.FrameCaseMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);

        String out = r.stdout.replaceAll("\\s+", " ").trim();
        assertTrue(out.contains("ERR[TAIL]") && out.contains("OKDONE[TAIL]"),
                () -> "Expected ERR[TAIL] and OKDONE[TAIL], got: " + r.stdout);
    }
}
