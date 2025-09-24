package e2e.instance.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;
import e2e.instance.TailPrioTarget;

@Mixin(targets = "e2e.instance.TailPrioTarget", priority = 100)
public abstract class TailPrioLow {
    @Inject(method = "sum(II)I", at = Inject.At.TAIL, id = "tail-lp")
    private void onTail(final int a, final int b) {
        System.out.println("TAIL-LP");
    }
}
