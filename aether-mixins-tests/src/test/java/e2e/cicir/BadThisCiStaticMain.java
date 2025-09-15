package e2e.cicir;

public class BadThisCiStaticMain {
    public static void main(final String[] args) {
        foo();
    }

    static void foo() { /* no-op */ }
}
