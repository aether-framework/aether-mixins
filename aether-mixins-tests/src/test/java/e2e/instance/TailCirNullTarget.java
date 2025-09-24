package e2e.instance;

public class TailCirNullTarget {
    public String maybe(final int n) {
        return (n % 2 == 0) ? "HELLO" : null;
    }
}