package e2e.instance.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;
import de.splatgames.aether.mixins.core.api.Unique;
import e2e.instance.UniqueHelperCollisionTarget;

@Mixin(UniqueHelperCollisionTarget.class)
public abstract class UniqueHelperCollisionMixin {

    @Unique
    private int helper(final int x) {
        // If renaming works, this method will be copied/renamed and callable from the hook
        System.out.println("UNIQUE-HELPER-OK");
        return x + 2;
    }

    @Inject(method = "run(I)I", at = Inject.At.TAIL)
    private void tail(final int n) {
        // Call the @Unique helper (should target the renamed private method on target)
        int v = this.helper(n); // should call the renamed copy, not target's helper
        // Effect: run returns n+2; we don't change return, but we can just print via side effects
        // For a visible result delta, you could convert this to CIR, but here we validate invocation via marker.
    }
}
