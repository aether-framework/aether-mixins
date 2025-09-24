package e2e.instance;

public class TailCirLongMain {
    public static void main(final String[] args) {
        var t = new TailCirLongTarget();
        System.out.println("RESULT=" + t.calc(12));
    }
}