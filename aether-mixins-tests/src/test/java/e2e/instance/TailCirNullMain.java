package e2e.instance;

public class TailCirNullMain {
    public static void main(final String[] args) {
        TailCirNullTarget t = new TailCirNullTarget();
        System.out.println("A=" + t.maybe(2));
        System.out.println("B=" + t.maybe(3));
    }
}