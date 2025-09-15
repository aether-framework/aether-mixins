package e2e.cicir;

public class TryFinallyMain {
    public static void main(final String[] args) {
        System.out.print("RET=" + target(Boolean.getBoolean("p")));
    }
    static String target(boolean p) {
        try {
            if (p) return "A";
            return "B";
        } catch (RuntimeException e) {
            return "C";
        } finally {
            // no-op but forces complex frames
            int x = 1;
        }
    }
}
