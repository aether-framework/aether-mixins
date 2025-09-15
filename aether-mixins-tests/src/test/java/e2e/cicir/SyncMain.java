package e2e.cicir;

public class SyncMain {
    public static void main(final String[] args) {
        System.out.print("RET=" + f());
    }

    static synchronized String f() {
        return "ORIG";
    }
}
