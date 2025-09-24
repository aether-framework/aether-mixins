package e2e.statics;

public class GreeterVirtualMain {
    public static void main(final String[] args) {
        System.out.println(new Greeter().greetVirtual());
        System.out.println(new Greeter().greetVirtualSingle());
    }
}