package e2e.statics.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;
import e2e.statics.Util;

@Mixin(targets = "e2e.statics.Greeter", priority = 100)
public final class GreeterInjectHeadMixin {
    @Inject(
            method = "greet()Ljava/lang/String;",
            at = Inject.At.HEAD,
            id = "greeter-head"
    )
    public static void addPrefix() {
        Util.log("PATCHED: ");
    }
}
