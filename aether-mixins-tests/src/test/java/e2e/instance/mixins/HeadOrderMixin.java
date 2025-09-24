package e2e.instance.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;
import e2e.instance.HeadAndRedirectTarget;

@Mixin(HeadAndRedirectTarget.class)
public abstract class HeadOrderMixin {

    @Inject(method = "run(I)I", at = Inject.At.HEAD)
    private void onHead(final int n) {
        System.out.println("HEAD-ORDER-OK");
    }
}
