package e2e.instance;

public class InterfaceCaller {
    private final Greeter g = new GreeterImpl();

    public String call() {
        return this.g.greet(4);
    }
}