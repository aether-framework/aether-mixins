package e2e.instance.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;
import de.splatgames.aether.mixins.core.api.Mutable;
import de.splatgames.aether.mixins.core.api.Shadow;
import de.splatgames.aether.mixins.core.api.Unique;
import e2e.instance.CounterService;

import java.util.concurrent.atomic.AtomicInteger;

@Mixin(CounterService.class)
public abstract class InstanceShadowMixin {

    @Shadow
    @Mutable
    private AtomicInteger shadow$base;

    @Inject(method = "work(I)I", at = Inject.At.HEAD)
    @Unique
    public void outHead(final int n) {
        this.shadow$base = new AtomicInteger(6);
    }
}
