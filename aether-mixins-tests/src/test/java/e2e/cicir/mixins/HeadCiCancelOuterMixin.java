package e2e.cicir.mixins;

import de.splatgames.aether.mixins.core.api.CallbackInfo;
import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;

@Mixin(targets = "e2e.cicir.NestedCancelMain")
public final class HeadCiCancelOuterMixin {
    @Inject(method = "outer()V", at = Inject.At.HEAD, id = "head-ci-cancel-outer")
    public static void head(final CallbackInfo ci) {
        System.out.print("[HEAD-CANCEL]");
        ci.cancel();
    }
}
