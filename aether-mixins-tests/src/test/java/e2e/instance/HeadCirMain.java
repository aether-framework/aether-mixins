package e2e.instance;

public class HeadCirMain {
    public static void main(final String[] args) {
        var t = new HeadCirTarget();
        System.out.println("RESULT=" + t.compute(5));
    }
}