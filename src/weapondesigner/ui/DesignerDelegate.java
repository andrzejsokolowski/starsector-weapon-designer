package weapondesigner.ui;

import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate;
import com.fs.starfarer.api.ui.CustomPanelAPI;

public final class DesignerDelegate implements CustomVisualDialogDelegate {
    private final DesignerPanel panel = new DesignerPanel();

    public CustomUIPanelPlugin getCustomPanelPlugin() {
        return panel;
    }

    public void init(CustomPanelAPI customPanel, DialogCallbacks callbacks) {
        callbacks.getPanelFader().setDurationOut(0.3f);
        panel.init(customPanel, callbacks);
    }

    public float getNoiseAlpha() {
        return 0f;
    }

    public void advance(float amount) {
    }

    public void reportDismissed(int option) {
        panel.markDismissed();
    }
}
