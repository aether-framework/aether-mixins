package e2e.instance;

public class UniqueCollisionTarget {
    public void targetMethod() {
        System.out.println("ORIG-CALL");
    }

    // Force a name+desc collision with the hook method from mixin
    private void onTargetMethod() {
        // existing symbol with same signature; premerge should rename injected copy
        System.out.println("ORIG-HELPER");
    }
}