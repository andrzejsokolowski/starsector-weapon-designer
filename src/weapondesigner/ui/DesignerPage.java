package weapondesigner.ui;

import java.lang.ref.WeakReference;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import oddiszcommons.manage.ManagePage;
import weapondesigner.WeaponDesignerModPlugin;
import weapondesigner.Workshop;

/**
 * The Weapon designer tab on OddiszCommons' Manage (Y) screen (1.2.0; before that, an option on
 * the colony's menu). It opens while one of the player's colonies qualifies ({@link Workshop});
 * otherwise it says what is missing. A fresh designer is built each time the tab opens, carrying
 * on with the draft the player left (1.2.1), as long as the same campaign is loaded.
 */
public final class DesignerPage implements ManagePage {
    public static final String TAB_NAME = "Weapon designer";

    private DesignerPanel open;
    private DesignerPanel.View left;
    /** The campaign {@link #left} belongs to; weak, so a closed campaign is not kept in memory. */
    private WeakReference<SectorAPI> leftIn = new WeakReference<>(null);

    @Override
    public CustomPanelAPI createPanel(float width, float height) {
        panelClosed();
        Holder holder = new Holder(width, height);
        CustomPanelAPI root = Global.getSettings().createCustom(width, height, holder);
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
            if (leftIn.get() == Global.getSector()) designer.restore(left);
            CustomPanelAPI inner = Global.getSettings().createCustom(DesignerPanel.PANEL_W, DesignerPanel.PANEL_H, designer);
            // Built before it goes on screen, so a failure leaves nothing half-made behind.
            designer.init(inner);
            root.addComponent(inner).inTL(holder.x(null), holder.y(null));
            holder.inner = inner;
            open = designer;
        } catch (Exception e) {
            Global.getLogger(DesignerPage.class).error("Weapon Designer: could not open", e);
            message(root, width, "The weapon designer could not be opened. See starsector.log.");
        }
        return root;
    }

    @Override
    public void panelClosed() {
        if (open != null) {
            try {
                left = open.view();
                leftIn = new WeakReference<>(Global.getSector());
            } catch (RuntimeException e) {
                left = null;
            }
            open.markDismissed();
        }
        open = null;
    }

    private static void message(CustomPanelAPI root, float width, String text) {
        float w = Math.min(width - 40f, 600f);
        TooltipMakerAPI note = root.createUIElement(w, 80f, false);
        note.addPara(text, Misc.getTextColor(), 0f);
        root.addUIElement(note).inTL(Math.max(20f, (width - w) / 2f), 40f);
    }

    /**
     * Centers the designer in the tab. A tab smaller than the designer (small screens, or the
     * intel screen's Manage window) gets it centered too and then kept on the screen, so no
     * control ends up out of reach.
     */
    private static final class Holder extends BaseCustomUIPanelPlugin {
        private final float width, height;
        CustomPanelAPI inner;

        Holder(float width, float height) {
            this.width = width;
            this.height = height;
        }

        float x(PositionAPI at) {
            float x = (width - DesignerPanel.PANEL_W) / 2f;
            if (at == null) return x;
            float screen = Global.getSettings().getScreenWidth();
            return Math.max(-at.getX(), Math.min(screen - DesignerPanel.PANEL_W - at.getX(), x));
        }

        float y(PositionAPI at) {
            float y = (height - DesignerPanel.PANEL_H) / 2f;
            if (at == null) return y;
            float screen = Global.getSettings().getScreenHeight();
            float top = at.getY() + height;
            // Offsets go down from the tab's top; screen positions go up from the bottom.
            return Math.min(top - DesignerPanel.PANEL_H, Math.max(top - screen, y));
        }

        @Override
        public void positionChanged(PositionAPI position) {
            if (inner == null || position == null) return;
            inner.getPosition().inTL(x(position), y(position));
        }
    }
}
