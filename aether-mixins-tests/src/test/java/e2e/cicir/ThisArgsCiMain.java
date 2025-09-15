package e2e.cicir;

public class ThisArgsCiMain {
    public static void main(final String[] args) {
        new ThisArgsCiMain().g(5, "X");
    }

    String g(final int i, final String s) {
        System.out.print("BODY");
        return s + i;
    }
}
