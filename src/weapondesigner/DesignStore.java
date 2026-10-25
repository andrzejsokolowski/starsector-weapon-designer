package weapondesigner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import com.fs.starfarer.api.Global;

/**
 * The campaign's designs, kept in the sector's persistent data as one JSON string. A string keeps
 * the save free of this mod's classes, so the save still loads if a design fails to read.
 */
public final class DesignStore {
    private static final String KEY = "weapondesigner_designs";

    private DesignStore() {
    }

    public static List<Design> load() {
        List<Design> designs = new ArrayList<>();
        if (Global.getSector() == null) return designs;
        Object raw = Global.getSector().getPersistentData().get(KEY);
        if (!(raw instanceof String)) return designs;
        try {
            JSONArray array = new JSONArray((String) raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject json = array.optJSONObject(i);
                if (json == null) continue;
                Design d = Design.fromJson(json);
                if (Kind.ofWeaponId(d.weaponId) != null) designs.add(d);
            }
        } catch (Exception e) {
            Global.getLogger(DesignStore.class).error("Weapon Designer: could not read the saved designs", e);
        }
        return designs;
    }

    public static void save(List<Design> designs) {
        if (Global.getSector() == null) return;
        try {
            JSONArray array = new JSONArray();
            for (Design d : designs) array.put(d.toJson());
            Map<String, Object> data = Global.getSector().getPersistentData();
            data.put(KEY, array.toString());
        } catch (Exception e) {
            Global.getLogger(DesignStore.class).error("Weapon Designer: could not store the designs", e);
        }
    }

    /**
     * Moves designs whose weapon is past the end of the reserve (0.2.x kept 100 per kind, now 10)
     * onto a free blank weapon of their kind. Designs with no free weapon left are dropped.
     * Returns true when anything changed.
     */
    public static boolean fitReserve(List<Design> designs) {
        boolean changed = false;
        for (int i = 0; i < designs.size(); i++) {
            Design d = designs.get(i);
            int index = Kind.indexOf(d.weaponId);
            if (index >= 1 && index <= Kind.RESERVE) continue;
            String free = freeWeaponId(d.kind, designs);
            changed = true;
            if (free != null) {
                Global.getLogger(DesignStore.class).info("Weapon Designer: moved " + d.name + " from " + d.weaponId + " to " + free);
                d.weaponId = free;
            } else {
                Global.getLogger(DesignStore.class).warn("Weapon Designer: no blank weapon left for " + d.name + ", dropped");
                designs.remove(i--);
            }
        }
        return changed;
    }

    /** The first blank weapon of this kind no design uses yet, or null when the reserve is full. */
    public static String freeWeaponId(Kind kind, List<Design> designs) {
        for (int i = 1; i <= Kind.RESERVE; i++) {
            String id = kind.weaponId(i);
            boolean used = false;
            for (Design d : designs) {
                if (id.equals(d.weaponId)) {
                    used = true;
                    break;
                }
            }
            if (!used) return id;
        }
        return null;
    }
}
