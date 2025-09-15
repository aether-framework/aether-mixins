package e2e.cicir;

public class CirTailMain {
    public static void main(final String[] args) {
        System.out.println("RET=" + target());
    }

    static String target() {
        return "RET=ORIG".substring(4); /* "ORIG" */
    }
}