package weapondesigner.combat;

import java.util.HashMap;
import java.util.Map;

import weapondesigner.Design;
import weapondesigner.Kind;
import weapondesigner.Script;
import weapondesigner.Stat;

/**
 * What battle code needs to know about every designed weapon, by weapon id: its script levels,
 * and its designed fire rate when that is faster than the game fires. Filled whenever a design is
 * written onto its weapon, so like the weapon definitions it always matches the loaded save.
 */
public final class ScriptBook {
    /** Seconds between the missiles of one salvo, as written by SpecWriter. */
    public static final float MISSILE_BURST_DELAY = 0.12f;

    private static final Map<String, int[]> LEVELS = new HashMap<>();
    private static final Map<String, Float> BEAM_PULSE_DAMAGE = new HashMap<>();
    private static final Map<String, Rate> RATES = new HashMap<>();

    /** A designed fire rate the weapon cannot reach by itself; {@link RapidFire} tops it up. */
    public static final class Rate {
        public final double shotsPerSecond;
        /** The longest wait between two real shots while the weapon keeps firing. */
        public final double longestGap;
        public final int maxPerShot;

        Rate(double shotsPerSecond, double longestGap, int maxPerShot) {
            this.shotsPerSecond = shotsPerSecond;
            this.longestGap = longestGap;
            this.maxPerShot = maxPerShot;
        }
    }

    private ScriptBook() {
    }

    public static void put(Design d) {
        if (d.weaponId == null) return;
        int[] levels = new int[Script.values().length];
        boolean any = false;
        for (Script script : Script.values()) {
            levels[script.ordinal()] = d.level(script);
            any |= levels[script.ordinal()] > 0;
        }
        if (any) LEVELS.put(d.weaponId, levels);
        else LEVELS.remove(d.weaponId);
        if (d.kind == Kind.BEAM) BEAM_PULSE_DAMAGE.put(d.weaponId, d.value(Stat.DAMAGE) * Script.BEAM_PULSE);
        Rate rate = rateFor(d);
        if (rate != null) RATES.put(d.weaponId, rate);
        else RATES.remove(d.weaponId);
    }

    /** The top-up a design needs, or null when the weapon fires its designed rate by itself. */
    public static Rate rateFor(Design d) {
        double minGap = 1.0 / RapidFire.NATIVE_RATE;
        switch (d.kind) {
            case SHOT:
            case LASER: {
                double rate = d.value(Stat.RATE);
                return rate > RapidFire.NATIVE_RATE ? new Rate(rate, minGap, RapidFire.MAX_PER_SHOT) : null;
            }
            case MISSILE: {
                double reload = d.value(Stat.RELOAD);
                if (reload >= minGap) return null;
                double salvo = Math.max(1, Math.min(Math.round(d.value(Stat.SALVO)), Math.round(d.value(Stat.AMMO))));
                double cycle = reload + MISSILE_BURST_DELAY * (salvo - 1);
                double gap = salvo > 1 ? Math.max(minGap, MISSILE_BURST_DELAY) : minGap;
                return new Rate(salvo / Math.max(1e-9, cycle), gap, RapidFire.MAX_PER_MISSILE);
            }
            default:
                return null;
        }
    }

    public static void clear(String weaponId) {
        LEVELS.remove(weaponId);
        BEAM_PULSE_DAMAGE.remove(weaponId);
        RATES.remove(weaponId);
    }

    public static Rate rateOf(String weaponId) {
        return weaponId == null ? null : RATES.get(weaponId);
    }

    /** The weapon's script levels by {@link Script#ordinal()}, or null when it has none. */
    public static int[] of(String weaponId) {
        return weaponId == null ? null : LEVELS.get(weaponId);
    }

    /** Damage a beam counts per pulse: half a second of its damage per second. */
    public static float beamPulseDamage(String weaponId) {
        Float damage = BEAM_PULSE_DAMAGE.get(weaponId);
        return damage == null ? 0f : damage;
    }
}
