package weapondesigner;

import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;

/**
 * The numbers a design can set. Each stat is a ladder of values and a design stores its rung, so
 * changing the mount size moves a stat to the same rung of the new size's ladder.
 */
public enum Stat {
    DAMAGE,
    RATE,
    RANGE,
    FLUX,
    SPEED,
    ACCURACY,
    TURN,
    EMP,
    SALVO,
    RELOAD,
    AMMO,
    REGEN,
    GUIDANCE;

    private static final float[] SHOT_DAMAGE = {10, 15, 20, 25, 30, 40, 50, 60, 75, 90, 100, 125, 150, 175, 200,
            250, 300, 400};
    private static final float[] BEAM_DAMAGE = {25, 50, 75, 100, 125, 150, 200, 250, 300, 400, 500};
    private static final float[] MISSILE_DAMAGE = {50, 75, 100, 150, 200, 250, 300, 400, 500, 750, 1000, 1500,
            2000, 2500, 3000, 4000};
    private static final float[] RATES = {0.2f, 0.25f, 0.33f, 0.5f, 0.67f, 0.75f, 1, 1.25f, 1.5f, 2, 2.5f, 3, 4,
            5, 6, 8, 10, 12, 15};
    private static final float[] FLUX_PER_DAMAGE = {2f, 1.75f, 1.5f, 1.3f, 1.2f, 1.1f, 1f, 0.9f, 0.8f, 0.7f, 0.6f,
            0.5f, 0.4f, 0.3f, 0.25f};
    private static final float[] SPREAD = {25, 20, 15, 12, 10, 8, 6, 5, 4, 3, 2, 1, 0};
    private static final float[] TURN_RATES = {5, 7, 10, 12, 15, 20, 25, 30, 40, 50, 60, 80, 100, 120};
    private static final float[] EMP_SHARE = {0, 0.1f, 0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f};
    private static final float[] SALVOS = {1, 2, 3, 4, 5, 6, 8, 10, 12, 16, 20};
    private static final float[] RELOADS = {20, 15, 12, 10, 8, 6, 5, 4, 3, 2.5f, 2, 1.5f, 1, 0.75f, 0.5f};
    private static final float[] AMMO_COUNTS = {1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 25, 30, 40, 50, 60, 80, 100,
            120, 150, 200};
    private static final float[] REGEN_PER_MINUTE = {0, 1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 30, 40, 60};
    private static final float[] GUIDANCE_RATES = {0, 15, 30, 45, 60, 90, 120, 180, 240, 360};

    public float[] ladder(Kind kind, WeaponSize size) {
        switch (this) {
            case DAMAGE:
                if (kind == Kind.MISSILE) return MISSILE_DAMAGE;
                return scaled(kind == Kind.BEAM ? BEAM_DAMAGE : SHOT_DAMAGE, sizeFactor(size));
            case RATE:
                return RATES;
            case RANGE:
                if (kind == Kind.MISSILE) return steps(600, 4000, 200);
                int low = kind == Kind.BEAM ? 400 : 300;
                int high = kind == Kind.BEAM ? 1100 : 900;
                int grow = size == WeaponSize.LARGE ? 2 : size == WeaponSize.MEDIUM ? 1 : 0;
                return steps(low + grow * 100, high + grow * (kind == Kind.BEAM ? 250 : 300), 50);
            case FLUX:
                return FLUX_PER_DAMAGE;
            case SPEED:
                return kind == Kind.MISSILE ? steps(100, 1000, 50) : steps(300, 2000, 100);
            case ACCURACY:
                return SPREAD;
            case TURN:
                return TURN_RATES;
            case EMP:
                return EMP_SHARE;
            case SALVO:
                return SALVOS;
            case RELOAD:
                return RELOADS;
            case AMMO:
                return AMMO_COUNTS;
            case REGEN:
                return REGEN_PER_MINUTE;
            case GUIDANCE:
                return GUIDANCE_RATES;
            default:
                throw new IllegalStateException(name());
        }
    }

    public String label(Kind kind) {
        switch (this) {
            case DAMAGE:
                return kind == Kind.BEAM ? "Damage per second"
                        : kind == Kind.MISSILE ? "Damage per missile" : "Damage per shot";
            case RATE:
                return "Shots per second";
            case RANGE:
                return "Range";
            case FLUX:
                return "Flux per damage";
            case SPEED:
                return kind == Kind.MISSILE ? "Missile speed" : "Shot speed";
            case ACCURACY:
                return "Spread";
            case TURN:
                return "Turn rate";
            case EMP:
                return kind == Kind.BEAM ? "EMP per second" : kind == Kind.MISSILE ? "EMP per missile" : "EMP per shot";
            case SALVO:
                return "Missiles per salvo";
            case RELOAD:
                return "Seconds between salvos";
            case AMMO:
                return "Missiles carried";
            case REGEN:
                return "Missiles regained per minute";
            case GUIDANCE:
                return "Missile turn rate";
            default:
                return name();
        }
    }

    private static float sizeFactor(WeaponSize size) {
        return size == WeaponSize.LARGE ? 4f : size == WeaponSize.MEDIUM ? 2f : 1f;
    }

    private static float[] scaled(float[] base, float factor) {
        float[] out = new float[base.length];
        for (int i = 0; i < base.length; i++) out[i] = base[i] * factor;
        return out;
    }

    private static float[] steps(int from, int to, int step) {
        float[] out = new float[(to - from) / step + 1];
        for (int i = 0; i < out.length; i++) out[i] = from + i * step;
        return out;
    }
}
