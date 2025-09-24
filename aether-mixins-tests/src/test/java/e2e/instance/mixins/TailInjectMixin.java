package e2e.instance.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;
import e2e.instance.TailTarget;

@Mixin(TailTarget.class)
public abstract class TailInjectMixin {

    @Inject(method = "run()V", at = Inject.At.TAIL)
    private void afterRun() {
        System.out.println("TAIL-SUCCESS");
    }
}
