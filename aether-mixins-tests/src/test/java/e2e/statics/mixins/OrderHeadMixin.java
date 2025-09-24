package e2e.statics.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;

@Mixin(targets = "e2e.statics.OrderPlay", priority = 100)
public final class OrderHeadMixin {
    @Inject(method = "run()Ljava/lang/String;", at = Inject.At.HEAD)
    public static void head() {
        System.out.print("[HEAD]");
    }
}
