package e2e.cicir.mixins;

import de.splatgames.aether.mixins.core.api.CallbackInfo;
import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;

@Mixin(targets = "e2e.cicir.CtorCancelMain")
public final class HeadCiCtorCancelMixin {
    @Inject(method = "<init>()V", at = Inject.At.HEAD, id = "head-ci-ctor-cancel")
    public static void head(final CallbackInfo ci) {
        System.out.print("[HEAD-CI]");
        ci.cancel();
    }
}
