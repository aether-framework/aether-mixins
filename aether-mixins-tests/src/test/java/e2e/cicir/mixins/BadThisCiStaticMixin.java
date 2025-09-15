package e2e.cicir.mixins;

import de.splatgames.aether.mixins.core.api.CallbackInfo;
import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;
import e2e.cicir.BadThisCiStaticMain;

@Mixin(targets = "e2e.cicir.BadThisCiStaticMain")
public final class BadThisCiStaticMixin {
    @Inject(method = "foo()V", at = Inject.At.HEAD, id = "bad-this-ci-static")
    public static void head(final BadThisCiStaticMain self, final CallbackInfo ci) {
        // invalid: static method hat kein 'this'
    }
}
