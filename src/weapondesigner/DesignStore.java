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
