package e2e.statics.mixins;

import de.splatgames.aether.mixins.core.api.Mixin;
import de.splatgames.aether.mixins.core.api.Redirect;
import e2e.statics.SubSuper;

@Mixin(targets = "e2e.statics.SubSuper", priority = 100)
public final class SubSuperCallMixin {
    @Redirect(
            method = "call()Ljava/lang/String;",
            callOwner = "e2e/statics/BaseSuper",
            callName = "msg",
            callDesc = "()Ljava/lang/String;",
            kind = Redirect.InvokeKind.INVOKESPECIAL
    )
    public static String patchSuper(final SubSuper self) {
        return "PATCHED-SUPER";
    }
}
