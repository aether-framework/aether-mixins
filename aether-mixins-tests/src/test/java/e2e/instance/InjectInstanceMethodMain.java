package e2e.instance;

public class InjectInstanceMethodMain {
    public static void main(final String[] args) {
        var instance = new InjectInstanceMethod();
        instance.targetMethod();
    }
}
