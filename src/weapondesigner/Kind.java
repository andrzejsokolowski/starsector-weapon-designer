package weapondesigner;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What a weapon fires. The kind is fixed by the reserved weapon's data files, so a design keeps
 * its kind once saved, and each kind has its own stock of blank weapons.
 */
public enum Kind {
    SHOT("shot", "Ballistic"),
    LASER("laser", "Laser"),
    BEAM("beam", "Beam"),
    MISSILE("missile", "Missile");

    /** Blank weapons per kind. Must match RESERVE in tools/generate_reserve.py. */
    public static final int RESERVE = 10;

    private static final Map<Kind, List<Stat>> STATS = new EnumMap<>(Kind.class);
    static {
        STATS.put(SHOT, List.of(Stat.DAMAGE, Stat.RATE, Stat.RANGE, Stat.FLUX, Stat.SPEED, Stat.ACCURACY,
                Stat.TURN, Stat.EMP));
        STATS.put(LASER, STATS.get(SHOT));
        STATS.put(BEAM, List.of(Stat.DAMAGE, Stat.RANGE, Stat.FLUX, Stat.TURN, Stat.EMP));
        STATS.put(MISSILE, List.of(Stat.DAMAGE, Stat.SALVO, Stat.RELOAD, Stat.AMMO, Stat.REGEN, Stat.RANGE,
                Stat.SPEED, Stat.GUIDANCE, Stat.TURN, Stat.EMP));
    }

    public final String key;
    public final String label;

    Kind(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public List<Stat> stats() {
        return STATS.get(this);
    }

    public String weaponId(int index) {
        return String.format("wd_%s_%03d", key, index);
    }

    /** The number at the end of a reserved weapon id (wd_shot_007 is 7), or -1. */
    public static int indexOf(String weaponId) {
        Kind kind = ofWeaponId(weaponId);
        if (kind == null) return -1;
        try {
            return Integer.parseInt(weaponId.substring(("wd_" + kind.key + "_").length()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** The kind whose reserve holds this weapon id, or null for any other weapon. */
    public static Kind ofWeaponId(String weaponId) {
        if (weaponId == null || !weaponId.startsWith("wd_")) return null;
        for (Kind kind : values()) {
            if (weaponId.startsWith("wd_" + kind.key + "_")) return kind;
        }
        return null;
    }
}
