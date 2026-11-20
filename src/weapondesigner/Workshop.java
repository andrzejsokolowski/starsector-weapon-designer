package weapondesigner;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Which colonies unlock the designer (its Manage tab opens while one does): the player's own, with Orbital Works or any building upgraded
 * from it (Vaults of Knowledge's Orbital Skunkworks and Fleetwork Facilities, for example) holding
 * a Pristine Nanoforge or Vaults of Knowledge's Tenebrian Nanoforge.
 */
public final class Workshop {
    /** Vaults of Knowledge's Tenebrian Nanoforge. */
    static final String TENEBRIAN_NANOFORGE = "aotd_shrouded_nanoforge";

    private Workshop() {
    }

    /** The player's first colony that offers the designer, or null when none does. */
    public static MarketAPI find() {
        for (MarketAPI market : Misc.getPlayerMarkets(false)) {
            if (qualifies(market)) return market;
        }
        return null;
    }

    public static boolean unlocks(String itemId) {
        return Items.PRISTINE_NANOFORGE.equals(itemId) || TENEBRIAN_NANOFORGE.equals(itemId);
    }

    public static boolean qualifies(MarketAPI market) {
        if (market == null || !market.isPlayerOwned()) return false;
        for (Industry industry : market.getIndustries()) {
            if (!fromOrbitalWorks(industry.getSpec())) continue;
            // A new building under construction does not count yet. An upgrade in progress does:
            // the old building keeps working until the upgrade is done.
            if (industry.isBuilding() && !industry.isUpgrading()) continue;
            SpecialItemData item = industry.getSpecialItem();
            if (item != null && unlocks(item.getId())) return true;
        }
        return false;
    }

    /** True for Orbital Works and for anything whose chain of downgrades leads back to it. */
    static boolean fromOrbitalWorks(IndustrySpecAPI spec) {
        for (int step = 0; spec != null && step < 10; step++) {
            if (Industries.ORBITALWORKS.equals(spec.getId())) return true;
            String downgrade = spec.getDowngrade();
            if (downgrade == null || downgrade.isEmpty()) return false;
            spec = Global.getSettings().getIndustrySpec(downgrade);
        }
        return false;
    }
}
