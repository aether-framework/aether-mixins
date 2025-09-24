package e2e.instance.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;
import e2e.instance.InjectOptionalMissingTarget;

@Mixin(InjectOptionalMissingTarget.class)
public abstract class InjectOptionalMissingMixin {

    @Inject(method = "noSuchMethod()V", at = Inject.At.HEAD, optional = true, id = "inject-optional-miss")
    private static void neverCalled() {
        System.out.println("SHOULD-NOT-PRINT");
    }
}
