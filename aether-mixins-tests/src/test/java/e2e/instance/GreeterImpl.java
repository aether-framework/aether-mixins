package e2e.instance;

public class GreeterImpl implements Greeter {
    public String greet(final int n) {
        return "G" + n;
    }
}