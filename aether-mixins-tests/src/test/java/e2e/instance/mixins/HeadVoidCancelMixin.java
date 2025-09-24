package e2e.instance.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;
import de.splatgames.aether.mixins.core.api.CallbackInfo;
import e2e.instance.HeadVoidCancelTarget;

@Mixin(HeadVoidCancelTarget.class)
public abstract class HeadVoidCancelMixin {

    @Inject(method = "ping()V", at = Inject.At.HEAD, cancellable = true, id = "head-void-cancel")
    private void onHead(final CallbackInfo ci) {
        System.out.println("HEAD-CI-CANCEL");
        ci.cancel();
    }
}
