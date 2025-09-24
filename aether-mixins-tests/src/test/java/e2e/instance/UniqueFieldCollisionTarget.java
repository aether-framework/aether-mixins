package e2e.instance;

public class UniqueFieldCollisionTarget {
    private int uniqueField = 2; // name collision with @Unique field in mixin

    public int calc(final int n) {
        return uniqueField + n;
    }
}