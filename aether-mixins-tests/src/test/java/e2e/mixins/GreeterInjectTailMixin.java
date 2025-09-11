package e2e.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;

@Mixin(targets = "e2e.Greeter", priority = 100)
public final class GreeterInjectTailMixin {
    @Inject(
            method = "greet()Ljava/lang/String;",
            at = Inject.At.TAIL,
            id = "greeter-tail"
    )
    public static void tailMarker() {
        e2e.Util.log("TAIL PATCHED!");
    }
}
