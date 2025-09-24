package e2e.statics.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;
import e2e.statics.Util;

@Mixin(targets = "e2e.statics.CtorDemo", priority = 100)
public final class CtorTailMixin {
    @Inject(
            method = "<init>(Ljava/lang/String;)V",
            at = Inject.At.TAIL,
            id = "ctor-tail"
    )
    public static void tail() {
        Util.log("[TAIL]");
    }
}
