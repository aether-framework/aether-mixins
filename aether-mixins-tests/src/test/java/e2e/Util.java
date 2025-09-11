package e2e;

import org.jetbrains.annotations.NotNull;

public final class Util {
    public static String msg() {
        return "ORIGINAL";
    }

    public static void log(@NotNull final String s) {
        System.out.println(s);
    }
}
