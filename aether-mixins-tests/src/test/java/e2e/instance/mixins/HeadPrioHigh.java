package e2e.instance.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;
import e2e.instance.HeadPrioTarget;

@Mixin(targets = "e2e.instance.HeadPrioTarget", priority = 1000)
public abstract class HeadPrioHigh {
    @Inject(method = "go()Ljava/lang/String;", at = Inject.At.HEAD, id = "head-hp")
    private void onHead() {
        System.out.println("HEAD-HP");
    }
}
