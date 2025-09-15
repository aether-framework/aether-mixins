package e2e.cicir.mixins;

import de.splatgames.aether.mixins.core.api.CallbackInfo;
import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;

@Mixin(targets = "e2e.cicir.CirHeadNonVoidMain")
public final class InvalidCiNonVoidMixin {

    @Inject(
        method = "target()I",
        at = Inject.At.HEAD,
        id = "invalid-ci-nonvoid"
    )
    public static void head(final CallbackInfo ci) {
        // This is intentionally invalid: CI on non-void target.
    }
}
