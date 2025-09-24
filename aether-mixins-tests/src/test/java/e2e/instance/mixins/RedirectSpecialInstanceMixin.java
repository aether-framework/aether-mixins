package e2e.instance.mixins;

import de.splatgames.aether.mixins.core.api.Mixin;
import de.splatgames.aether.mixins.core.api.Redirect;
import e2e.instance.SpecialInstanceTarget;

@Mixin(SpecialInstanceTarget.class)
public abstract class RedirectSpecialInstanceMixin {

    @Redirect(
        method    = "run(I)I",
        callOwner = "e2e/instance/SpecialInstanceTarget",
        callName  = "step",
        callDesc  = "(I)I",
        kind      = Redirect.InvokeKind.INVOKESPECIAL,
        id        = "special-instance-noowner"
    )
    private int redirectStep(final int n) {
        System.out.println("REDIRECT-SPECIAL-INSTANCE-OK");
        return n * 5;
    }
}
