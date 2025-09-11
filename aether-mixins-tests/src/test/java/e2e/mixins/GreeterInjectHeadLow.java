package e2e.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;

@Mixin(targets = "e2e.Greeter", priority = 10)
public final class GreeterInjectHeadLow {
    @Inject(
            method = "greet()Ljava/lang/String;",
            at = Inject.At.HEAD,
            id = "head-low"
    )
    public static void low() {
        e2e.Util.log("[LOW]");
    }
}
