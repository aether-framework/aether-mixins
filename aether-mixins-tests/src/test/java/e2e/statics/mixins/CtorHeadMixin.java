package e2e.statics.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;
import e2e.statics.Util;

@Mixin(targets = "e2e.statics.CtorDemo", priority = 50)
public final class CtorHeadMixin {
    @Inject(
            method = "<init>(Ljava/lang/String;)V",
            at = Inject.At.HEAD,
            id = "ctor-head"
    )
    public static void head() {
        Util.log("[HEAD]");
    }
}