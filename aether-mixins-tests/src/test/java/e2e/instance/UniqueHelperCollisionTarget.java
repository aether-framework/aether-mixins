package e2e.instance;

public class UniqueHelperCollisionTarget {
    // A method with the same signature as our mixin @Unique helper
    private int helper(final int x) { return x + 1; }

    public int run(final int n) {
        // body does something simple; mixin hook will call its own helper (renamed)
        return n + 2;
    }
}