package e2e.instance.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;
import de.splatgames.aether.mixins.core.api.CallbackInfoReturnable;
import e2e.instance.TailCirNullTarget;

@Mixin(TailCirNullTarget.class)
public abstract class TailCirNullMixin {

    @Inject(method = "maybe(I)Ljava/lang/String;", at = Inject.At.TAIL)
    private void onTail(int n, CallbackInfoReturnable<String> cir) {
        if (cir.getReturnValue() == null) {
            System.out.println("TAIL-CIR-NULL-OK");
            cir.setReturnValue("DEFAULT");
        } else {
            System.out.println("TAIL-CIR-NULL-OK");
        }
    }
}
