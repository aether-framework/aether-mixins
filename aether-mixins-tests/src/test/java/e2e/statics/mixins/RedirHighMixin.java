package e2e.statics.mixins;

import de.splatgames.aether.mixins.core.api.Mixin;
import de.splatgames.aether.mixins.core.api.Redirect;

@Mixin(targets = "e2e.statics.RedirPrio", priority = 200)
public final class RedirHighMixin {
    @Redirect(
            method = "call()Ljava/lang/String;",
            callOwner = "e2e/statics/Util2",
            callName = "msg",
            callDesc = "()Ljava/lang/String;",
            kind = Redirect.InvokeKind.INVOKESTATIC
    )
    public static String high() {
        return "PATCH-HIGH";
    }
}
