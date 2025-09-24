package e2e.instance;

public class SpecialTarget {
    private int secret(final int x) {
        return x + 1;
    }

    public int run(final int n) {
        return secret(n) * 3;
    }
}