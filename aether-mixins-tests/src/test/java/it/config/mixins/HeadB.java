package it.config.mixins;

import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;

@Mixin(targets = "it.config.AppMain", priority = 100)
public final class HeadB {
    @Inject(
            method = "greet()Ljava/lang/String;",
            at = Inject.At.HEAD
    )
    public static void head() {
        System.out.print("PATCH-B: ");
    }
}
