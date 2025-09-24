package e2e.statics;

public class RedirPrioMain {
    public static void main(String[] args) {
        String out = new RedirPrio().call();
        System.out.println(out);
    }
}
