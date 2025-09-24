package e2e.instance.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;
import de.splatgames.aether.mixins.core.api.CallbackInfoReturnable;
import e2e.instance.HeadCirTarget;

@Mixin(HeadCirTarget.class)
public abstract class HeadCirMixin {

    @Inject(method = "compute(I)I", at = Inject.At.HEAD, id = "head-cir", cancellable = true)
    private void onHead(final int x, final CallbackInfoReturnable<Integer> cir) {
        System.out.println("HEAD-CIR-OK");
        cir.setReturnValue(111);
        // cancel so the original body is skipped
        cir.cancel();
    }
}
