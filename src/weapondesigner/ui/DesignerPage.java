package weapondesigner.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import oddiszcommons.manage.ManagePage;
import weapondesigner.WeaponDesignerModPlugin;
import weapondesigner.Workshop;

/**
 * The Weapon designer tab on OddiszCommons' Manage (Y) screen (1.2.0; before that, an option on
 * the colony's menu). It opens while one of the player's colonies qualifies ({@link Workshop});
 * otherwise it says what is missing. A fresh designer is built each time the tab opens.
 */
public final class DesignerPage implements ManagePage {
    public static final String TAB_NAME = "Weapon designer";

    private DesignerPanel open;

    @Override
    public CustomPanelAPI createPanel(float width, float height) {
        panelClosed();
        CustomPanelAPI root = Global.getSettings().createCustom(width, height, new BaseCustomUIPanelPlugin());
        MarketAPI workshop = Workshop.find();
        if (workshop == null) {
            message(root, width, "The weapon designer needs one of your colonies to have Orbital Works, or a building "
                    + "upgraded from it, with a Pristine Nanoforge or a Tenebrian Nanoforge installed.");
            return root;
        }
        try {
            // Picks up changed LunaLib settings: every design is written again before the screen reads them.
            WeaponDesignerModPlugin.rebuildAll();
            DesignerPanel designer = new DesignerPanel();
            CustomPanelAPI inner = Global.getSettings().createCustom(DesignerPanel.PANEL_W, DesignerPanel.PANEL_H, designer);
            root.addComponent(inner).inTL(Math.max(0f, (width - DesignerPanel.PANEL_W) / 2f),
                    Math.max(0f, (height - DesignerPanel.PANEL_H) / 2f));
            designer.init(inner);
            open = designer;
        } catch (Exception e) {
            Global.getLogger(DesignerPage.class).error("Weapon Designer: could not open", e);
            message(root, width, "The weapon designer could not be opened. See starsector.log.");
        }
        return root;
    }

    @Override
    public void panelClosed() {
        if (open != null) open.markDismissed();
        open = null;
    }

    private static void message(CustomPanelAPI root, float width, String text) {
        float w = Math.min(width - 40f, 600f);
        TooltipMakerAPI note = root.createUIElement(w, 80f, false);
        note.addPara(text, Misc.getTextColor(), 0f);
        root.addUIElement(note).inTL(Math.max(20f, (width - w) / 2f), 40f);
    }
}
