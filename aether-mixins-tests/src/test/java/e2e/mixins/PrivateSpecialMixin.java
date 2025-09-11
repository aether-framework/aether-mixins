package e2e.mixins;

import de.splatgames.aether.mixins.core.api.Mixin;
import de.splatgames.aether.mixins.core.api.Redirect;

@Mixin(targets = "e2e.PrivateSpecial", priority = 100)
public final class PrivateSpecialMixin {
    @Redirect(
            method = "call()Ljava/lang/String;",
            callOwner = "e2e/PrivateSpecial",
            callName = "secret",
            callDesc = "()Ljava/lang/String;",
            kind = Redirect.InvokeKind.INVOKESPECIAL,
            ordinal = 0
    )
    public static String redirectPrivateSecret(final e2e.PrivateSpecial self) {
        return "PATCHED-PRIV";
    }
}
