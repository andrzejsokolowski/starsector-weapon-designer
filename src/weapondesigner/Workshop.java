package weapondesigner;

import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Items;

/** Which colonies offer the designer: the player's own, with Orbital Works holding a Pristine Nanoforge. */
public final class Workshop {
    private Workshop() {
    }

    public static boolean qualifies(MarketAPI market) {
        if (market == null || !market.isPlayerOwned()) return false;
        Industry works = market.getIndustry(Industries.ORBITALWORKS);
        if (works == null || works.isBuilding()) return false;
        SpecialItemData item = works.getSpecialItem();
        return item != null && Items.PRISTINE_NANOFORGE.equals(item.getId());
    }
}
