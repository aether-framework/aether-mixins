package e2e.instance;

public class InjectOptionalMissingMain {
    public static void main(final String[] args) {
        System.out.println("RESULT=" + new InjectOptionalMissingTarget().run());
    }
}