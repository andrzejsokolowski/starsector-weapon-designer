package weapondesigner;

import lunalib.lunaSettings.LunaSettings;

/**
 * The LunaLib settings. Read once per load and whenever the designer opens, then kept here, so
 * costing never calls into LunaLib on its own (and the checks run without it).
 */
public final class Settings {
    public static final String MOD_ID = "weapon_designer";
    public static final String OP_RATE = "wd_op_rate";
    public static final String PRICE_PERCENT = "wd_price_percent";
    public static final String OP_LIMIT = "wd_op_limit";

    public static final int DEFAULT_OP_RATE = 10;

    private static int opRate = DEFAULT_OP_RATE;
    private static int pricePercent = 100;
    private static int opLimit = 0;

    private Settings() {
    }

    public static void refresh() {
        opRate = read(OP_RATE, DEFAULT_OP_RATE, 1, 100);
        pricePercent = read(PRICE_PERCENT, 100, 1, 1000);
        opLimit = read(OP_LIMIT, 0, 0, 100000);
    }

    /** Percent of a design's design points that becomes its OP cost. */
    public static int opRate() {
        return opRate;
    }

    /** Production cost in percent of the normal price. */
    public static int pricePercent() {
        return pricePercent;
    }

    /** Most OP a design may cost, or 0 for no limit. */
    public static int opLimit() {
        return opLimit;
    }

    private static int read(String key, int fallback, int min, int max) {
        try {
            Integer value = LunaSettings.getInt(MOD_ID, key);
            if (value != null) return Math.max(min, Math.min(max, value));
        } catch (Throwable t) {
            // LunaLib missing or not ready: keep the default.
        }
        return fallback;
    }
}
