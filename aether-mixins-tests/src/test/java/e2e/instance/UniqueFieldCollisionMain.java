package e2e.instance;

public class UniqueFieldCollisionMain {
    public static void main(final String[] args) {
        var t = new UniqueFieldCollisionTarget();
        System.out.println("RESULT=" + t.calc(5));
    }
}