package e2e.instance;

public class InterfaceCallerMain {
    public static void main(final String[] args) {
        var c = new InterfaceCaller();
        System.out.println("RESULT=" + c.call());
    }
}