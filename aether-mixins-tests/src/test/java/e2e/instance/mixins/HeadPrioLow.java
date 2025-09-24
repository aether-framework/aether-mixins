package e2e.instance.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;
import e2e.instance.HeadPrioTarget;

@Mixin(targets = "e2e.instance.HeadPrioTarget", priority = 100)
public abstract class HeadPrioLow {
    @Inject(method = "go()Ljava/lang/String;", at = Inject.At.HEAD, id = "head-lp")
    private void onHead() {
        System.out.println("HEAD-LP");
    }
}
