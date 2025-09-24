package e2e.statics;

public class MultiTail {
    public void work(final int n) {
        if (n < 0) {
            System.out.print("NEG");
            return;
        }
        if (n == 0) {
            System.out.print("ZERO");
            return;
        }
        System.out.print("POS");
    }
}
