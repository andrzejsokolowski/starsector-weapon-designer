package weapondesigner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;

/** One player design. Saved in the campaign as JSON, so it survives mod updates. */
public final class Design {
    public static final int MAX_NAME = 40;
    public static final int MAX_DESCRIPTION = 800;
    /** Borrowed scripts a design can carry: one screen of rows. */
    public static final int MAX_BORROWED = 9;

    /** The reserved weapon this design is written onto, or null before the first save. */
    public String weaponId;
    public String name = "";
    /** The weapon's description, or empty for the default text. */
    public String description = "";
    public WeaponSize size = WeaponSize.SMALL;
    public WeaponType mount = WeaponType.BALLISTIC;
    public DamageType damage = DamageType.KINETIC;
    public Kind kind = Kind.SHOT;
    /** The weapon whose look this design wears, or null to pick one automatically. */
    public String look;
    private final Map<Stat, Integer> rungs = new EnumMap<>(Stat.class);
    private final Map<Script, Integer> levels = new EnumMap<>(Script.class);
    private final List<Borrowed> borrowed = new ArrayList<>();

    public Design() {
        resetStats();
    }

    /** The rung a stat starts on for a new design of this kind. */
    public static int defaultRung(Kind kind, Stat stat) {
        switch (kind) {
            case SHOT:
                switch (stat) {
                    case DAMAGE: return 6;
                    case RATE: return 9;
                    case RANGE: return 8;
                    case FLUX: return 6;
                    case SPEED: return 6;
                    case ACCURACY: return 6;
                    case TURN: return 7;
                    default: return 0;
                }
            case LASER:
                switch (stat) {
                    case DAMAGE: return 6;
                    case RATE: return 11;
                    case RANGE: return 4;
                    case FLUX: return 8;
                    case SPEED: return 7;
                    case ACCURACY: return 12;
                    case TURN: return 9;
                    default: return 0;
                }
            case BEAM:
                switch (stat) {
                    case DAMAGE: return 2;
                    case RANGE: return 12;
                    case FLUX: return 8;
                    case TURN: return 5;
                    default: return 0;
                }
            case MISSILE:
                switch (stat) {
                    case DAMAGE: return 9;
                    case RELOAD: return 12;
                    case AMMO: return 2;
                    case RANGE: return 9;
                    case SPEED: return 4;
                    case GUIDANCE: return 4;
                    case TURN: return 7;
                    default: return 0;
                }
            default:
                return 0;
        }
    }

    /** Puts every stat of the current kind back on its starting rung. Scripts are kept. */
    public void resetStats() {
        rungs.clear();
        for (Stat stat : kind.stats()) rungs.put(stat, defaultRung(kind, stat));
    }

    public int rung(Stat stat) {
        Integer r = rungs.get(stat);
        return r == null ? 0 : Math.max(stat.bottom(kind, size), Math.min(stat.top(kind, size), r));
    }

    public void set(Stat stat, int rung) {
        rungs.put(stat, rung);
    }

    public float value(Stat stat) {
        if (!kind.stats().contains(stat)) return 0f;
        return stat.value(kind, size, rung(stat));
    }

    public int level(Script script) {
        Integer l = levels.get(script);
        return l == null ? 0 : Math.max(0, Math.min(script.top(), l));
    }

    public void setLevel(Script script, int level) {
        if (level <= 0) levels.remove(script);
        else levels.put(script, level);
    }

    public List<Borrowed> borrowed() {
        return Collections.unmodifiableList(borrowed);
    }

    /** Whether this script can be added: room left, the right kind of design, not already taken. */
    public boolean canBorrow(String donorId, Borrowed.Type type) {
        if (borrowed.size() >= MAX_BORROWED || !type.fits(kind)) return false;
        for (Borrowed b : borrowed) if (b.same(donorId, type)) return false;
        return true;
    }

    public void borrow(Borrowed b) {
        if (b != null && canBorrow(b.donorId, b.type)) borrowed.add(b);
    }

    public void unborrow(int index) {
        if (index >= 0 && index < borrowed.size()) borrowed.remove(index);
    }

    /** Drops borrowed scripts this kind of design cannot run (after the kind changes on a new design). */
    public void dropUnfitBorrowed() {
        borrowed.removeIf(b -> !b.type.fits(kind));
    }

    public int activeScripts() {
        int count = 0;
        for (Script script : Script.values()) if (level(script) > 0) count++;
        return count;
    }

    /**
     * Whether a stat can move by one rung. Going up stops only where the numbers would stop making
     * sense (no longer a finite value, or past {@link Costing#MAX_DESIGN_POINTS}); going down stops
     * at the stat's worst value.
     */
    public boolean canStep(Stat stat, int delta) {
        int next = rung(stat) + delta;
        if (next < stat.bottom(kind, size) || next > stat.top(kind, size)) return false;
        float value = stat.value(kind, size, next);
        if (!Float.isFinite(value) || (stat.lowerIsBetter() && stat != Stat.ACCURACY && value <= 0f)) return false;
        Design after = copy();
        after.set(stat, next);
        return Costing.allowsStep(this, after, delta);
    }

    public void step(Stat stat, int delta) {
        if (canStep(stat, delta)) rungs.put(stat, rung(stat) + delta);
    }

    public boolean canStep(Script script, int delta) {
        int next = level(script) + delta;
        if (next < 0 || next > script.top()) return false;
        Design after = copy();
        after.setLevel(script, next);
        return Costing.allowsStep(this, after, delta);
    }

    public void step(Script script, int delta) {
        if (canStep(script, delta)) setLevel(script, level(script) + delta);
    }

    /** The sum of each stat's distance from its starting rung, plus every script level. */
    int stepsFromDefault() {
        int steps = 0;
        for (Stat stat : kind.stats()) steps += rung(stat) - defaultRung(kind, stat);
        for (Script script : Script.values()) steps += level(script);
        return steps;
    }

    public Design copy() {
        Design d = new Design();
        d.weaponId = weaponId;
        d.name = name;
        d.description = description;
        d.size = size;
        d.mount = mount;
        d.damage = damage;
        d.kind = kind;
        d.look = look;
        d.rungs.clear();
        d.rungs.putAll(rungs);
        d.levels.putAll(levels);
        for (Borrowed b : borrowed) d.borrowed.add(b.copy());
        return d;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("weaponId", weaponId);
        json.put("name", name);
        if (description != null && !description.isEmpty()) json.put("description", description);
        json.put("size", size.name());
        json.put("mount", mount.name());
        json.put("damage", damage.name());
        json.put("kind", kind.name());
        if (look != null) json.put("look", look);
        JSONObject stats = new JSONObject();
        for (Map.Entry<Stat, Integer> e : rungs.entrySet()) stats.put(e.getKey().name(), e.getValue().intValue());
        json.put("stats", stats);
        if (!borrowed.isEmpty()) {
            JSONArray list = new JSONArray();
            for (Borrowed b : borrowed) list.put(b.toJson());
            json.put("borrowed", list);
        }
        if (!levels.isEmpty()) {
            JSONObject scripts = new JSONObject();
            for (Map.Entry<Script, Integer> e : levels.entrySet()) scripts.put(e.getKey().name(), e.getValue().intValue());
            json.put("scripts", scripts);
        }
        return json;
    }

    /** Reads a saved design. Unknown or missing fields fall back to defaults instead of failing. */
    public static Design fromJson(JSONObject json) {
        Design d = new Design();
        d.weaponId = json.isNull("weaponId") ? null : json.optString("weaponId", null);
        d.name = json.isNull("name") ? "" : json.optString("name", "");
        d.description = json.isNull("description") ? "" : json.optString("description", "");
        d.size = parse(WeaponSize.class, json.optString("size"), WeaponSize.SMALL);
        d.mount = parse(WeaponType.class, json.optString("mount"), WeaponType.BALLISTIC);
        d.damage = parse(DamageType.class, json.optString("damage"), DamageType.KINETIC);
        Kind fromId = Kind.ofWeaponId(d.weaponId);
        d.kind = fromId != null ? fromId : parse(Kind.class, json.optString("kind"), Kind.SHOT);
        d.look = json.isNull("look") ? null : json.optString("look", null);
        d.resetStats();
        JSONObject stats = json.optJSONObject("stats");
        if (stats != null) {
            for (Stat stat : d.kind.stats()) {
                if (stats.has(stat.name())) d.set(stat, stats.optInt(stat.name(), d.rung(stat)));
            }
        }
        JSONArray list = json.optJSONArray("borrowed");
        if (list != null) {
            for (int i = 0; i < list.length(); i++) d.borrow(Borrowed.fromJson(list.optJSONObject(i)));
        }
        JSONObject scripts = json.optJSONObject("scripts");
        if (scripts != null) {
            for (Script script : Script.values()) {
                if (scripts.has(script.name())) d.setLevel(script, scripts.optInt(script.name(), 0));
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
