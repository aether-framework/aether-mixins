package e2e.instance.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;
import de.splatgames.aether.mixins.core.api.Shadow;
import de.splatgames.aether.mixins.core.api.Unique;
import e2e.instance.OptionalTarget;

@Mixin(OptionalTarget.class)
public abstract class OptionalShadowMixin {

    @Shadow(optional = true)
    private int shadow$missing; // not present on target

    @Inject(method = "run()V", at = Inject.At.HEAD)
    @Unique
    private void head() {
        // use the missing shadow (read), which must be neutralized (push default) → no crash
        int ignored = this.shadow$missing;
    }
}
