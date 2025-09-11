package e2e.mixins;

import de.splatgames.aether.mixins.core.api.Mixin;
import de.splatgames.aether.mixins.core.api.Redirect;

@Mixin(targets = "e2e.Greeter", priority = 100)
public final class GreeterOptionalBadCall {
    @Redirect(
            method = "greet()Ljava/lang/String;",
            callOwner = "e2e/Util",
            callName = "doesNotExist",
            callDesc = "()Ljava/lang/String;",
            kind = Redirect.InvokeKind.INVOKESTATIC,
            optional = true
    )
    public static String noop() {
        return "SHOULD_NOT_APPEAR";
    }
}
