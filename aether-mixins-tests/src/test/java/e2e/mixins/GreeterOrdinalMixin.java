package e2e.mixins;

import de.splatgames.aether.mixins.core.api.Mixin;
import de.splatgames.aether.mixins.core.api.Redirect;

@Mixin(targets = "e2e.Greeter", priority = 100)
public final class GreeterOrdinalMixin {
    @Redirect(
            method = "greetTwice()Ljava/lang/String;",
            callOwner = "e2e/Util",
            callName = "msg",
            callDesc = "()Ljava/lang/String;",
            kind = Redirect.InvokeKind.INVOKESTATIC,
            ordinal = 1,
            id = "greetTwice-second-redirect"
    )
    public static String replaceSecondCall() {
        return "PATCHED-2";
    }
}
