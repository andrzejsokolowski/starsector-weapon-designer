package weapondesigner;

import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;

/**
 * Design points and weapon OP.
 *
 * Every stat rung and script level costs design points (DP). The core is fitted to the base
 * game's guns (worth grows with DPS^0.9 and range, with a premium per size step), so a design
 * that copies a vanilla weapon is worth about what that weapon costs: 10 DP per OP of worth.
 * Flux efficiency, spread, shot speed and turn rate are free in the base game because every gun
 * is a fixed package. Here they are bought, so they carry their own multipliers.
 *
 * On top of that worth, every step away from a stat's starting rung adds or removes one DP and
 * every script level adds one, so a step never shows a change of 0. The weapon's OP is a share of
 * its design points (LunaLib setting, 10% by default), and its price follows its worth.
 */
public final class Costing {
    /** Design points per OP of worth. At the default conversion rate (10%) one OP costs 10 DP. */
    public static final int DP_PER_OP = 10;
    /** Far past anything sensible. Stops the numbers from running out of room. */
    public static final long MAX_DESIGN_POINTS = 100_000_000L;
    /**
     * Most OP the game lets a weapon cost: its refit screen calls anything at 1000 or more "not
     * installable" (FighterPickerDialog.UNINSTALLABLE_OP, 1000 in 0.98a), whatever the slot.
     */
    public static final int GAME_MAX_OP = 999;
    /**
     * Most credits a copy costs. The game adds up an order in whole numbers, so a higher price
     * times a big order could wrap around to a negative total.
     */
    public static final int MAX_PRICE = 10_000_000;

    private Costing() {
    }

    /** Design points before the floor of 1, so a step's change is always visible. */
    public static long designPointsExact(Design d) {
        double worth = DP_PER_OP * (double) raw(d) * scriptMult(d);
        if (!(worth < MAX_DESIGN_POINTS * 2.0)) return MAX_DESIGN_POINTS * 2;
        long borrowed = 0;
        for (Borrowed b : d.borrowed()) borrowed += borrowedPoints(b);
        return Math.round(worth) + d.stepsFromDefault() + borrowed;
    }

    /**
     * A borrowed script costs a share of the OP of the weapon it comes from (LunaLib setting, 50%
     * by default), in design points, at least 1.
     */
    public static long borrowedPoints(Borrowed b) {
        double points = b.donorOp * DP_PER_OP * Settings.borrowPercent() / 100.0;
        return Math.max(1L, Math.min(MAX_DESIGN_POINTS, Math.round(points)));
    }

    /** Can go below 1 (to 0 or less) for designs worse than the starting point everywhere. */
    public static int designPoints(Design d) {
        return (int) Math.max(-MAX_DESIGN_POINTS, Math.min(MAX_DESIGN_POINTS, designPointsExact(d)));
    }

    /** The OP written onto the weapon: the design's share, at least 1, at most the game's limit. */
    public static int op(Design d) {
        return Math.min(GAME_MAX_OP, opUncapped(d));
    }

    /** The design's share of OP before the game's limit. */
    public static int opUncapped(Design d) {
        return op(designPoints(d), Settings.opRate());
    }

    public static int op(long designPoints, int ratePercent) {
        return (int) Math.max(1L, Math.round(designPoints * (double) ratePercent / 100.0));
    }

    /** True when the weapon OP limit setting is on and this design is over it. */
    public static boolean overLimit(Design d) {
        return Settings.opLimit() > 0 && op(d) > Settings.opLimit();
    }

    /** Steps stay inside the safety ceiling both ways; anything in between is the player's call. */
    static boolean allowsStep(Design before, Design after, int delta) {
        long next = designPointsExact(after);
        return delta > 0 ? next <= MAX_DESIGN_POINTS : next >= -MAX_DESIGN_POINTS;
    }

    /** Production cost of one copy, in credits. Follows the design's worth, not its OP setting. */
    public static int price(Design d) {
        double worth = Math.max(0, designPoints(d)) / (double) DP_PER_OP;
        double base = Math.max(50.0, Math.round(40.0 * Math.pow(worth, 1.5) / 10.0) * 10.0);
        double scaled = Math.round(base * Settings.pricePercent() / 100.0 / 10.0) * 10.0;
        return (int) Math.max(10.0, Math.min(MAX_PRICE, scaled));
    }

    /** What the scripts add to the stats' worth: 1 with none. */
    public static float scriptMult(Design d) {
        float mult = 1f;
        for (Script script : Script.values()) mult += script.worth(d.level(script));
        return mult;
    }

    public static float raw(Design d) {
        switch (d.kind) {
            case BEAM:
                return beam(d);
            case MISSILE:
                return missile(d);
            default:
                return shot(d);
        }
    }

    public static float damagePerSecond(Design d) {
        switch (d.kind) {
            case BEAM:
                return d.value(Stat.DAMAGE);
            case MISSILE: {
                float salvo = Math.min(d.value(Stat.SALVO), d.value(Stat.AMMO));
                return d.value(Stat.DAMAGE) * salvo / d.value(Stat.RELOAD);
            }
            default:
                return d.value(Stat.DAMAGE) * d.value(Stat.RATE);
        }
    }

    public static float fluxPerSecond(Design d) {
        if (d.kind == Kind.MISSILE) return 0f;
        return damagePerSecond(d) * d.value(Stat.FLUX);
    }

    public static float empPerShot(Design d) {
        return d.value(Stat.DAMAGE) * d.value(Stat.EMP);
    }

    public static float empPerSecond(Design d) {
        return damagePerSecond(d) * d.value(Stat.EMP);
    }

    /** Seconds a missile flies before it runs out of fuel. */
    public static float flightTime(Design d) {
        return d.value(Stat.RANGE) / Math.max(1f, d.value(Stat.SPEED)) + 0.5f;
    }

    public static float totalDamage(Design d) {
        return d.value(Stat.DAMAGE) * d.value(Stat.AMMO);
    }

    private static float shotScale(WeaponSize size) {
        return size == WeaponSize.LARGE ? 5.0f : size == WeaponSize.MEDIUM ? 4.6f : 4.0f;
    }

    /**
     * Big hits get through armor and small ones bounce off it, so per-shot damage is worth more
     * than its DPS for damage types that work on armor. 1.0 at 100 damage per shot. Kinetic is
     * left out: it is meant for shields, where the size of each hit does not matter.
     */
    private static float hitMult(DamageType type, float damage) {
        if (type == DamageType.KINETIC) return 1f;
        return pow(2f * damage / (damage + 150f) / 0.8f, 0.45f);
    }

    private static float beamScale(WeaponSize size) {
        return size == WeaponSize.LARGE ? 3.45f : size == WeaponSize.MEDIUM ? 3.3f : 3.0f;
    }

    private static float missileScale(WeaponSize size) {
        return size == WeaponSize.LARGE ? 3.0f : size == WeaponSize.MEDIUM ? 2.3f : 2.0f;
    }

    static float typeMult(DamageType type) {
        if (type == DamageType.FRAGMENTATION) return 0.45f;
        if (type == DamageType.ENERGY) return 1.05f;
        return 1f;
    }

    private static float shot(Design d) {
        float k = shotScale(d.size);
        float dps = damagePerSecond(d) * typeMult(d.damage);
        float range = d.value(Stat.RANGE) / 700f;
        float core = k * pow(dps / 100f, 0.9f) * range
                * hitMult(d.damage, d.value(Stat.DAMAGE))
                * pow(1f / d.value(Stat.FLUX), 0.5f)
                * accuracyMult(d.value(Stat.ACCURACY))
                * pow(d.value(Stat.SPEED) / 900f, 0.25f)
                * pow(d.value(Stat.TURN) / 30f, 0.08f);
        float emp = k * pow(empPerSecond(d) / 100f, 0.9f) * range * 0.3f;
        return core + emp;
    }

    /**
     * 1 at the base game's typical 6 degrees of spread, a little more for tighter guns, falling
     * toward 0 (never below) for wild ones, so even 360 degrees of spread costs something.
     */
    private static float accuracyMult(float spread) {
        if (spread <= 6f) return 1f + (6f - spread) * 0.015f;
        return 1f / (1f + (spread - 6f) * 0.015f);
    }

    private static float beam(Design d) {
        float k = beamScale(d.size);
        float dps = d.value(Stat.DAMAGE) * typeMult(d.damage);
        float range = d.value(Stat.RANGE) / 700f;
        float core = k * pow(dps / 100f, 0.9f) * range
                * pow(1f / d.value(Stat.FLUX), 0.5f)
                * pow(d.value(Stat.TURN) / 30f, 0.08f);
        float emp = k * pow(empPerSecond(d) / 100f, 0.9f) * range * 0.3f;
        return core + emp;
    }

    private static float missile(Design d) {
        float m = missileScale(d.size);
        float damage = d.value(Stat.DAMAGE);
        float type = typeMult(d.damage);
        float ammo = d.value(Stat.AMMO);
        float salvo = Math.min(d.value(Stat.SALVO), ammo);
        float rangeMult = pow(d.value(Stat.RANGE) / 2000f, 0.4f);
        float main = m * (float) Math.sqrt(damage * type * ammo / 1000f)
                * (0.6f + 0.55f * (float) Math.sqrt(d.value(Stat.GUIDANCE) / 120f))
                * pow(d.value(Stat.SPEED) / 400f, 0.3f)
                * rangeMult
                * pow(2f * damage / (damage + 150f), 0.3f)
                * pow(Math.max(0.5f, salvo * damage / d.value(Stat.RELOAD) / 300f), 0.1f)
                * pow(d.value(Stat.TURN) / 30f, 0.05f);
        float regenDps = damage * type * d.value(Stat.REGEN) / 60f;
        float regen = m * 1.5f * pow(regenDps / 100f, 0.9f) * rangeMult;
        float emp = m * 0.3f * (float) Math.sqrt(damage * d.value(Stat.EMP) * ammo / 1000f);
        return main + regen + emp;
    }

    private static float pow(float base, float exponent) {
        if (base <= 0f) return 0f;
        return (float) Math.pow(base, exponent);
    }
}
