package e2e.instance;

public class RedirectVirtualInstanceTarget {
    private final MathService ms = new MathService();

    public int compute(final int x) {
        // original: ms.mul(x, 4) + 1  =>  (2*4)+1=9
        return this.ms.mul(x, 4) + 1;
    }
}