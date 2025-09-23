package e2e.instance;

public class CounterMain {
    public static void main(final String[] args) {
        CounterService s = new CounterService();
        int res = s.work(5);
        s.out();
        System.out.println("RESULT=" + res);
    }
}
