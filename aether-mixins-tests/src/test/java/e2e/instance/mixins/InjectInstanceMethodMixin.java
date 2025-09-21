package e2e.instance.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;
import e2e.instance.InjectInstanceMethod;

@Mixin(InjectInstanceMethod.class)
public abstract class InjectInstanceMethodMixin {

    @Inject(method = "targetMethod()V", at = Inject.At.HEAD)
    private void onTargetMethod() {
        System.out.println("INJECT-SUCCESS");
    }
}
