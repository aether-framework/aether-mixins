package e2e.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;

@Mixin(targets = "e2e.CtorDemo", priority = 100)
public final class CtorTailMixin {
    @Inject(
            method = "<init>(Ljava/lang/String;)V",
            at = Inject.At.TAIL,
            id = "ctor-tail"
    )
    public static void tail() {
        e2e.Util.log("[TAIL]");
    }
}
