package weapondesigner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;

/**
 * Weapon definitions live in memory, not in the save, and are shared by every save loaded in one
 * session. So every load rebuilds all reserved weapons: the save's designs on theirs, blank on the rest.
 */
public class WeaponDesignerModPlugin extends BaseModPlugin {

    @Override
    public void onApplicationLoad() {
        Looks.index();
        Borrowing.index();
        int missing = 0;
        for (Kind kind : Kind.values()) {
            for (int i = 1; i <= Kind.RESERVE; i++) {
                try {
                    Global.getSettings().getWeaponSpec(kind.weaponId(i));
                } catch (Exception e) {
                    missing++;
                }
            }
        }
        if (missing > 0) {
            Global.getLogger(WeaponDesignerModPlugin.class)
                    .error("Weapon Designer: " + missing + " reserved weapons are missing. Reinstall the mod.");
        }
    }

    @Override
    public void onGameLoad(boolean newGame) {
        rebuildAll();
    }

    public static void rebuildAll() {
        Settings.refresh();
        List<Design> designs = DesignStore.load();
        if (DesignStore.fitReserve(designs)) DesignStore.save(designs);
        Map<String, Design> byWeapon = new HashMap<>();
        for (Design d : designs) {
            Borrowing.refresh(d);
            byWeapon.put(d.weaponId, d);
        }
        for (Kind kind : Kind.values()) {
            for (int i = 1; i <= Kind.RESERVE; i++) {
                String id = kind.weaponId(i);
                try {
                    Design d = byWeapon.get(id);
                    if (d != null) {
                        SpecWriter.apply(d);
                        Global.getSector().getPlayerFaction().addKnownWeapon(id, false);
                    } else {
                        SpecWriter.blank(id);
                    }
                } catch (Exception e) {
                    Global.getLogger(WeaponDesignerModPlugin.class).error("Weapon Designer: could not set up " + id, e);
                }
            }
        }
    }
}
