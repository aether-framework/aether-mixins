package e2e.cicir.mixins;

import de.splatgames.aether.mixins.core.api.CallbackInfoReturnable;
import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;

@Mixin(targets = "e2e.cicir.SyncMain")
public final class TailCirSyncMixin {
    @Inject(method = "f()Ljava/lang/String;", at = Inject.At.TAIL, id = "tail-cir-sync")
    public static void tail(final CallbackInfoReturnable<String> cir) {
        cir.setReturnValue("SYNC");
    }
}
