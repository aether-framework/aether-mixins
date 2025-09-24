package e2e.instance;

public class RedirectVirtualMain {
    public static void main(final String[] args) {
        var s = new RedirectVirtualService();
        var res = s.compute(4);
        System.out.println("RESULT=" + res);
    }
}