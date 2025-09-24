package e2e.statics.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;

@Mixin(targets = "e2e.statics.OrderPlay", priority = 100)
public final class OrderTailMixin {
    @Inject(method = "run()Ljava/lang/String;", at = Inject.At.TAIL)
    public static void tail() {
        System.out.print("[TAIL]");
    }
}
