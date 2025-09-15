package e2e.cicir;

import de.splatgames.aether.mixins.testkit.JvmRunner;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CiCirE2E {

    // --- HEAD: CI (void) cancels early ---
    @Test
    void headCiCancelsVoidTarget() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_ci_head_void_cancel.yml");
        assertNotNull(url, "mixins_ci_head_void_cancel.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        // Target prints "ORIG-VOID" if body runs; CI should cancel -> expect "HEAD-CI-CANCELLED"
        var r = JvmRunner.runWithAgent("e2e.cicir.CiCiVoidMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        var out = r.stdout.replaceAll("\\s+", " ").trim();
        assertTrue(out.contains("HEAD-CI-CANCELLED"), () -> "Expected HEAD-CI-CANCELLED in output\n" + r.stdout);
        assertFalse(out.contains("ORIG-VOID"), () -> "Body must be skipped by CI cancel\n" + r.stdout);
    }

    // --- HEAD: CIR (non-void) cancels and returns replacement value ---
    @Test
    void headCirCancelsNonVoidReturnsReplacement() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_cir_head_nonvoid_cancel.yml");
        assertNotNull(url, "mixins_cir_head_nonvoid_cancel.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        // Target would return 7; CIR cancels and returns 42 instead.
        var r = JvmRunner.runWithAgent("e2e.cicir.CirHeadNonVoidMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        var out = r.stdout.replaceAll("\\s+", " ").trim();
        assertTrue(out.contains("RET=42"), () -> "Expected replacement return value via CIR (42)\n" + r.stdout);
        assertFalse(out.contains("RET=7"), () -> "Original value must be skipped\n" + r.stdout);
    }

    // --- TAIL: CIR overrides final return value ---
    @Test
    void tailCirOverridesReturn() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_cir_tail_override.yml");
        assertNotNull(url, "mixins_cir_tail_override.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        // Target returns "ORIG"; TAIL CIR sets "TAIL-OVERRIDE"
        var r = JvmRunner.runWithAgent("e2e.cicir.CirTailMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        var out = r.stdout.replaceAll("\\s+", " ").trim();
        assertTrue(out.contains("RET=TAIL-OVERRIDE"), () -> "Expected CIR to override the return value\n" + r.stdout);
        assertFalse(out.contains("RET=ORIG"), () -> "Original should be replaced\n" + r.stdout);
    }

    // --- TAIL: CI (void) side-effect only (no cancel) ---
    @Test
    void tailCiSideEffectOnly() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_ci_tail_sideeffect.yml");
        assertNotNull(url, "mixins_ci_tail_sideeffect.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        // Target prints "BODY-DONE"; TAIL CI appends "[TAIL-CI]" (no cancel semantics)
        var r = JvmRunner.runWithAgent("e2e.cicir.CiTailVoidMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        var out = r.stdout.replaceAll("\\s+", " ").trim();
        assertTrue(out.contains("[TAIL-CI]BODY-DONE"),
                () -> "Expected side-effect marker from CI tail\n" + r.stdout);
    }

    // --- HEAD in <init>: CI works (void only), body runs after super() ---
    @Test
    void headCiInCtor() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_ci_head_ctor.yml");
        assertNotNull(url, "mixins_ci_head_ctor.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        // Constructor prints "CTOR:BODY"; HEAD CI prints "[HEAD-CI]" after super-call
        var r = JvmRunner.runWithAgent("e2e.cicir.CtorCiMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        var out = r.stdout.replaceAll("\\s+", " ").trim();
        assertTrue(out.contains("[HEAD-CI] CTOR:BODY"), () -> "Expected CI head after <init> call\n" + r.stdout);
    }

    // --- Negative: invalid CI/CIR usage should fail (non-optional) ---
    @Test
    void invalidCiOnNonVoidFails() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_invalid_ci_on_nonvoid.yml");
        assertNotNull(url, "mixins_invalid_ci_on_nonvoid.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.cicir.CirHeadNonVoidMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertNotEquals(0, r.exitCode, () -> "Expected failure when CI used on non-void\n--- STDERR ---\n" + r.stderr);
    }

    @Test
    void invalidCirOnVoidFails() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_invalid_cir_on_void.yml");
        assertNotNull(url, "mixins_invalid_cir_on_void.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.cicir.CiCiVoidMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertNotEquals(0, r.exitCode, () -> "Expected failure when CIR used on void\n--- STDERR ---\n" + r.stderr);
    }

    @Test
    void headCiCancelsBody() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_ci_head_cancel.yml");
        assertNotNull(url, "mixins_ci_head_cancel.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.cicir.CancelVoidMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);

        var out = r.stdout.replaceAll("\\s+", " ").trim();
        assertTrue(out.contains("[HEAD]"), () -> "Expected HEAD marker\n" + r.stdout);
        assertFalse(out.contains("BODY"), () -> "Body must be skipped by CI cancel\n" + r.stdout);
    }

    @Test
    void headCiNoCancelBodyRuns() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_ci_head_nocancel.yml");
        assertNotNull(url, "mixins_ci_head_nocancel.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.cicir.CancelVoidMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);

        var out = r.stdout.replaceAll("\\s+", "").trim();
        assertTrue(out.contains("[HEAD]BODY"), () -> "Expected HEAD then BODY\n" + r.stdout);
    }

    @Test
    void headCirCancelSetsReturn() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_cir_head_cancel_set.yml");
        assertNotNull(url, "mixins_cir_head_cancel_set.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.cicir.HeadCirIntMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);

        var out = r.stdout.replaceAll("\\s+", " ").trim();
        assertTrue(out.contains("RET=42"), () -> "Expected replacement return 42\n" + r.stdout);
        assertFalse(out.contains("RET=7"), () -> "Original must be skipped\n" + r.stdout);
    }

    @Test
    void headCirCancelWithoutSetReturn() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_cir_head_cancel_noset.yml");
        assertNotNull(url, "mixins_cir_head_cancel_noset.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.cicir.HeadCirIntMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);

        var out = r.stdout.replaceAll("\\s+", "").trim();
        assertTrue(out.contains("RET=0"), () -> "Expected default int return (0) when cancelled w/o setReturn\n" + r.stdout);
        assertFalse(out.contains("RET=7"), () -> "Original must not appear\n" + r.stdout);
    }

    @Test
    void headCiCancelCtorSkipsBody() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_ci_head_ctor_cancel.yml");
        assertNotNull(url, "mixins_ci_head_ctor_cancel.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.cicir.CtorCancelMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);

        var out = r.stdout.replaceAll("\\s+", " ").trim();
        assertTrue(out.contains("[HEAD-CI]"), () -> "Expected HEAD-CI marker\n" + r.stdout);
        assertFalse(out.contains("CTOR:BODY"), () -> "Ctor body must be skipped\n" + r.stdout);
    }

    @Test
    void tailCirOverridesAcrossTryCatchFinally() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_cir_tail_tryfinally.yml");
        assertNotNull(url, "mixins_cir_tail_tryfinally.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.cicir.TryFinallyMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        var out = r.stdout.replaceAll("\\s+", "").trim();

        // Egal welcher Pfad (true/false), TAIL-CIR setzt final "OVR"
        assertTrue(out.contains("RET=OVR"), () -> "Expected overridden return via TAIL-CIR\n" + r.stdout);
        assertFalse(out.contains("RET=A") || out.contains("RET=B"), () -> "Original returns must be overridden\n" + r.stdout);
    }

    @Test
    void tailCirOverridesInSynchronizedMethod() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_cir_tail_sync.yml");
        assertNotNull(url, "mixins_cir_tail_sync.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.cicir.SyncMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        var out = r.stdout.replaceAll("\\s+", "").trim();

        assertTrue(out.contains("RET=SYNC"), () -> "Expected overridden return in synchronized method\n" + r.stdout);
        assertFalse(out.contains("RET=ORIG"), () -> "Original must be replaced\n" + r.stdout);
    }

    @Test
    void headCiCancelPreventsNestedCall() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_ci_head_cancel_outer.yml");
        assertNotNull(url, "mixins_ci_head_cancel_outer.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.cicir.NestedCancelMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        var out = r.stdout.replaceAll("\\s+", " ").trim();

        assertTrue(out.contains("[HEAD-CANCEL]"), () -> "Expected head cancel marker\n" + r.stdout);
        assertFalse(out.contains("INNER"), () -> "Inner call must not run when outer is cancelled\n" + r.stdout);
    }

    @Test
    void tailCirOverrideStaticWide() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_cir_tail_static_wide.yml");
        assertNotNull(url, "mixins_cir_tail_static_wide.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.cicir.StaticWideMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        var out = r.stdout.replaceAll("\\s+","").trim();
        assertTrue(out.contains("RET=123"), () -> "Expected overridden return via TAIL-CIR\n" + r.stdout);
    }

    @Test
    void headThisArgsCiLoadsCorrectly() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_ci_head_thisargs.yml");
        assertNotNull(url, "mixins_ci_head_thisargs.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.cicir.ThisArgsCiMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        var out = r.stdout.replaceAll("\\s+","").trim();
        assertTrue(out.contains("[HEAD:ok]BODY"), () -> "Expected HEAD side-effect before BODY\n" + r.stdout);
    }

    @Test
    void tailCirPriorityLowWins() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_cir_tail_priority.yml");
        assertNotNull(url, "mixins_cir_tail_priority.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.cicir.PriorTailMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        var out = r.stdout.replaceAll("\\s+","").trim();
        assertTrue(out.contains("RET=LOW"), () -> "Lower priority wins with current tail wrapping order\n" + r.stdout);
        assertFalse(out.contains("RET=HIGH"));
    }

    @Test
    void tailCirNullReturnRefType() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_cir_tail_null.yml");
        assertNotNull(url, "mixins_cir_tail_null.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.cicir.NullTailMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertEquals(0, r.exitCode, r.stderr);
        var out = r.stdout.replaceAll("\\s+","").trim();
        assertTrue(out.contains("RET=null"), () -> "Expected null return via TAIL-CIR\n" + r.stdout);
    }

    @Test
    void invalidThisCiOnStaticFails() throws Exception {
        var url = ClassLoader.getSystemResource("mixins_invalid_thisci_on_static.yml");
        assertNotNull(url, "mixins_invalid_thisci_on_static.yml not found");
        var cfg = Paths.get(url.toURI()).toAbsolutePath().toString();

        var r = JvmRunner.runWithAgent("e2e.cicir.BadThisCiStaticMain", List.of(), Map.of("aether.mixins.config", cfg));
        assertNotEquals(0, r.exitCode, () -> "Expected non-zero exit for THIS_CI on static\n--- STDERR ---\n" + r.stderr);
    }
}
