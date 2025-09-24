package e2e.statics;

import e2e.statics.api.UseInterface;

public class UseInterfaceMain {
    public static void main(final String[] args) {
        System.out.println(new UseInterface().callIface());
    }
}
