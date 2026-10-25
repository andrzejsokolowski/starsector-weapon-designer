package weapondesigner;

import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;

/**
 * After a design changes size or mount type, copies already fitted may sit in slots they no longer
 * fit. Those are taken off and put in the cargo next to the ship, so no loadout is left invalid.
 */
public final class Refits {
    private Refits() {
    }

    /** Returns how many copies were taken off. */
    public static int unequipMisfits(String weaponId) {
        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null) return 0;
        WeaponSpecAPI spec = Global.getSettings().getWeaponSpec(weaponId);
        int removed = 0;
        CargoAPI fleetCargo = Global.getSector().getPlayerFleet().getCargo();
        removed += strip(Global.getSector().getPlayerFleet().getFleetData(), fleetCargo, weaponId, spec);
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            SubmarketAPI storage = market.getSubmarket(Submarkets.SUBMARKET_STORAGE);
            if (storage == null || storage.getCargo() == null) continue;
            CargoAPI cargo = storage.getCargo();
            if (cargo.getMothballedShips() == null) continue;
            removed += strip(cargo.getMothballedShips(), cargo, weaponId, spec);
        }
        return removed;
    }

    private static int strip(FleetDataAPI fleet, CargoAPI cargo, String weaponId, WeaponSpecAPI spec) {
        int removed = 0;
        for (FleetMemberAPI member : fleet.getMembersListCopy()) {
            removed += strip(member.getVariant(), cargo, weaponId, spec);
        }
        return removed;
    }

    private static int strip(ShipVariantAPI variant, CargoAPI cargo, String weaponId, WeaponSpecAPI spec) {
        if (variant == null) return 0;
        int removed = 0;
        for (String slotId : new ArrayList<>(variant.getNonBuiltInWeaponSlots())) {
            if (!weaponId.equals(variant.getWeaponId(slotId))) continue;
            WeaponSlotAPI slot = variant.getSlot(slotId);
            if (slot != null && slot.weaponFits(spec)) continue;
            variant.clearSlot(slotId);
            cargo.addWeapons(weaponId, 1);
            removed++;
        }
        List<String> modules = variant.getModuleSlots();
        if (modules != null) {
            for (String moduleSlot : modules) {
                removed += strip(variant.getModuleVariant(moduleSlot), cargo, weaponId, spec);
            }
        }
        return removed;
    }
}
