package e2e.instance.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;
import de.splatgames.aether.mixins.core.api.CallbackInfoReturnable;
import e2e.instance.TailCirStringTarget;

@Mixin(TailCirStringTarget.class)
public abstract class TailCirStringMixin {

    @Inject(method = "greet(Ljava/lang/String;)Ljava/lang/String;", at = Inject.At.TAIL)
    private void onTail(final String name, final CallbackInfoReturnable<String> cir) {
        System.out.println("TAIL-CIR-STR-OK");
        cir.setReturnValue(cir.getReturnValue().toUpperCase() + "!");
    }
}
