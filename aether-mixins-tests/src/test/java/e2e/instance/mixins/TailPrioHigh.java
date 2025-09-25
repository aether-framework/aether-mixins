package e2e.instance.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;

@Mixin(targets = "e2e.instance.TailPrioTarget", priority = 1000)
public abstract class TailPrioHigh {
    @Inject(method = "sum(II)I", at = Inject.At.TAIL, id = "tail-hp")
    private void onTail(final int a, final int b) {
        System.out.println("TAIL-HP");
    }
}
