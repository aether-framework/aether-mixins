package e2e.instance.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;

@Mixin(targets = "e2e.instance.InjectInstanceMethod")
public abstract class InjectInstanceMethodMixin {

    @Inject(method = "targetMethod()V", at = Inject.At.HEAD)
    private void onTargetMethod() {
        System.out.println("INJECT-SUCCESS");
    }
}
