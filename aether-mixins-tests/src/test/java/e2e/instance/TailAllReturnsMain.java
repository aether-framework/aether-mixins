package e2e.instance;

public class TailAllReturnsMain {
    public static void main(final String[] args) {
        var t = new TailAllReturnsTarget();
        System.out.println("R1=" + t.eval(1));
        System.out.println("R2=" + t.eval(0));
    }
}