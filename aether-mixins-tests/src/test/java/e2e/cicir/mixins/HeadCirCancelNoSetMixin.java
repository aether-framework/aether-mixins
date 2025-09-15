package e2e.cicir.mixins;

import de.splatgames.aether.mixins.core.api.CallbackInfoReturnable;
import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;

@Mixin(targets = "e2e.cicir.HeadCirIntMain")
public final class HeadCirCancelNoSetMixin {
    @Inject(method = "target()I", at = Inject.At.HEAD, id = "head-cir-cancel-noset")
    public static void head(final CallbackInfoReturnable<Integer> cir) {
        cir.cancel();
    }
}
