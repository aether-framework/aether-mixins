package e2e.instance.mixins;

import de.splatgames.aether.mixins.core.api.Mixin;
import de.splatgames.aether.mixins.core.api.Redirect;
import e2e.instance.InterfaceCaller;
import e2e.instance.Greeter;

@Mixin(InterfaceCaller.class)
public abstract class RedirectInterfaceMixin {

    @Redirect(
        method    = "call()Ljava/lang/String;",
        callOwner = "e2e/instance/Greeter",
        callName  = "greet",
        callDesc  = "(I)Ljava/lang/String;",
        kind      = Redirect.InvokeKind.INVOKEINTERFACE,
        id        = "iface-redirect"
    )
    private static String redirectGreet(final Greeter self, final int n) {
        System.out.println("REDIRECT-IFACE-OK");
        return "IFACE-REDIR";
    }
}
