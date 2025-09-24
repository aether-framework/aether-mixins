package e2e.statics.mixins;

import de.splatgames.aether.mixins.core.api.Mixin;
import de.splatgames.aether.mixins.core.api.Redirect;
import e2e.statics.PrivateSpecial;

@Mixin(targets = "e2e.statics.PrivateSpecial", priority = 100)
public final class PrivateSpecialMixin {
    @Redirect(
            method = "call()Ljava/lang/String;",
            callOwner = "e2e/statics/PrivateSpecial",
            callName = "secret",
            callDesc = "()Ljava/lang/String;",
            kind = Redirect.InvokeKind.INVOKESPECIAL,
            ordinal = 0
    )
    public static String redirectPrivateSecret(final PrivateSpecial self) {
        return "PATCHED-PRIV";
    }
}
