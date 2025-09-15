package e2e.cicir.mixins;

import de.splatgames.aether.mixins.core.api.CallbackInfoReturnable;
import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;

@Mixin(targets = "e2e.cicir.CiCiVoidMain")
public final class InvalidCirVoidMixin {

    @Inject(
        method = "main([Ljava/lang/String;)V",
        at = Inject.At.HEAD,
        id = "invalid-cir-void"
    )
    public static void head(final String[] args, final CallbackInfoReturnable<Integer> cir) {
        // This is intentionally invalid: CIR on a void target.
    }
}
