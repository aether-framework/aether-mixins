package e2e.instance.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;
import e2e.instance.CtorTarget;

@Mixin(CtorTarget.class)
public abstract class CtorTailMixin {

    // Inject at the end of the constructor
    @Inject(method = "<init>()V", at = Inject.At.TAIL)
    private void onCtorTail() {
        ((CtorTarget) (Object) this).setFlag(true);
        System.out.println("CTOR-TAIL-OK");
    }
}

