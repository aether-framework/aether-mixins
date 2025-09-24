package e2e.statics.mixins;

import de.splatgames.aether.mixins.core.api.Mixin;
import de.splatgames.aether.mixins.core.api.Redirect;

@Mixin(targets = "e2e.statics.Greeter", priority = 100)
public final class GreeterMixin {

    @Redirect(
            method = "greet()Ljava/lang/String;",
            callOwner = "e2e/statics/Util",
            callName = "msg",
            callDesc = "()Ljava/lang/String;",
            kind = Redirect.InvokeKind.INVOKESTATIC,
            ordinal = 0,
            id = "greeter-msg-redirect"
    )
    public static String redirectMsg() {
        return "PATCHED";
    }
}
