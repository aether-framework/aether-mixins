package e2e.instance.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;
import de.splatgames.aether.mixins.core.api.Unique;
import e2e.instance.UniqueCollisionTarget;

@Mixin(UniqueCollisionTarget.class)
public abstract class UniqueCollisionMixin {

    @Inject(method = "targetMethod()V", at = Inject.At.HEAD)
    private void onTargetMethodInject() {
        System.out.println("INJECT-CALL");
        // call a helper with the same name+desc as a method already present on the target
        this.onTargetMethod(); // must resolve to renamed private copy
    }

    @Unique
    private void onTargetMethod() {
        System.out.println("UNIQUE-HOOK");
    }
}
