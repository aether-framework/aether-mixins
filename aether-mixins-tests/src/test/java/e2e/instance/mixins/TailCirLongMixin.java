package e2e.instance.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;
import de.splatgames.aether.mixins.core.api.CallbackInfoReturnable;
import e2e.instance.TailCirLongTarget;

@Mixin(TailCirLongTarget.class)
public abstract class TailCirLongMixin {

    @Inject(method = "calc(I)J", at = Inject.At.TAIL)
    private void onTail(final int a, final CallbackInfoReturnable<Long> cir) {
        System.out.println("TAIL-CIR-LONG-OK");
        cir.setReturnValue(cir.getReturnValue() + 1000L);
    }
}
