package e2e.instance;

public class TailStateMain {
    public static void main(final String[] args) {
        var t = new TailStateTarget();
        System.out.println("R1=" + t.work(2));
        System.out.println("R2=" + t.work(2));
    }
}