package it.smoke.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;

@Mixin(targets = "it.smoke.AppMain", priority = 100)
public final class HeadMixin {
    @Inject(
            method = "greet()Ljava/lang/String;",
            at = Inject.At.HEAD
    )
    public static void head() {
        System.out.print("PATCHED: ");
    }
}
