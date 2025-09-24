package e2e.instance;

public class HeadAndRedirectTarget {
    private final OrderHelper h = new OrderHelper();

    public int run(final int n) {
        int v = n + 1;
        String s = h.say();
        System.out.println("BODY:" + v + ":" + s);
        return v;
    }
}