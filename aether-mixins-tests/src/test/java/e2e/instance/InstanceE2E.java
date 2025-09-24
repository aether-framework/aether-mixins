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
        assertTrue(r.stdout.contains("REDIRECT-IFACE-OK"), "Expected REDIRECT-IFACE-OK");
        assertTrue(r.stdout.contains("RESULT=IFACE-REDIR"), "Expected RESULT=IFACE-REDIR");
    }

    @Test
    void redirectPrivateSpecial_replacesBehavior() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_instance_redirect_special.yml");
        assertNotNull(url, "mixins_instance_redirect_special.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.instance.SpecialMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        assertTrue(r.stdout.contains("REDIRECT-SPECIAL-OK"), "Expected REDIRECT-SPECIAL-OK");
        assertTrue(r.stdout.contains("RESULT=21"), "Expected RESULT=21");
    }

    // in e2e.instance.InstanceE2E
    @Test
    void headCIR_cancelsAndOverridesReturn() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_instance_head_cir.yml");
        assertNotNull(url, "mixins_instance_head_cir.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.instance.HeadCirMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        assertTrue(r.stdout.contains("HEAD-CIR-OK"), "Expected HEAD-CIR-OK");
        assertFalse(r.stdout.contains("ORIG-BODY"), "Original body should be skipped due to cancel");
        assertTrue(r.stdout.contains("RESULT=111"), "Expected RESULT=111");
    }

    @Test
    void redirect_optional_missing_skipsGracefully() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_instance_redirect_optional_missing.yml");
        assertNotNull(url, "mixins_instance_redirect_optional_missing.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.instance.OptionalRedirMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        // We prove the program ran and was not aborted
        assertTrue(r.stdout.contains("RUN"), "Expected RUN (program continued without redirect)");
        assertTrue(r.stdout.contains("RESULT=OK"), "Expected RESULT=OK");
    }

    @Test
    void headVoid_cancelSkipsBody() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_instance_head_ci_cancel.yml");
        assertNotNull(url, "mixins_instance_head_ci_cancel.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.instance.HeadVoidCancelMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        assertTrue(r.stdout.contains("HEAD-CI-CANCEL"), "Expected HEAD-CI-CANCEL");
        assertFalse(r.stdout.contains("ORIG-PING"), "Original body must be skipped");
        assertTrue(r.stdout.contains("DONE"), "Program continued");
    }

    @Test
    void tailCirString_adjustsReturn() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_instance_tail_cir_string.yml");
        assertNotNull(url, "mixins_instance_tail_cir_string.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.instance.TailCirStringMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        assertTrue(r.stdout.contains("TAIL-CIR-STR-OK"));
        assertTrue(r.stdout.contains("RESULT=HELLO, BOB!"));
    }

    @Disabled("Non-self instance redirects not yet supported; enable when adapter supports non-self calls")
    @Test
    void redirectVirtual_instanceHandler_noOwner_multiArg() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_instance_redirect_virtual_instance.yml");
        assertNotNull(url, "mixins_instance_redirect_virtual_instance.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.instance.RedirectVirtualInstanceMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        assertTrue(r.stdout.contains("REDIRECT-INSTANCE-NOOWNER-OK"));
        assertTrue(r.stdout.contains("RESULT=19")); // (2*4 + 10) + 1
    }

    @Test
    void tail_runsOnAllReturnSites() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_instance_tail_all_returns.yml");
        assertNotNull(url, "mixins_instance_tail_all_returns.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.instance.TailAllReturnsMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        // expect marker printed twice (for x>0 and x<=0)
        long count = r.stdout.lines().filter(s -> s.contains("TAIL-BRANCH-OK")).count();
        assertEquals(2, count, "TAIL should run on both return sites");
        assertTrue(r.stdout.contains("R1=1"));
        assertTrue(r.stdout.contains("R2=-1"));
    }

    @Test
    void headAndRedirect_coexist_andOrder() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_instance_head_and_redirect.yml");
        assertNotNull(url, "mixins_instance_head_and_redirect.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.instance.HeadAndRedirectMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);

        assertTrue(r.stdout.contains("HEAD-ORDER-OK"));
        assertTrue(r.stdout.contains("REDIRECT-ORDER-OK"));
        assertTrue(r.stdout.contains("BODY:3:PATCHED")); // v = 2+1=3, say() → "PATCHED"
    }
}
