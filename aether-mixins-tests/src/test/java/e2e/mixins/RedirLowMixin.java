package e2e.mixins;

import de.splatgames.aether.mixins.core.api.Mixin;
import de.splatgames.aether.mixins.core.api.Redirect;

@Mixin(targets = "e2e.RedirPrio", priority = 50)
public final class RedirLowMixin {
    @Redirect(
            method = "call()Ljava/lang/String;",
            callOwner = "e2e/Util2",
            callName = "msg",
            callDesc = "()Ljava/lang/String;",
            kind = Redirect.InvokeKind.INVOKESTATIC,
            optional = true
    )
    public static String low() {
        return "PATCH-LOW";
    }
}