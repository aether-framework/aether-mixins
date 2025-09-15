package e2e.cicir;

public class CiCiVoidMain {
    public static void main(final String[] args) {
        // If CI HEAD cancels, this line must not print
        System.out.println("ORIG-VOID");
    }
}