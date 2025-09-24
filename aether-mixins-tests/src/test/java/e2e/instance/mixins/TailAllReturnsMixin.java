package e2e.instance.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;
import e2e.instance.TailAllReturnsTarget;

@Mixin(TailAllReturnsTarget.class)
public abstract class TailAllReturnsMixin {

    @Inject(method = "eval(I)I", at = Inject.At.TAIL)
    private void onTail(final int x) {
        System.out.println("TAIL-BRANCH-OK");
    }
}
