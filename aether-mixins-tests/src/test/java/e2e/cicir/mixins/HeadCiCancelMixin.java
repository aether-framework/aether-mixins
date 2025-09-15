package e2e.cicir.mixins;

import de.splatgames.aether.mixins.core.api.CallbackInfo;
import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;

@Mixin(targets = "e2e.cicir.CancelVoidMain")
public final class HeadCiCancelMixin {
    @Inject(method = "body()V", at = Inject.At.HEAD, id = "head-ci-cancel")
    public static void head(final CallbackInfo ci) {
        System.out.print("[HEAD]");
        ci.cancel();
    }
}
