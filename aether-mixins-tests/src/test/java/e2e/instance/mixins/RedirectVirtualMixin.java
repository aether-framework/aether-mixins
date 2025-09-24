package e2e.instance.mixins;

import de.splatgames.aether.mixins.core.api.Mixin;
import de.splatgames.aether.mixins.core.api.Redirect;
import e2e.instance.RedirectVirtualService;

@Mixin(RedirectVirtualService.class)
public abstract class RedirectVirtualMixin {

    // Redirect the virtual call to helper(int) inside compute(int): return n * 3 instead of n * 2
    @Redirect(
        method    = "compute(I)I",
        callOwner = "e2e/instance/RedirectVirtualService",
        callName  = "helper",
        callDesc  = "(I)I",
        kind      = Redirect.InvokeKind.INVOKEVIRTUAL,
        ordinal   = 0,
        id        = "redirect-helper-virtual"
    )
    private int redirectHelper(final int n) {
        System.out.println("REDIRECT-OK");
        return n * 3;
    }
}
