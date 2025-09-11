package e2e.mixins;

import de.splatgames.aether.mixins.core.api.Mixin;
import de.splatgames.aether.mixins.core.api.Redirect;

@Mixin(targets = "e2e.api.UseInterface", priority = 100)
public final class UseInterfaceMixin {
    @Redirect(
            method = "callIface()Ljava/lang/String;",
            callOwner = "e2e/api/IService",
            callName = "ping",
            callDesc = "()Ljava/lang/String;",
            kind = Redirect.InvokeKind.INVOKEINTERFACE
    )
    public static String replaceIface(final e2e.api.IService self) { // Receiver = Interface-Typ
        return "PATCHED-IF";
    }
}