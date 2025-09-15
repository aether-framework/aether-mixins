package e2e.cicir;

public class CirHeadNonVoidMain {
    public static void main(final String[] args) {
        int v = target();
        System.out.println("RET=" + v);
    }

    static int target() {
        return 7;
    }
}