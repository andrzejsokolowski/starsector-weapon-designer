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

    /** Pairs of (character the game's fonts lack, what to write instead). */
    private static final String[][] FONT_SWAPS = {
            {"ą", "a"}, {"ć", "c"}, {"ę", "e"}, {"ł", "l"}, {"ń", "n"},
            {"ś", "s"}, {"ź", "z"}, {"ż", "z"}, {"Ą", "A"}, {"Ć", "C"},
            {"Ę", "E"}, {"Ł", "L"}, {"Ń", "N"}, {"Ś", "S"}, {"Ź", "Z"},
            {"Ż", "Z"}, {"‘", "'"}, {"’", "'"}, {"‚", "'"}, {"′", "'"},
            {"“", "\""}, {"”", "\""}, {"„", "\""}, {"″", "\""}, {"–", "-"},
            {"—", "-"}, {"−", "-"}, {"…", "..."}, {" ", " "}};

    /**
     * Text the player typed or pasted, with Polish letters, curly quotes and long dashes swapped
     * for plain ones most of the game's fonts have. Anything else a font lacks is drawn as "?" by
     * the game, which is harmless.
     */
    public static String fontSafe(String text) {
        if (text == null) return "";
        StringBuilder out = new StringBuilder(text.length());
        outer:
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            for (String[] swap : FONT_SWAPS) {
                if (swap[0].charAt(0) == c) {
                    out.append(swap[1]);
                    continue outer;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    /** Puts a space into any run of more than max characters without one, so text can wrap. */
    public static String breakLongWords(String text, int max) {
        StringBuilder out = new StringBuilder(text.length());
        int run = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                run = 0;
            } else if (++run > max) {
                out.append(' ');
                run = 1;
            }
            out.append(c);
        }
        return out.toString();
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
