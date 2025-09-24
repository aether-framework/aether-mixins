package e2e.instance;

public class SpecialInstanceMain {
    public static void main(final String[] args) {
        System.out.println("RESULT=" + new SpecialInstanceTarget().run(3));
    }
}