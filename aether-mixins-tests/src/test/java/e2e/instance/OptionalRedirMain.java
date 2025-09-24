
package e2e.instance;

public class OptionalRedirMain {
    public static void main(final String[] args) {
        var t = new OptionalRedirTarget();
        System.out.println("RESULT=" + t.run());
    }
}