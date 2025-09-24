package e2e.instance;

public class SpecialInstanceTarget {
    private int step(final int n) {
        return n * 2;
    }

    public int run(final int n) {
        return step(n) + 1;
    } // 3*2+1=7 -> redirected to 3*5+1=16
}