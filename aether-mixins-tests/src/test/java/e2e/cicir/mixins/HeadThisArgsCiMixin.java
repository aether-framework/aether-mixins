package e2e.cicir.mixins;

import de.splatgames.aether.mixins.core.api.CallbackInfoReturnable;
import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;
import e2e.cicir.ThisArgsCiMain;

@Mixin(targets = "e2e.cicir.ThisArgsCiMain")
public final class HeadThisArgsCiMixin {
    @Inject(
            method = "g(ILjava/lang/String;)Ljava/lang/String;",
            at = Inject.At.HEAD,
            id = "head-this-args-ci"
    )
    public static void head(final ThisArgsCiMain self, final int i, final String s, final CallbackInfoReturnable<String> cir) {
        if (self != null && i == 5 && "X".equals(s)) {
            System.out.print("[HEAD:ok]");
        }
    }
}
