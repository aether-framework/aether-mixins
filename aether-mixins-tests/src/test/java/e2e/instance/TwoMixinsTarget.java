package e2e.instance;

public class TwoMixinsTarget {
    private int base = 0;

    public int work(final int n) {
        // base may be changed by Mixins at HEAD/TAIL
        return this.base + n;
    }

    public void add(final int v) {
        this.base += v;
    }
}