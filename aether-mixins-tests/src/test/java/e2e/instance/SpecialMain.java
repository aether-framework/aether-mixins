package e2e.instance;

public class SpecialMain {
    public static void main(final String[] args) {
        var t = new SpecialTarget();
        System.out.println("RESULT=" + t.run(2)); // (2+1)*3 = 9, redirected to (2+5)*3 = 21
    }
}