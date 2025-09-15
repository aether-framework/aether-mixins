package e2e.cicir.mixins;

import de.splatgames.aether.mixins.core.api.CallbackInfoReturnable;
import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;

@Mixin(targets = "e2e.cicir.HeadCirIntMain")
public final class HeadCirCancelSetMixin {
    @Inject(method = "target()I", at = Inject.At.HEAD, id = "head-cir-cancel-set")
    public static void head(final CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(42);
        cir.cancel();
    }
}
