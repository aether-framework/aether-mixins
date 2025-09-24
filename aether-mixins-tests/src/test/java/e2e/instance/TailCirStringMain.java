package e2e.instance;

public class TailCirStringMain {
    public static void main(final String[] args) {
        var t = new TailCirStringTarget();
        System.out.println("RESULT=" + t.greet("Bob"));
    }
}