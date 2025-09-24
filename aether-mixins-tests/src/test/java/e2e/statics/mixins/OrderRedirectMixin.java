package e2e.statics.mixins;

import de.splatgames.aether.mixins.core.api.Mixin;
import de.splatgames.aether.mixins.core.api.Redirect;

@Mixin(targets = "e2e.statics.OrderPlay", priority = 100)
public final class OrderRedirectMixin {
    @Redirect(
            method = "run()Ljava/lang/String;",
            callOwner = "e2e/statics/Util3",
            callName = "msg",
            callDesc = "()Ljava/lang/String;",
            kind = Redirect.InvokeKind.INVOKESTATIC
    )
    public static String redirectMsg() {
        return "REDIR";
    }
}
