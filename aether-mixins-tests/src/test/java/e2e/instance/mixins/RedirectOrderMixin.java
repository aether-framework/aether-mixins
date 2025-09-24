package e2e.instance.mixins;

import de.splatgames.aether.mixins.core.api.Mixin;
import de.splatgames.aether.mixins.core.api.Redirect;
import e2e.instance.HeadAndRedirectTarget;
import e2e.instance.OrderHelper;

@Mixin(HeadAndRedirectTarget.class)
public abstract class RedirectOrderMixin {

    @Redirect(
        method    = "run(I)I",
        callOwner = "e2e/instance/OrderHelper",
        callName  = "say",
        callDesc  = "()Ljava/lang/String;",
        kind      = Redirect.InvokeKind.INVOKEVIRTUAL,
        id        = "redir-order-say"
    )
    private static String redirectSay(final OrderHelper self) {
        System.out.println("REDIRECT-ORDER-OK");
        return "PATCHED";
    }
}
