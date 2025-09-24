package e2e.instance;

public class TailAllReturnsTarget {
    public int eval(final int x) {
        if (x > 0) {
            return 1;
        }
        return -1;
    }
}