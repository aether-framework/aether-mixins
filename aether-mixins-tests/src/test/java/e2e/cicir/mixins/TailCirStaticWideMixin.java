package e2e.cicir.mixins;

import de.splatgames.aether.mixins.core.api.CallbackInfoReturnable;
import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;

@Mixin(targets = "e2e.cicir.StaticWideMain")
public final class TailCirStaticWideMixin {
    @Inject(method = "f(JD)I", at = Inject.At.TAIL, id = "tail-cir-static-wide")
    public static void tail(final CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(123);
    }
}
