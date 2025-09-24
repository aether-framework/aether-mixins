package e2e.instance.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;
import e2e.instance.TwoMixinsTarget;

@Mixin(TwoMixinsTarget.class)
public abstract class TwoMixinsA {

    @Inject(method = "work(I)I", at = Inject.At.HEAD)
    private void head(final int n) {
        // Set base to 2 before body executes
        ((TwoMixinsTarget) (Object) this).add(2);
        System.out.println("MIXIN-A-OK");
    }
}
