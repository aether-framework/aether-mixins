package e2e.cicir.mixins;

import de.splatgames.aether.mixins.core.api.CallbackInfo;
import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;

@Mixin(targets = "e2e.cicir.CiCiVoidMain")
public final class HeadCiVoidCancelMixin {

    @Inject(
        method = "main([Ljava/lang/String;)V",
        at = Inject.At.HEAD,
        id = "head-ci-void-cancel"
    )
    public static void head(final String[] args, final CallbackInfo ci) {
        System.out.println("HEAD-CI-CANCELLED");
        ci.cancel();
    }
}
