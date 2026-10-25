package weapondesigner;

import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;

/**
 * Weapon OP: what a design costs to mount, and the budget its stats are bought with.
 *
 * The core is fitted to the base game's guns (OP grows with DPS^0.9 and range, with a premium
 * per size step), so a design that copies a vanilla weapon costs about what that weapon costs.
 * Flux efficiency, spread, shot speed and turn rate are free in the base game because every gun
 * is a fixed package. Here they are bought, so they carry their own multipliers.
 */
public final class Costing {
    private Costing() {
    }

    /** Most weapon OP a design of this size may use. */
    public static int cap(WeaponSize size) {
        return size == WeaponSize.LARGE ? 40 : size == WeaponSize.MEDIUM ? 20 : 10;
    }

    public static int op(Design d) {
        return Math.max(1, Math.round(raw(d)));
    }

    public static boolean withinCap(Design d) {
        return op(d) <= cap(d.size);
    }

    /** Production cost of one copy, in credits. */
    public static int price(Design d) {
        int op = op(d);
        return Math.max(50, Math.round(40f * (float) Math.pow(op, 1.5) / 10f) * 10);
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
        return d.value(Stat.RANGE) / Math.max(50f, d.value(Stat.SPEED)) + 0.5f;
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
                * (1f + (6f - d.value(Stat.ACCURACY)) * 0.015f)
                * pow(d.value(Stat.SPEED) / 900f, 0.25f)
                * pow(d.value(Stat.TURN) / 30f, 0.08f);
        float emp = k * pow(empPerSecond(d) / 100f, 0.9f) * range * 0.3f;
        return core + emp;
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
