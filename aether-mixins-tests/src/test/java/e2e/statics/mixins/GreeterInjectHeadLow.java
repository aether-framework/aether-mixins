package e2e.statics.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;
import e2e.statics.Util;

@Mixin(targets = "e2e.statics.Greeter", priority = 10)
public final class GreeterInjectHeadLow {
    @Inject(
            method = "greet()Ljava/lang/String;",
            at = Inject.At.HEAD,
            id = "head-low"
    )
    public static void low() {
        Util.log("[LOW]");
    }
}
