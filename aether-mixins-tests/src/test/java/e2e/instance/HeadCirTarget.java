package e2e.instance;

public class HeadCirTarget {
    public int compute(final int x) {
        System.out.println("ORIG-BODY");
        return x * 2;
    }
}