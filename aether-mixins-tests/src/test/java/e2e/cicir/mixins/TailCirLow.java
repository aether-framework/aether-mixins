package e2e.cicir.mixins;

import de.splatgames.aether.mixins.core.api.CallbackInfoReturnable;
import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;

@Mixin(targets = "e2e.cicir.PriorTailMain", priority = 10) // LOW
public final class TailCirLow {
    @Inject(method = "t()Ljava/lang/String;", at = Inject.At.TAIL, id = "tail-cir-low")
    public static void tail(final CallbackInfoReturnable<String> cir) {
        cir.setReturnValue("LOW");
    }
}
