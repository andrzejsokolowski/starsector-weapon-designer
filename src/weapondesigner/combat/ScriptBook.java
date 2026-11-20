package weapondesigner.combat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;
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
    /** Donor weapon ids of the borrowed scripts, by type. The scripts themselves are made per battle. */
    private static final Map<String, List<String>> HITS = new HashMap<>();
    private static final Map<String, List<String>> FIRES = new HashMap<>();
    private static final Map<String, List<String>> FRAMES = new HashMap<>();
    private static final Map<String, List<String>> BEAMS = new HashMap<>();
    private static final String HITS_KEY = "weapondesigner_lent_hits";
    private static final String FIRES_KEY = "weapondesigner_lent_fires";

    /**
     * A borrowed on-hit or on-fire script. Made fresh for each battle, like the game makes them:
     * an on-fire script per weapon, an on-hit script per design, so nothing one holds outlives the
     * battle or is shared between weapons.
     */
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

    /** Keeps the borrowed scripts' donors; scripts whose donor is no longer loaded are left out. */
    private static void putBorrowed(Design d) {
        List<String> hits = new ArrayList<>();
        List<String> fires = new ArrayList<>();
        List<String> frames = new ArrayList<>();
        List<String> beams = new ArrayList<>();
        for (Borrowed b : d.borrowed()) {
            WeaponSpecAPI donor;
            try {
                donor = Global.getSettings().getWeaponSpec(b.donorId);
            } catch (Throwable t) {
                continue;
            }
            if (donor == null) continue;
            switch (b.type) {
                case HIT:
                    hits.add(b.donorId);
                    break;
                case FIRE:
                    fires.add(b.donorId);
                    break;
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

    /** A design's borrowed on-hit scripts in this battle, made the first time one of its shots hits. */
    @SuppressWarnings("unchecked")
    public static List<Lent<OnHitEffectPlugin>> borrowedHits(CombatEngineAPI engine, String weaponId) {
        List<String> donors = listOf(HITS, weaponId);
        if (donors.isEmpty() || engine == null) return Collections.emptyList();
        Object stored = engine.getCustomData().get(HITS_KEY);
        if (!(stored instanceof Map)) {
            stored = new HashMap<String, List<Lent<OnHitEffectPlugin>>>();
            engine.getCustomData().put(HITS_KEY, stored);
        }
        Map<String, List<Lent<OnHitEffectPlugin>>> made = (Map<String, List<Lent<OnHitEffectPlugin>>>) stored;
        List<Lent<OnHitEffectPlugin>> lent = made.get(weaponId);
        if (lent == null) {
            lent = new ArrayList<>();
            for (String donorId : donors) {
                Object shot = shotOf(engine, weaponId, donorId, Borrowed.Type.HIT);
                OnHitEffectPlugin script = shot instanceof ProjectileSpecAPI ? ((ProjectileSpecAPI) shot).getOnHitEffect()
                        : shot instanceof MissileSpecAPI ? ((MissileSpecAPI) shot).getOnHitEffect() : null;
                if (script != null) lent.add(new Lent<>(script, Guard.key(weaponId, donorId, Borrowed.Type.HIT.name())));
            }
            made.put(weaponId, lent);
        }
        return lent;
    }

    /** One weapon's borrowed on-fire scripts in this battle, made at its first shot. */
    @SuppressWarnings("unchecked")
    public static List<Lent<OnFireEffectPlugin>> borrowedFires(CombatEngineAPI engine, WeaponAPI weapon, String weaponId) {
        List<String> donors = listOf(FIRES, weaponId);
        if (donors.isEmpty() || engine == null || weapon == null) return Collections.emptyList();
        Object stored = engine.getCustomData().get(FIRES_KEY);
        if (!(stored instanceof Map)) {
            stored = new WeakHashMap<WeaponAPI, List<Lent<OnFireEffectPlugin>>>();
            engine.getCustomData().put(FIRES_KEY, stored);
        }
        Map<WeaponAPI, List<Lent<OnFireEffectPlugin>>> made = (Map<WeaponAPI, List<Lent<OnFireEffectPlugin>>>) stored;
        List<Lent<OnFireEffectPlugin>> lent = made.get(weapon);
        if (lent == null) {
            lent = new ArrayList<>();
            for (String donorId : donors) {
                Object shot = shotOf(engine, weaponId, donorId, Borrowed.Type.FIRE);
                OnFireEffectPlugin script = shot instanceof ProjectileSpecAPI ? ((ProjectileSpecAPI) shot).getOnFireEffect()
                        : shot instanceof MissileSpecAPI ? ((MissileSpecAPI) shot).getOnFireEffect() : null;
                if (script != null) lent.add(new Lent<>(script, Guard.key(weaponId, donorId, Borrowed.Type.FIRE.name())));
            }
            made.put(weapon, lent);
        }
        return lent;
    }

    /** The donor's projectile spec, or null (noted once) when it cannot be had. */
    private static Object shotOf(CombatEngineAPI engine, String weaponId, String donorId, Borrowed.Type type) {
        try {
            WeaponSpecAPI donor = Global.getSettings().getWeaponSpec(donorId);
            return donor == null ? null : donor.getProjectileSpec();
        } catch (Throwable t) {
            Guard.fail(engine, Guard.key(weaponId, donorId, type.name()), t);
            return null;
        }
    }

    private static <T> void store(Map<String, List<T>> map, String weaponId, List<T> list) {
        if (list.isEmpty()) map.remove(weaponId);
        else map.put(weaponId, list);
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
