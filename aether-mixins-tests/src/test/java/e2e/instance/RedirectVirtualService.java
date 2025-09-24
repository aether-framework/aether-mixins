package e2e.instance;

public class RedirectVirtualService {
    // Simple state-free demo
    public int helper(final int n) {
        return n * 2;
    }

    public int compute(final int n) {
        // Virtual call – will be redirected
        return helper(n) + 1;
    }
}