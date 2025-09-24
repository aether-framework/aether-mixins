package e2e.instance;

public class CtorMain {
    public static void main(final String[] args) {
        var t = new CtorTarget();
        System.out.println("FLAG=" + t.flag());
    }
}