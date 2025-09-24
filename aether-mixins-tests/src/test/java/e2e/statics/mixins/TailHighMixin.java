package e2e.statics.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;

@Mixin(targets = "e2e.statics.TailOrder", priority = 200)
public final class TailHighMixin {
    @Inject(method = "run()V", at = Inject.At.TAIL)
    public static void tailHigh() {
        System.out.print("[HIGH]");
    }
}