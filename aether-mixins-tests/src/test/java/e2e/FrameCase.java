package e2e;

public class FrameCase {
    public void run(final int x) {
        try {
            if (x == 1) {
                throw new RuntimeException("X");
            }
            System.out.print("OK");
        } catch (RuntimeException e) {
            System.out.print("ERR");
            return;
        }
        System.out.print("DONE");
    }
}
