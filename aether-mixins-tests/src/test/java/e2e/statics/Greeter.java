package e2e.statics;

public class Greeter {
    public String greet() {
        return Util.msg();
    }

    public String greetTwice() {
        return Util.msg() + " " + Util.msg();
    }

    public String greetVirtual() {
        return new Helper().ping();
    }

    public String greetVirtualSingle() {
        return ping();
    }

    public String ping() {
        return "ORIGINAL-V";
    }

    public static final class Helper {
        public String ping() {
            return "ORIG-V";
        }
    }
}