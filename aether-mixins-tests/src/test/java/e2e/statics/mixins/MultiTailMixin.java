package e2e.statics.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;

@Mixin(targets = "e2e.statics.MultiTail", priority = 100)
public final class MultiTailMixin {
    @Inject(method = "work(I)V", at = Inject.At.TAIL)
    public static void tailMarker() {
        System.out.print("[T]");
    }
}
