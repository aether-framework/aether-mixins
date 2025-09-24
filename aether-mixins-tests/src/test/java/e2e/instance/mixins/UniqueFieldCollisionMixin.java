package e2e.instance.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;
import de.splatgames.aether.mixins.core.api.Unique;
import e2e.instance.UniqueFieldCollisionTarget;

@Mixin(UniqueFieldCollisionTarget.class)
public abstract class UniqueFieldCollisionMixin {

    @Unique
    private int uniqueField; // no inline initializer here

    @Inject(method = "calc(I)I", at = Inject.At.HEAD)
    private void head(final int n) {
        // Initialize explicitly since <init>-based initializer isn't migrated
        uniqueField = 100;

        if (uniqueField == 100) {
            System.out.println("UNIQUE-FIELD-OK");
        }
    }
}
