package e2e.instance.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;
import de.splatgames.aether.mixins.core.api.Shadow;
import e2e.instance.ShadowMethodTarget;

@Mixin(ShadowMethodTarget.class)
public abstract class ShadowMethodProbeMixin {

    // Shadowing a instance method; used to invoke original from mixin
    @Shadow()
    public abstract int shadow$secret(int n);

    @Inject(method = "api(I)I", at = Inject.At.HEAD)
    private void probe(final int n) {
        int v = this.shadow$secret(3);
        System.out.println("SHADOW-METHOD=" + v); // expect 4
    }
}
