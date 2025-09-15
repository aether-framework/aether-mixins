package e2e.cicir.mixins;

import de.splatgames.aether.mixins.core.api.CallbackInfoReturnable;
import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;

@Mixin(targets = "e2e.cicir.TryFinallyMain")
public final class TailCirTryFinallyMixin {
    @Inject(method = "target(Z)Ljava/lang/String;", at = Inject.At.TAIL, id = "tail-cir-tryfinally")
    public static void tail(final CallbackInfoReturnable<String> cir) {
        cir.setReturnValue("OVR");
    }
}
