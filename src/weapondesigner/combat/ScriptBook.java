package weapondesigner.combat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.loading.MissileSpecAPI;
import com.fs.starfarer.api.loading.ProjectileSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;

import weapondesigner.Borrowed;
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
    private static final Map<String, List<Lent<OnHitEffectPlugin>>> HITS = new HashMap<>();
    private static final Map<String, List<Lent<OnFireEffectPlugin>>> FIRES = new HashMap<>();
    private static final Map<String, List<String>> FRAMES = new HashMap<>();
    private static final Map<String, List<String>> BEAMS = new HashMap<>();

    /** A borrowed on-hit or on-fire script: one shared object per donor projectile, as the game keeps it. */
    public static final class Lent<T> {
        public final T script;
        public final String key;

        Lent(T script, String key) {
            this.script = script;
            this.key = key;
        }
    }

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
        putBorrowed(d);
    }

    /** Looks up the borrowed scripts' donors; scripts whose donor is no longer loaded are left out. */
    private static void putBorrowed(Design d) {
        List<Lent<OnHitEffectPlugin>> hits = new ArrayList<>();
        List<Lent<OnFireEffectPlugin>> fires = new ArrayList<>();
        List<String> frames = new ArrayList<>();
        List<String> beams = new ArrayList<>();
        for (Borrowed b : d.borrowed()) {
            String key = Guard.key(d.weaponId, b.donorId, b.type.name());
            WeaponSpecAPI donor;
            try {
                donor = Global.getSettings().getWeaponSpec(b.donorId);
            } catch (Throwable t) {
                continue;
            }
            if (donor == null) continue;
            Object shot = donor.getProjectileSpec();
            switch (b.type) {
                case HIT: {
                    OnHitEffectPlugin script = shot instanceof ProjectileSpecAPI ? ((ProjectileSpecAPI) shot).getOnHitEffect()
                            : shot instanceof MissileSpecAPI ? ((MissileSpecAPI) shot).getOnHitEffect() : null;
                    if (script != null) hits.add(new Lent<>(script, key));
                    break;
                }
                case FIRE: {
                    OnFireEffectPlugin script = shot instanceof ProjectileSpecAPI ? ((ProjectileSpecAPI) shot).getOnFireEffect()
                            : shot instanceof MissileSpecAPI ? ((MissileSpecAPI) shot).getOnFireEffect() : null;
                    if (script != null) fires.add(new Lent<>(script, key));
                    break;
                }
                case FRAME:
                    frames.add(b.donorId);
                    break;
                case BEAM:
                    beams.add(b.donorId);
                    break;
            }
        }
        store(HITS, d.weaponId, hits);
        store(FIRES, d.weaponId, fires);
        store(FRAMES, d.weaponId, frames);
        store(BEAMS, d.weaponId, beams);
    }

    private static <T> void store(Map<String, List<T>> map, String weaponId, List<T> list) {
        if (list.isEmpty()) map.remove(weaponId);
        else map.put(weaponId, list);
    }

    public static List<Lent<OnHitEffectPlugin>> borrowedHits(String weaponId) {
        return listOf(HITS, weaponId);
    }

    public static List<Lent<OnFireEffectPlugin>> borrowedFires(String weaponId) {
        return listOf(FIRES, weaponId);
    }

    /** Donor weapon ids of the borrowed every-frame scripts. */
    public static List<String> borrowedFrames(String weaponId) {
        return listOf(FRAMES, weaponId);
    }

    /** Donor weapon ids of the borrowed beam scripts. */
    public static List<String> borrowedBeams(String weaponId) {
        return listOf(BEAMS, weaponId);
    }

    private static <T> List<T> listOf(Map<String, List<T>> map, String weaponId) {
        List<T> list = weaponId == null ? null : map.get(weaponId);
        return list == null ? Collections.<T>emptyList() : list;
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
        HITS.remove(weaponId);
        FIRES.remove(weaponId);
        FRAMES.remove(weaponId);
        BEAMS.remove(weaponId);
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
