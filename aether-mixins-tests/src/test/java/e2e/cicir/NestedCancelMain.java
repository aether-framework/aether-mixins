package e2e.cicir;

public class NestedCancelMain {
    public static void main(final String[] args) {
        outer();
    }

    static void outer() {
        inner();
        System.out.print(" OUTER-END");
    }

    static void inner() {
        System.out.print("INNER");
    }
}
