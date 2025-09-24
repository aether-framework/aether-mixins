package e2e.instance;

public class RedirectVirtualInstanceMain {
    public static void main(final String[] args) {
        var t = new RedirectVirtualInstanceTarget();
        System.out.println("RESULT=" + t.compute(2));
    }
}