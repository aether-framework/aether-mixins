package e2e.cicir;

public class StaticWideMain {
    public static void main(final String[] args) {
        System.out.print("RET=" + f(11L, 2.5));
    }

    static int f(final long a, final double b) {
        return (int) (a + b);
    } // 13
}
