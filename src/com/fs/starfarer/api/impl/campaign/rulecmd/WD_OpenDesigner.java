package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;

import weapondesigner.WeaponDesignerModPlugin;
import weapondesigner.ui.DesignerDelegate;
import weapondesigner.ui.DesignerPanel;

/**
 * Opens the designer, named by data/campaign/rules.csv.
 *
 * Lives in this package because that is where the rules engine looks command classes up by
 * their plain name.
 */
public class WD_OpenDesigner extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params,
                           Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;
        try {
            // Picks up changed LunaLib settings: every design is written again before the screen reads them.
            WeaponDesignerModPlugin.rebuildAll();
            dialog.showCustomVisualDialog(DesignerPanel.PANEL_W, DesignerPanel.PANEL_H, new DesignerDelegate());
        } catch (Exception e) {
            Global.getLogger(WD_OpenDesigner.class).error("Weapon Designer: could not open", e);
            dialog.getTextPanel().addPara("The weapon designer could not be opened. See starsector.log.", Color.RED);
        }
        return true;
    }
}
