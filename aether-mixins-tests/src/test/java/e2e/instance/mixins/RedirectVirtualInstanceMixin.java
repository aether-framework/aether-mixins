package e2e.instance.mixins;

import de.splatgames.aether.mixins.core.api.Mixin;
import de.splatgames.aether.mixins.core.api.Redirect;
import e2e.instance.RedirectVirtualInstanceTarget;

@Mixin(RedirectVirtualInstanceTarget.class)
public abstract class RedirectVirtualInstanceMixin {

    @Redirect(
        method    = "compute(I)I",
        callOwner = "e2e/instance/MathService",
        callName  = "mul",
        callDesc  = "(II)I",
        kind      = Redirect.InvokeKind.INVOKEVIRTUAL,
        id        = "redir-virtual-instance-mul"
    )
    private int redirectMul(final int a, final int b) {
        System.out.println("REDIRECT-INSTANCE-NOOWNER-OK");
        return a * b + 10; // boost result by 10
    }
}
