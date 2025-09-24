package e2e.instance.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;
import e2e.instance.TailStateTarget;

@Mixin(TailStateTarget.class)
public abstract class TailStateMixin {

    @Inject(method = "work(I)I", at = Inject.At.TAIL)
    private void after(final int n) {
        // Affect subsequent calls only
        ((TailStateTarget)(Object)this).setBase(10);
        System.out.println("TAIL-STATE-SET");
    }
}
