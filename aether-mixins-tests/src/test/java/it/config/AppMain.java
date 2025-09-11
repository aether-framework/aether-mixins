package it.config;

public class AppMain {
    public static void main(final String[] args) {
        System.out.println(new AppMain().greet());
    }

    public String greet() {
        return "ORIG";
    }
}
