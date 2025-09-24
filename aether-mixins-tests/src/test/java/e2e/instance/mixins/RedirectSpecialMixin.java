package e2e.instance.mixins;

import de.splatgames.aether.mixins.core.api.Mixin;
import de.splatgames.aether.mixins.core.api.Redirect;
import e2e.instance.SpecialTarget;

@Mixin(SpecialTarget.class)
public abstract class RedirectSpecialMixin {

    @Redirect(
        method    = "run(I)I",
        callOwner = "e2e/instance/SpecialTarget",
        callName  = "secret",
        callDesc  = "(I)I",
        kind      = Redirect.InvokeKind.INVOKESPECIAL,
        id        = "special-redirect"
    )
    private static int redirectSecret(final SpecialTarget self, final int x) {
        System.out.println("REDIRECT-SPECIAL-OK");
        return x + 5;
    }
}
