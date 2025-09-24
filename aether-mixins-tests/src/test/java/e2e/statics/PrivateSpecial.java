package e2e.statics;

public class PrivateSpecial {
    private String secret() {
        return "ORIGINAL-PRIV";
    }

    public String call() {
        return secret();
    }
}
