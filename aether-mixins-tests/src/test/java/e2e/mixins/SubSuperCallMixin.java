package e2e.mixins;

import de.splatgames.aether.mixins.core.api.Mixin;
import de.splatgames.aether.mixins.core.api.Redirect;

@Mixin(targets = "e2e.SubSuper", priority = 100)
public final class SubSuperCallMixin {
    @Redirect(
            method = "call()Ljava/lang/String;",
            callOwner = "e2e/BaseSuper",
            callName = "msg",
            callDesc = "()Ljava/lang/String;",
            kind = Redirect.InvokeKind.INVOKESPECIAL
    )
    public static String patchSuper(final e2e.SubSuper self) {
        return "PATCHED-SUPER";
    }
}
