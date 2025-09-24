package e2e.instance;

public class HeadVoidCancelMain {
    public static void main(final String[] args) {
        new HeadVoidCancelTarget().ping();
        System.out.println("DONE");
    }
}