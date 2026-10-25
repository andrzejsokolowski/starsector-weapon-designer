package weapondesigner;

import java.util.EnumMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;

/** One player design. Saved in the campaign as JSON, so it survives mod updates. */
public final class Design {
    public static final int MAX_NAME = 40;

    /** The reserved weapon this design is written onto, or null before the first save. */
    public String weaponId;
    public String name = "";
    public WeaponSize size = WeaponSize.SMALL;
    public WeaponType mount = WeaponType.BALLISTIC;
    public DamageType damage = DamageType.KINETIC;
    public Kind kind = Kind.SHOT;
    private final Map<Stat, Integer> rungs = new EnumMap<>(Stat.class);

    public Design() {
        resetStats();
    }

    /** Puts every stat of the current kind back on its starting rung. */
    public void resetStats() {
        rungs.clear();
        switch (kind) {
            case SHOT:
                set(Stat.DAMAGE, 6); set(Stat.RATE, 9); set(Stat.RANGE, 8); set(Stat.FLUX, 6);
                set(Stat.SPEED, 6); set(Stat.ACCURACY, 6); set(Stat.TURN, 7); set(Stat.EMP, 0);
                break;
            case LASER:
                set(Stat.DAMAGE, 6); set(Stat.RATE, 11); set(Stat.RANGE, 4); set(Stat.FLUX, 8);
                set(Stat.SPEED, 7); set(Stat.ACCURACY, 12); set(Stat.TURN, 9); set(Stat.EMP, 0);
                break;
            case BEAM:
                set(Stat.DAMAGE, 2); set(Stat.RANGE, 12); set(Stat.FLUX, 8); set(Stat.TURN, 5); set(Stat.EMP, 0);
                break;
            case MISSILE:
                set(Stat.DAMAGE, 9); set(Stat.SALVO, 0); set(Stat.RELOAD, 12); set(Stat.AMMO, 2);
                set(Stat.REGEN, 0); set(Stat.RANGE, 9); set(Stat.SPEED, 4); set(Stat.GUIDANCE, 4);
                set(Stat.TURN, 7); set(Stat.EMP, 0);
                break;
        }
    }

    public int rung(Stat stat) {
        Integer r = rungs.get(stat);
        int top = stat.ladder(kind, size).length - 1;
        return r == null ? 0 : Math.max(0, Math.min(top, r));
    }

    public void set(Stat stat, int rung) {
        rungs.put(stat, rung);
    }

    public boolean canStep(Stat stat, int delta) {
        int next = rung(stat) + delta;
        return next >= 0 && next < stat.ladder(kind, size).length;
    }

    public void step(Stat stat, int delta) {
        if (canStep(stat, delta)) rungs.put(stat, rung(stat) + delta);
    }

    public float value(Stat stat) {
        if (!kind.stats().contains(stat)) return 0f;
        return stat.ladder(kind, size)[rung(stat)];
    }

    public Design copy() {
        Design d = new Design();
        d.weaponId = weaponId;
        d.name = name;
        d.size = size;
        d.mount = mount;
        d.damage = damage;
        d.kind = kind;
        d.rungs.clear();
        d.rungs.putAll(rungs);
        return d;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("weaponId", weaponId);
        json.put("name", name);
        json.put("size", size.name());
        json.put("mount", mount.name());
        json.put("damage", damage.name());
        json.put("kind", kind.name());
        JSONObject stats = new JSONObject();
        for (Map.Entry<Stat, Integer> e : rungs.entrySet()) stats.put(e.getKey().name(), e.getValue().intValue());
        json.put("stats", stats);
        return json;
    }

    /** Reads a saved design. Unknown or missing fields fall back to defaults instead of failing. */
    public static Design fromJson(JSONObject json) {
        Design d = new Design();
        d.weaponId = json.isNull("weaponId") ? null : json.optString("weaponId", null);
        d.name = json.isNull("name") ? "" : json.optString("name", "");
        d.size = parse(WeaponSize.class, json.optString("size"), WeaponSize.SMALL);
        d.mount = parse(WeaponType.class, json.optString("mount"), WeaponType.BALLISTIC);
        d.damage = parse(DamageType.class, json.optString("damage"), DamageType.KINETIC);
        Kind fromId = Kind.ofWeaponId(d.weaponId);
        d.kind = fromId != null ? fromId : parse(Kind.class, json.optString("kind"), Kind.SHOT);
        d.resetStats();
        JSONObject stats = json.optJSONObject("stats");
        if (stats != null) {
            for (Stat stat : d.kind.stats()) {
                if (stats.has(stat.name())) d.set(stat, stats.optInt(stat.name(), d.rung(stat)));
            }
        }
        return d;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String name, E fallback) {
        if (name == null) return fallback;
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
