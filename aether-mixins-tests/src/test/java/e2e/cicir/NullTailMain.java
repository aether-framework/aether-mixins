package e2e.cicir;

public class NullTailMain {
    public static void main(final String[] args) {
        String v = s();
        System.out.print("RET=" + (v == null ? "null" : v));
    }
    static String s() { return "X"; }
}
