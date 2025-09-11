package e2e.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;

@Mixin(targets = "e2e.CtorDemo", priority = 50)
public final class CtorHeadMixin {
    @Inject(
            method = "<init>(Ljava/lang/String;)V",
            at = Inject.At.HEAD,
            id = "ctor-head"
    )
    public static void head() {
        e2e.Util.log("[HEAD]");
    }
}