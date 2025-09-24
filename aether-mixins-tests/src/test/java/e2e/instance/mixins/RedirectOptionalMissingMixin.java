package e2e.instance.mixins;

import de.splatgames.aether.mixins.core.api.Mixin;
import de.splatgames.aether.mixins.core.api.Redirect;
import e2e.instance.OptionalRedirTarget;

@Mixin(OptionalRedirTarget.class)
public abstract class RedirectOptionalMissingMixin {

    @Redirect(
        method     = "run()Ljava/lang/String;",
        callOwner  = "e2e/instance/NoSuchUtil",        // no such owner/call → no match
        callName   = "noop",
        callDesc   = "()V",
        kind       = Redirect.InvokeKind.AUTO,
        optional   = true,
        id         = "optional-missing"
    )
    private static void neverCalled() {
        // not expected to be called
        System.out.println("SHOULD-NOT-PRINT");
    }
}
