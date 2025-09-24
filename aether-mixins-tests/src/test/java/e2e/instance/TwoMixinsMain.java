package e2e.instance;

public class TwoMixinsMain {
    public static void main(final String[] args) {
        var t = new TwoMixinsTarget();
        int res = t.work(3);
        System.out.println("RESULT=" + res);
    }
}