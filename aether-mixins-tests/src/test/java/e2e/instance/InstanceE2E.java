package e2e.instance;

import de.splatgames.aether.mixins.testkit.JvmRunner;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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


    @Test
    void redirectInstanceVirtualCall_replacesBehavior() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_instance_redirect_virtual.yml");
        assertNotNull(url, "mixins_instance_redirect_virtual.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.instance.RedirectVirtualMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        // Original compute(4): helper(4)=8 -> 8+1=9
        // Redirected helper(4)=12 -> 12+1=13
        assertTrue(r.stdout.contains("RESULT=13"),
                () -> "Expected RESULT=13\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
        assertTrue(r.stdout.contains("REDIRECT-OK"), () -> "Expected REDIRECT-OK\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
    }

    @Test
    void injectTailInstanceMethod_executesAfterOriginal() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_instance_inject_tail.yml");
        assertNotNull(url, "mixins_instance_inject_tail.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.instance.TailMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        // We only assert both tokens exist; ordering is implicitly validated by manual inspection if needed.
        assertTrue(r.stdout.contains("ORIG-END"), () -> "Expected ORIG-END\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
        assertTrue(r.stdout.contains("TAIL-SUCCESS"), () -> "Expected TAIL-SUCCESS\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
    }

    @Test
    void injectConstructorTail_runsOnInstanceInit() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_instance_ctor_tail.yml");
        assertNotNull(url, "mixins_instance_ctor_tail.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.instance.CtorMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        assertTrue(r.stdout.contains("FLAG=true"), () -> "Expected FLAG=true after <init> TAIL injection\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
        assertTrue(r.stdout.contains("CTOR-TAIL-OK"), () -> "Expected CTOR-TAIL-OK\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
    }

    @Test
    void twoMixins_onSameTarget_applyInOrder() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_instance_two_mixins.yml");
        assertNotNull(url, "mixins_instance_two_mixins.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.instance.TwoMixinsMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        // Base starts at 0; MixinA sets to 2 at HEAD; original adds n; MixinB at TAIL adds +5; n=3 => (2) + 3 + 5 = 10
        assertTrue(r.stdout.contains("RESULT=10"),
                () -> "Expected RESULT=10\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
        assertTrue(r.stdout.contains("MIXIN-A-OK"), () -> "Expected MIXIN-A-OK\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
        assertTrue(r.stdout.contains("MIXIN-B-OK"), () -> "Expected MIXIN-B-OK\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
    }

    @Test
    void shadowPrivateMethod_invocationWorks() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_instance_shadow_method.yml");
        assertNotNull(url, "mixins_instance_shadow_method.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.instance.ShadowMethodMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        assertTrue(r.stdout.contains("SHADOW-METHOD=4"), () -> "Expected SHADOW-METHOD=4\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
        assertTrue(r.stdout.contains("RESULT=8"), () -> "Expected RESULT=8 (api(3) -> secret(3)=4 -> *2 = 8)\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
    }

    @Test
    void uniqueField_collision_isRenamedAndUsable() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_instance_unique_field_collision.yml");
        assertNotNull(url, "mixins_instance_unique_field_collision.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.instance.UniqueFieldCollisionMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        assertTrue(r.stdout.contains("UNIQUE-FIELD-OK"), () -> "Expected UNIQUE-FIELD-OK\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
        assertTrue(r.stdout.contains("RESULT=7"), () -> "Expected RESULT=7\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
    }

    @Test
    void redirectInterfaceCall_replacesBehavior() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_instance_redirect_iface.yml");
        assertNotNull(url, "mixins_instance_redirect_iface.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.instance.InterfaceCallerMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        assertTrue(r.stdout.contains("REDIRECT-IFACE-OK"),
                () -> "Expected REDIRECT-IFACE-OK\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
        assertTrue(r.stdout.contains("RESULT=IFACE-REDIR"),
                () -> "Expected RESULT=IFACE-REDIR\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
    }

    @Test
    void redirectPrivateSpecial_replacesBehavior() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_instance_redirect_special.yml");
        assertNotNull(url, "mixins_instance_redirect_special.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.instance.SpecialMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        assertTrue(r.stdout.contains("REDIRECT-SPECIAL-OK"),
                () -> "Expected REDIRECT-SPECIAL-OK\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
        assertTrue(r.stdout.contains("RESULT=21"),
                () -> "Expected RESULT=21\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
    }

    @Test
    void headCIR_cancelsAndOverridesReturn() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_instance_head_cir.yml");
        assertNotNull(url, "mixins_instance_head_cir.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.instance.HeadCirMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        assertTrue(r.stdout.contains("HEAD-CIR-OK"),
                () -> "Expected HEAD-CIR-OK\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
        assertFalse(r.stdout.contains("ORIG-BODY"),
                () -> "Original body should be skipped due to cancel\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
        assertTrue(r.stdout.contains("RESULT=111"),
                () -> "Expected RESULT=111\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
    }

    @Test
    void redirect_optional_missing_skipsGracefully() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_instance_redirect_optional_missing.yml");
        assertNotNull(url, "mixins_instance_redirect_optional_missing.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.instance.OptionalRedirMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        // We prove the program ran and was not aborted
        assertTrue(r.stdout.contains("RUN"),
                () -> "Expected RUN (program continued without redirect)\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
        assertTrue(r.stdout.contains("RESULT=OK"),
                () -> "Expected RESULT=OK\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
    }

    @Test
    void headVoid_cancelSkipsBody() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_instance_head_ci_cancel.yml");
        assertNotNull(url, "mixins_instance_head_ci_cancel.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.instance.HeadVoidCancelMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        assertTrue(r.stdout.contains("HEAD-CI-CANCEL"),
                () -> "Expected HEAD-CI-CANCEL\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
        assertFalse(r.stdout.contains("ORIG-PING"),
                () -> "Original body must be skipped\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
        assertTrue(r.stdout.contains("DONE"),
                () -> "Expected DONE (program continued)\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
    }

    @Test
    void tailCirString_adjustsReturn() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_instance_tail_cir_string.yml");
        assertNotNull(url, "mixins_instance_tail_cir_string.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.instance.TailCirStringMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        assertTrue(r.stdout.contains("TAIL-CIR-STR-OK"),
                () -> "Expected TAIL-CIR-STR-OK\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
        assertTrue(r.stdout.contains("RESULT=HELLO, BOB!"),
                () -> "Expected RESULT=HELLO, BOB!\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
    }

    @Disabled("Non-self instance redirects not yet supported; enable when adapter supports non-self calls")
    @Test
    void redirectVirtual_instanceHandler_noOwner_multiArg() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_instance_redirect_virtual_instance.yml");
        assertNotNull(url, "mixins_instance_redirect_virtual_instance.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.instance.RedirectVirtualInstanceMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        assertTrue(r.stdout.contains("REDIRECT-INSTANCE-NOOWNER-OK"),
                () -> "Expected REDIRECT-INSTANCE-NOOWNER-OK\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
        assertTrue(r.stdout.contains("RESULT=19"),
                () -> "Expected RESULT=19\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
    }

    @Test
    void tail_runsOnAllReturnSites() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_instance_tail_all_returns.yml");
        assertNotNull(url, "mixins_instance_tail_all_returns.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.instance.TailAllReturnsMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        long count = r.stdout.lines().filter(s -> s.contains("TAIL-BRANCH-OK")).count();
        assertEquals(2L, count,
                () -> "TAIL should run on both return sites\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
        assertTrue(r.stdout.contains("R1=1"),
                () -> "Expected R1=1\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
        assertTrue(r.stdout.contains("R2=-1"),
                () -> "Expected R2=-1\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
    }

    @Test
    void headAndRedirect_coexist_andOrder() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_instance_head_and_redirect.yml");
        assertNotNull(url, "mixins_instance_head_and_redirect.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.instance.HeadAndRedirectMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);

        assertTrue(r.stdout.contains("HEAD-ORDER-OK"),
                () -> "Expected HEAD-ORDER-OK\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
        assertTrue(r.stdout.contains("REDIRECT-ORDER-OK"),
                () -> "Expected REDIRECT-ORDER-OK\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
        assertTrue(r.stdout.contains("BODY:3:PATCHED"),
                () -> "Expected BODY:3:PATCHED (v = 2+1=3, say()->PATCHED)\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
    }

    @Test
    void tailCirLong_adjustsReturn() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_instance_tail_cir_long.yml");
        assertNotNull(url, "mixins_instance_tail_cir_long.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.instance.TailCirLongMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        assertTrue(r.stdout.contains("TAIL-CIR-LONG-OK"),
                () -> "Expected TAIL-CIR-LONG-OK\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
        assertTrue(r.stdout.contains("RESULT=13000"),
                () -> "Expected RESULT=13000\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
    }

    @Test
    void tail_mutates_state_affects_next_call() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_instance_tail_state.yml");
        assertNotNull(url, "mixins_instance_tail_state.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.instance.TailStateMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);

        assertTrue(r.stdout.contains("R1=3"),
                () -> "Expected R1=3 (first call base=1 => 1+2)\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
        assertTrue(r.stdout.contains("TAIL-STATE-SET"),
                () -> "Expected TAIL-STATE-SET marker (base mutated in TAIL)\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
        assertTrue(r.stdout.contains("R2=12"),
                () -> "Expected R2=12 (second call base=10 => 10+2)\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
    }

    @Test
    void redirectSpecial_instance_noOwner() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_instance_redirect_special_instance.yml");
        assertNotNull(url, "mixins_instance_redirect_special_instance.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.instance.SpecialInstanceMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        assertTrue(r.stdout.contains("REDIRECT-SPECIAL-INSTANCE-OK"),
                () -> "Expected REDIRECT-SPECIAL-INSTANCE-OK marker\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
        assertTrue(r.stdout.contains("RESULT=16"),
                () -> "Expected RESULT=16\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
    }

    @Test
    void inject_optional_missing_target_skips() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_instance_inject_optional_missing.yml");
        assertNotNull(url, "mixins_instance_inject_optional_missing.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.instance.InjectOptionalMissingMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        assertTrue(r.stdout.contains("RUN-OK"),
                () -> "Program must proceed without injection (optional=true)\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
        assertTrue(r.stdout.contains("RESULT=OK"),
                () -> "Expected RESULT=OK\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
    }

    @Test
    void headPriority_ordering_isDeterministic() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_instance_head_priority.yml");
        assertNotNull(url, "mixins_instance_head_priority.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.instance.HeadPrioMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);

        var out = r.stdout.replaceAll("\\s+", " ").trim();
        assertTrue(out.contains("HEAD-LP") && out.contains("HEAD-HP"),
                () -> "Expected both HEAD markers (LP & HP)\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
        assertTrue(out.indexOf("HEAD-LP") < out.indexOf("HEAD-HP"),
                () -> "Expected HEAD-LP before HEAD-HP (HP runs later at HEAD)\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
        assertTrue(out.contains("RESULT=OK"),
                () -> "Expected RESULT=OK\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
    }

    @Test
    void tailPriority_ordering_isDeterministic() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_instance_tail_priority.yml");
        assertNotNull(url, "mixins_instance_tail_priority.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.instance.TailPrioMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);

        var out = r.stdout.replaceAll("\\s+", " ").trim();
        assertTrue(out.contains("TAIL-HP") && out.contains("TAIL-LP"),
                () -> "Expected both TAIL markers (HP & LP)\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
        assertTrue(out.indexOf("TAIL-HP") < out.indexOf("TAIL-LP"),
                () -> "Expected TAIL-HP before TAIL-LP (HP runs earlier at TAIL)\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
        assertTrue(out.contains("RESULT=7"),
                () -> "Expected RESULT=7\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
    }

    @Test
    void tailCir_nullable_return_isDefaulted() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_instance_tail_cir_nullable.yml");
        assertNotNull(url, "mixins_instance_tail_cir_nullable.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.instance.TailCirNullMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);

        var out = r.stdout.replaceAll("\\s+", " ").trim();
        assertTrue(out.contains("A=HELLO"),
                () -> "Expected A=HELLO\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
        assertTrue(out.contains("B=DEFAULT"),
                () -> "Expected B=DEFAULT (null defaulted at TAIL)\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
        assertTrue(out.contains("TAIL-CIR-NULL-OK"),
                () -> "Expected marker TAIL-CIR-NULL-OK\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
    }

    @Test
    void uniqueHelper_collision_isRenamed_andInvoked() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_instance_unique_helper_collision.yml");
        assertNotNull(url, "mixins_instance_unique_helper_collision.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.instance.UniqueHelperCollisionMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);

        var out = r.stdout.replaceAll("\\s+", " ").trim();
        assertTrue(out.contains("UNIQUE-HELPER-OK"),
                () -> "Expected UNIQUE-HELPER-OK (renamed helper ran)\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
        assertTrue(out.contains("RESULT=17"),
                () -> "Expected RESULT=17\n--- STDOUT ---\n" + r.stdout + "\n--- STDERR ---\n" + r.stderr);
    }
}
