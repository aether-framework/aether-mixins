package e2e.mixins;

import de.splatgames.aether.mixins.core.api.Mixin;
import de.splatgames.aether.mixins.core.api.Redirect;

@Mixin(targets = "e2e.Greeter", priority = 100)
public final class BadRedirectStrictMixin {
    @Redirect(
            method = "greet()Ljava/lang/String;",
            callOwner = "e2e/Util",
            callName = "doesNotExist",
            callDesc = "()Ljava/lang/String;",
            kind = Redirect.InvokeKind.INVOKESTATIC
    )
    public static String nope() {
        return "NEVER";
    }
}
