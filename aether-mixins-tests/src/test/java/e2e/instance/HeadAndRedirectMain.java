package e2e.instance;

public class HeadAndRedirectMain {
    public static void main(final String[] args) {
        var t = new HeadAndRedirectTarget();
        t.run(2);
    }
}