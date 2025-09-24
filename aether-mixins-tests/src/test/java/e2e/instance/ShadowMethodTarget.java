package e2e.instance;

public class ShadowMethodTarget {
    private int secret(final int n) {
        return n + 1;
    }

    public int api(final int n) {
        // Mixin will probe secret via @Shadow
        return secret(n) * 2;
    }
}