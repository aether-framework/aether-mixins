package e2e.cicir.mixins;

import de.splatgames.aether.mixins.core.api.CallbackInfo;
import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;

@Mixin(targets = "e2e.cicir.CiTailVoidMain")
public final class TailCiSideEffectMixin {

    @Inject(
        method = "body()V",
        at = Inject.At.TAIL,
        id = "tail-ci-sideeffect"
    )
    public static void tail(final CallbackInfo ci) {
        System.out.print("[TAIL-CI]");
    }
}
