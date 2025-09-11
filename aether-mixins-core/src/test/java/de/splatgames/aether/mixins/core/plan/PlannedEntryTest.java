package de.splatgames.aether.mixins.core.plan;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Redirect;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlannedEntryTest {

    private static void invokeInjectWithNullAt() throws Throwable {
        Method m = PlannedEntry.class.getMethod(
                "inject",
                String.class, String.class, boolean.class, boolean.class, Inject.At.class
        );
        try {
            m.invoke(null, "m()V", "id", false, true, /* at */ null);
        } catch (InvocationTargetException ite) {
            throw ite.getCause();
        }
    }

    private static void invokeRedirectWithNullOwner() throws Throwable {
        Method m = PlannedEntry.class.getMethod(
                "redirect",
                String.class, String.class, boolean.class, boolean.class,
                String.class, String.class, String.class, Redirect.InvokeKind.class, int.class
        );
        try {
            m.invoke(null, "m()V", "id", false, true,
                    /* owner */ null, "f", "()V", Redirect.InvokeKind.AUTO, -1);
        } catch (InvocationTargetException ite) {
            throw ite.getCause();
        }
    }

    private static void invokeRedirectWithNullName() throws Throwable {
        Method m = PlannedEntry.class.getMethod(
                "redirect",
                String.class, String.class, boolean.class, boolean.class,
                String.class, String.class, String.class, Redirect.InvokeKind.class, int.class
        );
        try {
            m.invoke(null, "m()V", "id", false, true,
                    "pkg/Util", /* name */ null, "()V", Redirect.InvokeKind.AUTO, -1);
        } catch (InvocationTargetException ite) {
            throw ite.getCause();
        }
    }

    @Test
    void inject_factory_builds_valid_entry() {
        PlannedEntry e = PlannedEntry.inject("m()V", "id", false, true, Inject.At.HEAD);
        assertThat(e.getKind()).isEqualTo(PlannedEntry.Kind.INJECT);
        assertThat(e.getMethod()).isEqualTo("m()V");
        assertThat(e.getAt()).isEqualTo(Inject.At.HEAD);
        assertThat(e.getCallOwner()).isNull();
        assertThat(e.getInvokeKind()).isEqualTo(Redirect.InvokeKind.AUTO);
    }

    @Test
    void redirect_factory_builds_valid_entry() {
        PlannedEntry e = PlannedEntry.redirect("m(I)I", "id", true, true,
                "pkg/Util", "f", "(I)I", Redirect.InvokeKind.INVOKESTATIC, 0);

        assertThat(e.getKind()).isEqualTo(PlannedEntry.Kind.REDIRECT);
        assertThat(e.getCallOwner()).isEqualTo("pkg/Util");
        assertThat(e.getCallName()).isEqualTo("f");
        assertThat(e.getCallDesc()).isEqualTo("(I)I");
        assertThat(e.getInvokeKind()).isEqualTo(Redirect.InvokeKind.INVOKESTATIC);
        assertThat(e.getOrdinal()).isEqualTo(0);
        assertThat(e.getAt()).isNull();
    }

    @Test
    void inject_factory_rejects_null_at_via_reflection() {
        assertThatThrownBy(PlannedEntryTest::invokeInjectWithNullAt)
                .isInstanceOfAny(NullPointerException.class, IllegalArgumentException.class);
    }

    @Test
    void redirect_factory_rejects_missing_call_fields_via_reflection() {
        assertThatThrownBy(PlannedEntryTest::invokeRedirectWithNullOwner)
                .isInstanceOfAny(NullPointerException.class, IllegalArgumentException.class);

        assertThatThrownBy(PlannedEntryTest::invokeRedirectWithNullName)
                .isInstanceOfAny(NullPointerException.class, IllegalArgumentException.class);
    }
}
