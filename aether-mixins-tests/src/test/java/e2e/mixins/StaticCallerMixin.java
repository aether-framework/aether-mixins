package e2e.mixins;

import de.splatgames.aether.mixins.core.api.Mixin;
import de.splatgames.aether.mixins.core.api.Redirect;

@Mixin(targets = "e2e.StaticCallerMain", priority = 100)
public final class StaticCallerMixin {

    @Redirect(
        method    = "main([Ljava/lang/String;)V",
        callOwner = "e2e/StaticCaller",
        callName  = "hello",
        callDesc  = "()Ljava/lang/String;",
        kind      = Redirect.InvokeKind.INVOKESTATIC
    )
    public static String redirect_static_hello() {
        return "PATCHED-S";
    }
}
