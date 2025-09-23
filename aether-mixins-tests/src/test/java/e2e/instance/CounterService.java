package e2e.instance;

import java.util.concurrent.atomic.AtomicInteger;

public class CounterService {
    private final AtomicInteger base = new AtomicInteger(1);

    public void helper(final int v) {
    }

    public void out() {
        System.out.println("CounterService.out: base=" + this.base);
    }

    public int work(final int n) {

        return this.base.get() + n + 1;
    }
}
