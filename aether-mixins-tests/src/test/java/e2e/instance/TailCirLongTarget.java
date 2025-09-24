package e2e.instance;

public class TailCirLongTarget {
    public long calc(final int a) {
        return (long) a * 1000L; // e.g., 12 -> 12000
    }
}