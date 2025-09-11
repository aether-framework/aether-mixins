package e2e.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;

@Mixin(targets = "e2e.Greeter", priority = 100)
public final class GreeterInjectHeadHigh {
    @Inject(
            method = "greet()Ljava/lang/String;",
            at = Inject.At.HEAD,
            id = "head-high"
    )
    public static void high() {
        e2e.Util.log("[HIGH]");
    }
}
