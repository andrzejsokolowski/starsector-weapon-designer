package weapondesigner;

import java.util.Locale;

/** Number formatting for the designer. Kept free of game classes so the checks can use it. */
public final class Text {
    private Text() {
    }

    /** 1,250 for whole numbers, two significant digits below 1 (0.094), up to two decimals otherwise. */
    public static String number(float v) {
        if (Float.isNaN(v)) return "?";
        if (Float.isInfinite(v)) return v > 0 ? "huge" : "-huge";
        if (Math.abs(v) >= 1000f || Math.abs(v - Math.round(v)) < 0.005f) return grouped(Math.round((double) v));
        int decimals = 2;
        if (Math.abs(v) < 1f && v != 0f) {
            decimals = Math.min(8, Math.max(2, 1 - (int) Math.floor(Math.log10(Math.abs(v)))));
        }
        String text = String.format(Locale.ROOT, "%." + decimals + "f", v);
        while (text.endsWith("0")) text = text.substring(0, text.length() - 1);
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    public static String percent(float share) {
        return number(share * 100f) + "%";
    }

    public static String grouped(long v) {
        String digits = Long.toString(Math.abs(v));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) out.append(',');
            out.append(digits.charAt(i));
        }
        return (v < 0 ? "-" : "") + out;
    }
}
