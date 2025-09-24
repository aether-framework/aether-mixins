package e2e.statics;

public class FrameCaseMain {
    public static void main(final String[] args) {
        FrameCase f = new FrameCase();
        f.run(1);  // -> ERR + TAIL
        System.out.print(" ");
        f.run(0);  // -> OKDONE + TAIL
        System.out.println();
    }
}
