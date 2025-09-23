package e2e.instance.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;
import de.splatgames.aether.mixins.core.api.Mutable;
import de.splatgames.aether.mixins.core.api.Shadow;
import de.splatgames.aether.mixins.core.api.Unique;
import e2e.instance.CounterService;

import java.util.concurrent.atomic.AtomicInteger;

@Mixin(CounterService.class)
public abstract class InstanceShadowNoPrefixMixin {

    @Shadow // allow plain name with default prefix logic
    @Mutable
    private AtomicInteger base;

    @Inject(method = "work(I)I", at = Inject.At.HEAD)
    @Unique
    public void outHead(final int n) {
        this.base = new AtomicInteger(6);
    }
}
