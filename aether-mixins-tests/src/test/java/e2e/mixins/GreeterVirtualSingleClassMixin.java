package e2e.mixins;

import de.splatgames.aether.mixins.core.api.Mixin;
import de.splatgames.aether.mixins.core.api.Redirect;

@Mixin(targets = "e2e.Greeter", priority = 100)
public final class GreeterVirtualSingleClassMixin {
    @Redirect(
            method = "greetVirtualSingle()Ljava/lang/String;",
            callOwner = "e2e/Greeter",
            callName = "ping",
            callDesc = "()Ljava/lang/String;",
            kind = Redirect.InvokeKind.INVOKEVIRTUAL,
            ordinal = 0
    )
    public static String replacePing(final e2e.Greeter self) {
        return "PATCHED-SINGLE-V";
    }
}
