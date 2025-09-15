package e2e.cicir.mixins;

import de.splatgames.aether.mixins.core.api.CallbackInfoReturnable;
import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;

@Mixin(targets = "e2e.cicir.CirTailMain")
public final class TailCirOverrideMixin {

    @Inject(
        method = "target()Ljava/lang/String;",
        at = Inject.At.TAIL,
        id = "tail-cir-override"
    )
    public static void tail(final CallbackInfoReturnable<String> cir) {
        cir.setReturnValue("TAIL-OVERRIDE");
    }
}
