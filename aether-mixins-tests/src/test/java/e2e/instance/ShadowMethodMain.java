package e2e.instance;

public class ShadowMethodMain {
    public static void main(final String[] args) {
        var t = new ShadowMethodTarget();
        System.out.println("RESULT=" + t.api(3));
    }
}