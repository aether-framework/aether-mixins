package e2e.mixins;

import de.splatgames.aether.mixins.core.api.Mixin;
import de.splatgames.aether.mixins.core.api.Redirect;

@Mixin(targets = "e2e.OrderPlay", priority = 100)
public final class OrderRedirectMixin {
    @Redirect(
            method = "run()Ljava/lang/String;",
            callOwner = "e2e/Util3",
            callName = "msg",
            callDesc = "()Ljava/lang/String;",
            kind = Redirect.InvokeKind.INVOKESTATIC
    )
    public static String redirectMsg() {
        return "REDIR";
    }
}
