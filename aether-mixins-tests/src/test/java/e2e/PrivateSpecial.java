package e2e;

public class PrivateSpecial {
    private String secret() {
        return "ORIGINAL-PRIV";
    }

    public String call() {
        return secret();
    }
}
