package de.splatgames.aether.mixins.core.utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NumberConversions {
    private NumberConversions() {}

    public static int floor(final double num) {
        final int floor = (int) num;
        return floor == num ? floor : floor - (int) (Double.doubleToRawLongBits(num) >>> 63);
    }

    public static int ceil(final double num) {
        final int floor = (int) num;
        return floor == num ? floor : floor + (int) (~Double.doubleToRawLongBits(num) >>> 63);
    }

    public static int round(final double num) {
        return floor(num + 0.5d);
    }

    public static double square(final double num) {
        return num * num;
    }

    public static int toInt(@NotNull final Object object) {
        if (object instanceof Number) {
            return ((Number) object).intValue();
        }

        try {
            return Integer.parseInt(object.toString());
        } catch (NumberFormatException | NullPointerException ignored) {
        }
        return 0;
    }

    public static float toFloat(@NotNull final Object object) {
        if (object instanceof Number) {
            return ((Number) object).floatValue();
        }

        try {
            return Float.parseFloat(object.toString());
        } catch (NumberFormatException | NullPointerException ignored) {
        }
        return 0;
    }

    public static double toDouble(@NotNull final Object object) {
        if (object instanceof Number) {
            return ((Number) object).doubleValue();
        }

        try {
            return Double.parseDouble(object.toString());
        } catch (NumberFormatException | NullPointerException ignored) {
        }
        return 0;
    }

    public static long toLong(@NotNull final Object object) {
        if (object instanceof Number) {
            return ((Number) object).longValue();
        }

        try {
            return Long.parseLong(object.toString());
        } catch (NumberFormatException | NullPointerException ignored) {
        }
        return 0;
    }

    public static short toShort(@NotNull final Object object) {
        if (object instanceof Number) {
            return ((Number) object).shortValue();
        }

        try {
            return Short.parseShort(object.toString());
        } catch (NumberFormatException | NullPointerException ignored) {
        }
        return 0;
    }

    public static byte toByte(@NotNull final Object object) {
        if (object instanceof Number) {
            return ((Number) object).byteValue();
        }

        try {
            return Byte.parseByte(object.toString());
        } catch (NumberFormatException | NullPointerException ignored) {
        }
        return 0;
    }

    public static boolean isFinite(final double d) {
        return Math.abs(d) <= Double.MAX_VALUE;
    }

    public static boolean isFinite(final float f) {
        return Math.abs(f) <= Float.MAX_VALUE;
    }

    public static void checkFinite(final double d, @NotNull final String message) {
        if (!isFinite(d)) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void checkFinite(final float d, @NotNull final String message) {
        if (!isFinite(d)) {
            throw new IllegalArgumentException(message);
        }
    }
}
