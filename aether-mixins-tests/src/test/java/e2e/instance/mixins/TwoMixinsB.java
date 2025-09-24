package e2e.instance.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;
import de.splatgames.aether.mixins.core.api.CallbackInfoReturnable;
import e2e.instance.TwoMixinsTarget;

@Mixin(TwoMixinsTarget.class)
public abstract class TwoMixinsB {

    @Inject(method = "work(I)I", at = Inject.At.TAIL)
    private void tail(final int n, final CallbackInfoReturnable<Integer> cir) {
        // Adjust the computed return value at TAIL
        cir.setReturnValue(cir.getReturnValue() + 5);
        System.out.println("MIXIN-B-OK");
    }
}
