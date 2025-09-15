package e2e.cicir.mixins;

import de.splatgames.aether.mixins.core.api.CallbackInfoReturnable;
import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;

@Mixin(targets = "e2e.cicir.PriorTailMain", priority = 100) // HIGH
public final class TailCirHigh {
    @Inject(method = "t()Ljava/lang/String;", at = Inject.At.TAIL, id = "tail-cir-high")
    public static void tail(final CallbackInfoReturnable<String> cir) {
        cir.setReturnValue("HIGH");
    }
}