package e2e.instance;

public class TailStateTarget {
    private int base = 1;

    public int work(final int n) {
        return this.base + n;
    }

    public void setBase(final int b) {
        this.base = b;
    }
}