package e2e.statics.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;

@Mixin(targets = "e2e.statics.FrameCase", priority = 100)
public final class FrameCaseTailMixin {
    @Inject(method = "run(I)V", at = Inject.At.TAIL)
    public static void tailMark() {
        System.out.print("[TAIL]");
    }
}
