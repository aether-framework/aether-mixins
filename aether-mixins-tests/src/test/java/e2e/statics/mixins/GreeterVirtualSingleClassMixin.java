package e2e.statics.mixins;

import de.splatgames.aether.mixins.core.api.Mixin;
import de.splatgames.aether.mixins.core.api.Redirect;
import e2e.statics.Greeter;

@Mixin(targets = "e2e.statics.Greeter", priority = 100)
public final class GreeterVirtualSingleClassMixin {
    @Redirect(
            method = "greetVirtualSingle()Ljava/lang/String;",
            callOwner = "e2e/statics/Greeter",
            callName = "ping",
            callDesc = "()Ljava/lang/String;",
            kind = Redirect.InvokeKind.INVOKEVIRTUAL,
            ordinal = 0
    )
    public static String replacePing(final Greeter self) {
        return "PATCHED-SINGLE-V";
    }
}
