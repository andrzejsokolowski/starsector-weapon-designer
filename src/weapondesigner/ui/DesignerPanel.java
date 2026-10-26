package weapondesigner.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.lwjgl.Sys;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate.DialogCallbacks;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.loading.ProjectileWeaponSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;

import weapondesigner.Costing;
import weapondesigner.Design;
import weapondesigner.DesignStore;
import weapondesigner.Kind;
import weapondesigner.Looks;
import weapondesigner.Refits;
import weapondesigner.Script;
import weapondesigner.Settings;
import weapondesigner.SpecWriter;
import weapondesigner.Stat;
import weapondesigner.Text;

/**
 * The designer screen. Controls are drawn and hit-tested here rather than with the game's
 * tooltip buttons, the same way as Gambling Den's tables, so every click lands where it is drawn.
 *
 * The middle area shows one of four things: the stat rows, the script rows, the look grid or the
 * description editor. The tabs pick stats or scripts; Change look and Edit description lay their
 * own view over the rows until Done.
 */
public final class DesignerPanel extends BaseCustomUIPanelPlugin {
    public static final float PANEL_W = 1100, PANEL_H = 680;

    private static final float LIST_X = 16, LIST_W = 236, ROW_Y = 60, ROW_STEP = 34, ROW_H = 30;
    private static final int LIST_ROWS = 16;
    private static final float MID_X = 268, MID_W = 540, CHOICE_X = 364, CHOICE_W = 444;
    private static final float TAB_Y = 200, STAT_Y = 244, STAT_STEP = 34;
    private static final int STAT_ROWS = 10;
    private static final float AREA_H = STAT_ROWS * STAT_STEP, HINT_Y = 598;
    private static final float SIDE_X = 824, SIDE_W = 260, PREVIEW_H = 160;
    private static final float SUMMARY_Y = 256, SUMMARY_STEP = 24;
    private static final int SUMMARY_ROWS = 7;
    private static final int GRID_COLS = 6, GRID_ROWS = 4, GRID_CELLS = GRID_COLS * GRID_ROWS;
    private static final float CELL_W = 90, CELL_H = 78, LOOK_NAV_Y = 562;
    /** Steps a shift-click takes. */
    private static final int BIG_STEP = 10;
    /** Longest unbroken word in a description, so it wraps inside its box and the tooltip. */
    private static final int MAX_WORD = 40;

    private static final Color BG = new Color(12, 16, 22);
    private static final Color AREA = new Color(20, 27, 35);
    private static final Color BUTTON = new Color(32, 43, 52);
    private static final Color BUTTON_HOVER = new Color(52, 70, 80);
    private static final Color BUTTON_ON = new Color(70, 92, 64);
    private static final Color BUTTON_OFF = new Color(22, 26, 32);

    private static final WeaponSize[] SIZES = {WeaponSize.SMALL, WeaponSize.MEDIUM, WeaponSize.LARGE};
    private static final WeaponType[] MOUNTS = {WeaponType.BALLISTIC, WeaponType.ENERGY, WeaponType.MISSILE};
    private static final DamageType[] DAMAGES = {DamageType.KINETIC, DamageType.HIGH_EXPLOSIVE, DamageType.ENERGY,
            DamageType.FRAGMENTATION};

    private enum Page { STATS, SCRIPTS }

    private enum Overlay { NONE, LOOKS, TEXT }

    private enum Focus { NONE, NAME, TEXT }

    private final List<Design> designs = DesignStore.load();
    private final List<Btn> buttons = new ArrayList<>();
    private final LabelAPI[] rowNames = new LabelAPI[LIST_ROWS];
    private final LabelAPI[] rowCosts = new LabelAPI[LIST_ROWS];
    private final LabelAPI[] statNames = new LabelAPI[STAT_ROWS];
    private final LabelAPI[] statValues = new LabelAPI[STAT_ROWS];
    private final LabelAPI[] summaryNames = new LabelAPI[SUMMARY_ROWS];
    private final LabelAPI[] summaryValues = new LabelAPI[SUMMARY_ROWS];

    private CustomPanelAPI panel;
    private DialogCallbacks callbacks;
    private PositionAPI position;
    private LabelAPI nameText, status, hint, descriptionText;
    private Design draft = new Design();
    private int selected = -1;
    private int scroll;
    private Page page = Page.STATS;
    private Overlay overlay = Overlay.NONE;
    private Focus focus = Focus.NONE;
    private boolean dismissed, wasMouseDown;
    private String message = "";
    private boolean messageBad;
    private float caretTimer;
    private int hoveredRow = -1;
    private final Art preview = new Art();
    private WeaponSpecAPI previewDonor;
    private LabelAPI lookLabel;
    private int lookPage, hoveredCell = -1;
    private List<WeaponSpecAPI> lookChoices = new ArrayList<>();
    private final Art[] cellArt = new Art[GRID_CELLS];
    private String cellArtKey = "";

    /** A weapon's turret sprites, layered, with their natural sizes. */
    private static final class Art {
        final List<SpriteAPI> sprites = new ArrayList<>();
        final List<float[]> sizes = new ArrayList<>();

        void load(WeaponSpecAPI weapon) {
            sprites.clear();
            sizes.clear();
            if (weapon == null) return;
            try {
                List<String> paths = new ArrayList<>();
                if (weapon.getTurretUnderSpriteName() != null) paths.add(weapon.getTurretUnderSpriteName());
                paths.add(weapon.getTurretSpriteName());
                if (weapon instanceof ProjectileWeaponSpecAPI
                        && ((ProjectileWeaponSpecAPI) weapon).getTurretGunSpriteName() != null)
                    paths.add(((ProjectileWeaponSpecAPI) weapon).getTurretGunSpriteName());
                for (String path : paths) {
                    SpriteAPI sprite = Global.getSettings().getSprite(path);
                    sprites.add(sprite);
                    sizes.add(new float[]{sprite.getWidth(), sprite.getHeight()});
                }
            } catch (Exception e) {
                sprites.clear();
                sizes.clear();
            }
        }

        /** Draws the sprites centred on a point, scaled to fit the box, at most maxScale. */
        void render(float cx, float cy, float boxW, float boxH, float maxScale, float alpha) {
            float w = 0, h = 0;
            for (float[] size : sizes) {
                w = Math.max(w, size[0]);
                h = Math.max(h, size[1]);
            }
            if (w <= 0 || h <= 0) return;
            float scale = Math.min(maxScale, Math.min(boxW / w, boxH / h));
            for (int i = 0; i < sprites.size(); i++) {
                SpriteAPI s = sprites.get(i);
                s.setSize(sizes.get(i)[0] * scale, sizes.get(i)[1] * scale);
                s.setAlphaMult(alpha);
                s.setNormalBlend();
                s.renderAtCenter(cx, cy);
            }
        }
    }

    private static final class Btn {
        final String action;
        final float x, y, w, h;
        LabelAPI label;
        boolean enabled = true, checked, hovered;

        Btn(String action, float x, float y, float w, float h) {
            this.action = action;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        boolean contains(float px, float py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }
    }

    public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
        this.panel = panel;
        this.callbacks = callbacks;
        wasMouseDown = Mouse.isCreated() && Mouse.isButtonDown(0);

        button("new", "New design", LIST_X, 16, LIST_W, 32);
        for (int i = 0; i < LIST_ROWS; i++) {
            float y = ROW_Y + i * ROW_STEP;
            Btn row = new Btn("row:" + i, LIST_X, y, LIST_W, ROW_H);
            buttons.add(row);
            rowNames[i] = label("", Misc.getTextColor(), LIST_X + 10, y + 6, LIST_W - 80, 18, Alignment.LMID);
            rowCosts[i] = label("", Misc.getGrayColor(), LIST_X + LIST_W - 74, y + 6, 66, 18, Alignment.RMID);
        }
        button("up", "Up", LIST_X, 612, (LIST_W - 8) / 2, 32);
        button("down", "Down", LIST_X + (LIST_W + 8) / 2, 612, (LIST_W - 8) / 2, 32);

        label("Name", Misc.getGrayColor(), MID_X, 22, 90, 18, Alignment.LMID);
        buttons.add(new Btn("name", CHOICE_X, 16, CHOICE_W, 30));
        nameText = label("", Misc.getTextColor(), CHOICE_X + 10, 22, CHOICE_W - 20, 18, Alignment.LMID);

        choiceRow("Size", 56, "size:", new String[]{"Small", "Medium", "Large"});
        choiceRow("Mount", 92, "mount:", new String[]{"Ballistic", "Energy", "Missile"});
        choiceRow("Damage", 128, "damage:", new String[]{"Kinetic", "High Explosive", "Energy", "Fragmentation"});
        String[] kinds = new String[Kind.values().length];
        for (int i = 0; i < kinds.length; i++) kinds[i] = Kind.values()[i].label;
        choiceRow("Projectile", 164, "kind:", kinds);
        choiceRow("Show", TAB_Y, "tab:", new String[]{"Stats", "Scripts"});

        // The text box goes in first so the stat buttons on top of it win the hit test outside TEXT.
        Btn textBox = new Btn("text", MID_X, STAT_Y, MID_W, AREA_H - 6);
        textBox.enabled = false;
        buttons.add(textBox);
        descriptionText = label("", Misc.getTextColor(), MID_X + 10, STAT_Y + 8, MID_W - 20, 18, Alignment.TL);

        for (int i = 0; i < STAT_ROWS; i++) {
            float y = STAT_Y + i * STAT_STEP;
            statNames[i] = label("", Misc.getTextColor(), MID_X, y + 6, 200, 18, Alignment.LMID);
            statValues[i] = label("", Misc.getHighlightColor(), MID_X + 200, y + 6, 130, 18, Alignment.MID);
            button("less:" + i, "", MID_X + 336, y, 99, 28);
            button("more:" + i, "", MID_X + 441, y, 99, 28);
        }

        for (int i = 0; i < GRID_CELLS; i++) {
            cellArt[i] = new Art();
            button("cell:" + i, "", MID_X + (i % GRID_COLS) * CELL_W, STAT_Y + (i / GRID_COLS) * CELL_H,
                    CELL_W - 4, CELL_H - 4);
        }
        button("lookprev", "", MID_X, LOOK_NAV_Y, 80, 30);
        lookLabel = label("", Misc.getTextColor(), MID_X + 86, LOOK_NAV_Y + 6, 368, 18, Alignment.MID);
        button("looknext", "", MID_X + 460, LOOK_NAV_Y, 80, 30);
        hint = label("", Misc.getGrayColor(), MID_X, HINT_Y, MID_W, 60, Alignment.TL);

        button("look", "Change look", SIDE_X, 16 + PREVIEW_H + 6, SIDE_W, 28);
        button("describe", "Edit description", SIDE_X, 16 + PREVIEW_H + 40, SIDE_W, 28);
        for (int i = 0; i < SUMMARY_ROWS; i++) {
            float y = SUMMARY_Y + i * SUMMARY_STEP;
            summaryNames[i] = label("", Misc.getGrayColor(), SIDE_X, y, 140, 18, Alignment.LMID);
            summaryValues[i] = label("", Misc.getTextColor(), SIDE_X + 140, y, SIDE_W - 140, 18, Alignment.RMID);
        }
        status = label("", Misc.getHighlightColor(), SIDE_X, SUMMARY_Y + SUMMARY_ROWS * SUMMARY_STEP + 10, SIDE_W, 60,
                Alignment.TL);
        button("save", "Save design", SIDE_X, 612, (SIDE_W - 8) / 2, 32);
        button("leave", "Leave", SIDE_X + (SIDE_W + 8) / 2, 612, (SIDE_W - 8) / 2, 32);
        refresh();
    }

    private void choiceRow(String title, float y, String prefix, String[] names) {
        label(title, Misc.getGrayColor(), MID_X, y + 6, 90, 18, Alignment.LMID);
        float w = (CHOICE_W - 6f * (names.length - 1)) / names.length;
        for (int i = 0; i < names.length; i++) button(prefix + i, names[i], CHOICE_X + i * (w + 6), y, w, 30);
    }

    private LabelAPI label(String text, Color color, float x, float y, float w, float h, Alignment alignment) {
        LabelAPI label = Global.getSettings().createLabel(text, Fonts.DEFAULT_SMALL);
        label.setColor(color);
        label.setAlignment(alignment);
        panel.addComponent((UIComponentAPI) label).inTL(x, y).setSize(w, h);
        return label;
    }

    private void button(String action, String text, float x, float y, float w, float h) {
        Btn b = new Btn(action, x, y, w, h);
        b.label = label(text, Color.WHITE, x, y + (h - 18) / 2, w, 18, Alignment.MID);
        buttons.add(b);
    }

    private Btn find(String action) {
        for (Btn b : buttons) if (b.action.equals(action)) return b;
        return null;
    }

    // ---- Rows: stats on one page, scripts on the other. ----

    private List<?> rows() {
        return page == Page.SCRIPTS ? Arrays.asList(Script.values()) : draft.kind.stats();
    }

    private static boolean canStep(Design d, Object row, int delta) {
        return row instanceof Stat ? d.canStep((Stat) row, delta) : d.canStep((Script) row, delta);
    }

    private static void step(Design d, Object row, int delta) {
        if (row instanceof Stat) d.step((Stat) row, delta);
        else d.step((Script) row, delta);
    }

    private String rowName(Object row) {
        return row instanceof Stat ? ((Stat) row).label(draft.kind) : ((Script) row).label;
    }

    private String rowValue(Object row) {
        if (row instanceof Script) return ((Script) row).valueText(draft.level((Script) row));
        Stat stat = (Stat) row;
        float v = draft.value(stat);
        switch (stat) {
            case EMP:
                return Text.number(draft.kind == Kind.BEAM ? Costing.empPerSecond(draft) : Costing.empPerShot(draft));
            case GUIDANCE:
                return v == 0f ? "None" : Text.number(v);
            case ACCURACY:
                return v == 0f ? "Perfect" : Text.number(v);
            case RELOAD:
                return Text.number(v) + " s";
            default:
                return Text.number(v);
        }
    }

    private void refresh() {
        List<?> rows = rows();
        int dp = Costing.designPoints(draft);
        int op = Costing.op(draft);
        int limit = Settings.opLimit();
        boolean over = Costing.overLimit(draft);
        boolean showRows = overlay == Overlay.NONE;

        for (int i = 0; i < LIST_ROWS; i++) {
            int index = scroll + i;
            Btn row = find("row:" + i);
            boolean shown = index < designs.size();
            row.enabled = shown;
            row.checked = shown && index == selected;
            rowNames[i].setText(shown ? designs.get(index).name : "");
            rowCosts[i].setText(shown ? Text.grouped(Costing.op(designs.get(index))) + " OP" : "");
        }
        find("up").enabled = scroll > 0;
        find("down").enabled = scroll + LIST_ROWS < designs.size();
        find("new").checked = selected < 0;

        boolean caret = (caretTimer % 1f) < 0.5f;
        String name = draft.name == null ? "" : draft.name;
        nameText.setText(name + (focus == Focus.NAME && caret ? "_" : ""));
        find("name").checked = focus == Focus.NAME;

        for (int i = 0; i < SIZES.length; i++) find("size:" + i).checked = draft.size == SIZES[i];
        for (int i = 0; i < MOUNTS.length; i++) find("mount:" + i).checked = draft.mount == MOUNTS[i];
        for (int i = 0; i < DAMAGES.length; i++) find("damage:" + i).checked = draft.damage == DAMAGES[i];
        for (int i = 0; i < Kind.values().length; i++) {
            Btn b = find("kind:" + i);
            b.checked = draft.kind == Kind.values()[i];
            b.enabled = draft.weaponId == null || b.checked;
        }
        find("tab:0").checked = showRows && page == Page.STATS;
        find("tab:1").checked = showRows && page == Page.SCRIPTS;
        int active = draft.activeScripts();
        find("tab:1").label.setText(active > 0 ? "Scripts (" + active + ")" : "Scripts");

        for (int i = 0; i < STAT_ROWS; i++) {
            Btn less = find("less:" + i);
            Btn more = find("more:" + i);
            if (!showRows || i >= rows.size()) {
                statNames[i].setText("");
                statValues[i].setText("");
                hide(less);
                hide(more);
                continue;
            }
            Object row = rows.get(i);
            statNames[i].setText(rowName(row));
            statValues[i].setText(rowValue(row));
            boolean on = row instanceof Script && draft.level((Script) row) > 0;
            statValues[i].setColor(row instanceof Script && !on ? Misc.getGrayColor() : Misc.getHighlightColor());
            stepButton(less, row, -1, dp);
            stepButton(more, row, 1, dp);
        }
        refreshLooks();
        refreshText();
        refreshHint();

        find("look").label.setText(overlay == Overlay.LOOKS ? "Done" : "Change look");
        find("look").checked = overlay == Overlay.LOOKS;
        find("describe").label.setText(overlay == Overlay.TEXT ? "Done" : "Edit description");
        find("describe").checked = overlay == Overlay.TEXT;

        Color normal = Misc.getTextColor();
        summary(0, "Design points", Text.grouped(dp), Misc.getHighlightColor());
        summary(1, "Weapon OP", limit > 0 ? Text.grouped(op) + " / " + Text.grouped(limit) : Text.grouped(op),
                over ? Misc.getNegativeHighlightColor() : Misc.getHighlightColor());
        summary(2, "Production cost", Text.grouped(Costing.price(draft)) + " credits", normal);
        switch (draft.kind) {
            case MISSILE:
                summary(3, "Damage per second", Text.number(Costing.damagePerSecond(draft)), normal);
                summary(4, "Salvo damage", Text.number(draft.value(Stat.DAMAGE)
                        * Math.min(draft.value(Stat.SALVO), draft.value(Stat.AMMO))), normal);
                summary(5, "Total damage", Text.number(Costing.totalDamage(draft)), normal);
                summary(6, "Flight time", Text.number(Costing.flightTime(draft)) + " s", normal);
                break;
            case BEAM:
                summary(3, "Flux per second", Text.number(Costing.fluxPerSecond(draft)), normal);
                summary(4, "", "", normal);
                summary(5, "", "", normal);
                summary(6, "", "", normal);
                break;
            default:
                summary(3, "Damage per second", Text.number(Costing.damagePerSecond(draft)), normal);
                summary(4, "Flux per second", Text.number(Costing.fluxPerSecond(draft)), normal);
                summary(5, "EMP per second", Text.number(Costing.empPerSecond(draft)), normal);
                summary(6, "", "", normal);
                break;
        }

        boolean full = draft.weaponId == null && DesignStore.freeWeaponId(draft.kind, designs) == null;
        Btn save = find("save");
        save.enabled = !over && !full;
        save.label.setText(draft.weaponId == null ? "Save design" : "Save changes");
        if (over) {
            status.setText("Over the weapon OP limit of " + Text.grouped(limit) + " set in the LunaLib settings");
            status.setColor(Misc.getNegativeHighlightColor());
        } else if (full) {
            status.setText("No blank " + draft.kind.label.toLowerCase() + " weapons left (" + Kind.RESERVE
                    + " per projectile type)");
            status.setColor(Misc.getNegativeHighlightColor());
        } else {
            status.setText(message);
            status.setColor(messageBad ? Misc.getNegativeHighlightColor() : Misc.getHighlightColor());
        }
        status.autoSizeToWidth(SIDE_W);

        for (Btn b : buttons) {
            if (b.label != null) b.label.setColor(b.enabled ? Color.WHITE : Misc.getGrayColor().darker());
        }
        loadPreview();
    }

    private void hide(Btn b) {
        b.enabled = false;
        b.label.setText("");
    }

    /** A step button shows exactly how many design points its step adds or gives back. */
    private void stepButton(Btn b, Object row, int delta, int dp) {
        if (!canStep(draft, row, delta)) {
            hide(b);
            return;
        }
        Design next = draft.copy();
        step(next, row, delta);
        int change = Costing.designPoints(next) - dp;
        b.enabled = true;
        b.label.setText((change >= 0 ? "+" : "") + Text.grouped(change) + " DP");
    }

    private void summary(int row, String name, String value, Color color) {
        summaryNames[row].setText(name);
        summaryValues[row].setText(value);
        summaryValues[row].setColor(color);
    }

    private void refreshText() {
        boolean writing = overlay == Overlay.TEXT;
        find("text").enabled = writing;
        if (!writing) {
            descriptionText.setText("");
            return;
        }
        String text = draft.description == null ? "" : draft.description;
        boolean caret = focus == Focus.TEXT && (caretTimer % 1f) < 0.5f;
        if (text.isEmpty() && !caret) {
            descriptionText.setText(SpecWriter.DEFAULT_DESCRIPTION + "  (click here and type to replace this)");
            descriptionText.setColor(Misc.getGrayColor());
        } else {
            descriptionText.setText(text + (caret ? "_" : ""));
            descriptionText.setColor(Misc.getTextColor());
        }
        descriptionText.autoSizeToWidth(MID_W - 20);
    }

    private void refreshHint() {
        String text;
        if (overlay == Overlay.TEXT) {
            int length = draft.description == null ? 0 : draft.description.length();
            text = "Type the weapon's description. Enter starts a new line, Ctrl+V pastes, Escape stops typing. "
                    + length + " / " + Design.MAX_DESCRIPTION + " characters.";
        } else if (overlay == Overlay.LOOKS) {
            text = "";
        } else if (page == Page.SCRIPTS) {
            text = hoveredRow >= 0 && hoveredRow < Script.values().length
                    ? Script.values()[hoveredRow].description
                    : "Scripts are effects the weapon carries. Point at one to read what it does. Each level costs design points. Shift-click for 10 levels.";
        } else {
            text = "Each step costs design points. The weapon's OP is " + Settings.opRate()
                    + "% of its design points (LunaLib setting). Shift-click for 10 steps.";
        }
        hint.setText(text);
        hint.autoSizeToWidth(MID_W);
    }

    // ---- Look grid. ----

    private int lookPages() {
        return Math.max(1, (lookChoices.size() + GRID_CELLS) / GRID_CELLS);
    }

    /** The weapon behind a grid cell on the current page: null for Automatic, or out of range. */
    private WeaponSpecAPI cellWeapon(int cell) {
        int entry = lookPage * GRID_CELLS + cell - 1;
        return entry >= 0 && entry < lookChoices.size() ? lookChoices.get(entry) : null;
    }

    private boolean cellUsed(int cell) {
        int entry = lookPage * GRID_CELLS + cell;
        return entry <= lookChoices.size();
    }

    private void refreshLooks() {
        boolean picking = overlay == Overlay.LOOKS;
        lookChoices = picking ? Looks.choices(draft.kind, draft.size) : new ArrayList<>();
        lookPage = Math.max(0, Math.min(lookPage, lookPages() - 1));
        WeaponSpecAPI chosen = Looks.chosen(draft);
        for (int i = 0; i < GRID_CELLS; i++) {
            Btn cell = find("cell:" + i);
            boolean used = picking && cellUsed(i);
            boolean automatic = used && lookPage == 0 && i == 0;
            cell.enabled = used;
            cell.checked = used && (automatic ? chosen == null : cellWeapon(i) != null && cellWeapon(i) == chosen);
            cell.label.setText(automatic ? "Automatic" : "");
        }
        Btn prev = find("lookprev");
        Btn next = find("looknext");
        prev.label.setText(picking ? "<" : "");
        next.label.setText(picking ? ">" : "");
        prev.enabled = picking && lookPage > 0;
        next.enabled = picking && lookPage < lookPages() - 1;

        String key = picking ? draft.kind + ":" + draft.size + ":" + lookPage : "";
        if (!key.equals(cellArtKey)) {
            cellArtKey = key;
            for (int i = 0; i < GRID_CELLS; i++) cellArt[i].load(picking ? cellWeapon(i) : null);
        }
        refreshLookLabel();
    }

    private void refreshLookLabel() {
        if (overlay != Overlay.LOOKS) {
            lookLabel.setText("");
            return;
        }
        String name;
        if (hoveredCell >= 0 && cellUsed(hoveredCell)) {
            WeaponSpecAPI w = cellWeapon(hoveredCell);
            name = w == null ? "Automatic" : w.getWeaponName();
        } else {
            WeaponSpecAPI chosen = Looks.chosen(draft);
            name = chosen == null ? "Automatic" : chosen.getWeaponName();
        }
        lookLabel.setText(name + "      " + (lookPage + 1) + " / " + lookPages());
    }

    /** Opens the look grid on the page that holds the current look. */
    private void openLooks() {
        overlay = Overlay.LOOKS;
        lookPage = 0;
        WeaponSpecAPI chosen = Looks.chosen(draft);
        if (chosen != null) {
            int entry = Looks.choices(draft.kind, draft.size).indexOf(chosen) + 1;
            lookPage = entry / GRID_CELLS;
        }
    }

    private void loadPreview() {
        WeaponSpecAPI donor = Looks.donorFor(draft);
        if (donor == previewDonor) return;
        previewDonor = donor;
        preview.load(donor);
    }

    // ---- Actions. ----

    private void act(String action) {
        if (dismissed) return;
        focus = action.equals("name") ? Focus.NAME : action.equals("text") ? Focus.TEXT : Focus.NONE;
        message = "";
        if (action.equals("leave")) {
            finish();
            return;
        }
        if (action.equals("new")) {
            draft = new Design();
            selected = -1;
        } else if (action.equals("name") || action.equals("text")) {
            caretTimer = 0f;
        } else if (action.equals("up")) {
            scroll = Math.max(0, scroll - LIST_ROWS);
        } else if (action.equals("down")) {
            scroll = Math.min(Math.max(0, designs.size() - LIST_ROWS), scroll + LIST_ROWS);
        } else if (action.equals("save")) {
            save();
        } else if (action.startsWith("row:")) {
            int index = scroll + Integer.parseInt(action.substring(4));
            if (index < designs.size()) {
                selected = index;
                draft = designs.get(index).copy();
            }
        } else if (action.startsWith("tab:")) {
            page = action.equals("tab:1") ? Page.SCRIPTS : Page.STATS;
            overlay = Overlay.NONE;
        } else if (action.equals("look")) {
            if (overlay == Overlay.LOOKS) overlay = Overlay.NONE;
            else openLooks();
        } else if (action.equals("describe")) {
            if (overlay == Overlay.TEXT) {
                overlay = Overlay.NONE;
            } else {
                overlay = Overlay.TEXT;
                focus = Focus.TEXT;
                caretTimer = 0f;
            }
        } else if (action.equals("lookprev")) {
            lookPage--;
        } else if (action.equals("looknext")) {
            lookPage++;
        } else if (action.startsWith("cell:")) {
            int cell = Integer.parseInt(action.substring(5));
            WeaponSpecAPI weapon = cellWeapon(cell);
            draft.look = weapon == null ? null : weapon.getWeaponId();
        } else if (action.startsWith("size:")) {
            draft.size = SIZES[Integer.parseInt(action.substring(5))];
            lookPage = 0;
        } else if (action.startsWith("mount:")) {
            draft.mount = MOUNTS[Integer.parseInt(action.substring(6))];
        } else if (action.startsWith("damage:")) {
            draft.damage = DAMAGES[Integer.parseInt(action.substring(7))];
        } else if (action.startsWith("kind:")) {
            Kind kind = Kind.values()[Integer.parseInt(action.substring(5))];
            if (draft.weaponId == null && draft.kind != kind) {
                draft.kind = kind;
                draft.resetStats();
                lookPage = 0;
            }
        } else if (action.startsWith("less:") || action.startsWith("more:")) {
            int index = Integer.parseInt(action.substring(5));
            List<?> rows = rows();
            if (index < rows.size()) {
                int delta = action.startsWith("more:") ? 1 : -1;
                boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
                for (int i = 0; i < (shift ? BIG_STEP : 1) && canStep(draft, rows.get(index), delta); i++) {
                    step(draft, rows.get(index), delta);
                }
            }
        }
        Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 0.5f);
        refresh();
    }

    private void save() {
        if (Costing.overLimit(draft)) return;
        draft.name = draft.name == null ? "" : draft.name.trim();
        draft.description = draft.description == null ? "" : draft.description.trim();
        if (draft.weaponId == null) {
            String id = DesignStore.freeWeaponId(draft.kind, designs);
            if (id == null) return;
            draft.weaponId = id;
            if (draft.name.isEmpty()) draft.name = "Design " + (designs.size() + 1);
            designs.add(draft.copy());
            selected = designs.size() - 1;
            if (selected >= scroll + LIST_ROWS) scroll = selected - LIST_ROWS + 1;
        } else {
            if (draft.name.isEmpty()) draft.name = designs.get(selected).name;
            designs.set(selected, draft.copy());
        }
        DesignStore.save(designs);
        try {
            SpecWriter.apply(draft);
            Global.getSector().getPlayerFaction().addKnownWeapon(draft.weaponId, true);
            int moved = Refits.unequipMisfits(draft.weaponId);
            message = moved > 0 ? "Saved. " + moved + " fitted " + (moved == 1 ? "copy" : "copies")
                    + " no longer fit and went to cargo." : "Saved.";
            messageBad = false;
        } catch (Exception e) {
            Global.getLogger(DesignerPanel.class).error("Weapon Designer: could not apply " + draft.weaponId, e);
            message = "The design was saved but could not be applied. See starsector.log.";
            messageBad = true;
        }
    }

    /** Typing into the name or the description. Reads the event before it is consumed. */
    private void typed(InputEventAPI event) {
        int key = event.getEventValue();
        boolean name = focus == Focus.NAME;
        String text = name ? draft.name : draft.description;
        if (text == null) text = "";
        int max = name ? Design.MAX_NAME : Design.MAX_DESCRIPTION;
        if (key == Keyboard.KEY_BACK) {
            if (!text.isEmpty()) text = text.substring(0, text.length() - 1);
        } else if (key == Keyboard.KEY_ESCAPE) {
            focus = Focus.NONE;
        } else if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            if (name) focus = Focus.NONE;
            else if (text.length() < max) text += "\n";
        } else if (event.isCtrlDown() && !event.isAltDown()) {
            // Ctrl+Alt is AltGr on many keyboards (Polish letters), so only Ctrl alone is a shortcut.
            if (key == Keyboard.KEY_V) text = text + clean(clipboard(), !name);
        } else {
            char c = event.getEventChar();
            if (c >= 32 && c != 127) text += c;
        }
        text = Text.fontSafe(text);
        if (!name) text = Text.breakLongWords(text, MAX_WORD);
        if (text.length() > max) text = text.substring(0, max);
        if (name) draft.name = text;
        else draft.description = text;
        caretTimer = 0f;
        refresh();
    }

    private static String clipboard() {
        try {
            String text = Sys.getClipboard();
            return text == null ? "" : text;
        } catch (Throwable t) {
            return "";
        }
    }

    /** Pasted text without control characters; new lines kept only where they are allowed. */
    private static String clean(String text, boolean lines) {
        StringBuilder out = new StringBuilder();
        String unified = text.replace("\r\n", "\n").replace('\r', '\n').replace('\t', ' ');
        for (char c : unified.toCharArray()) {
            if (c == '\n') out.append(lines ? '\n' : ' ');
            else if (c >= 32 && c != 127) out.append(c);
        }
        return out.toString();
    }

    private void pointer(float mx, float my, boolean down) {
        boolean click = down && !wasMouseDown;
        wasMouseDown = down;
        int cellUnder = -1;
        for (Btn b : buttons) {
            b.hovered = b.enabled && b.contains(mx, my);
            if (b.hovered && b.action.startsWith("cell:")) cellUnder = Integer.parseInt(b.action.substring(5));
        }
        if (cellUnder != hoveredCell) {
            hoveredCell = cellUnder;
            refreshLookLabel();
        }
        int rowUnder = -1;
        if (overlay == Overlay.NONE && mx >= MID_X && mx <= MID_X + MID_W && my >= STAT_Y && my < STAT_Y + AREA_H) {
            rowUnder = (int) ((my - STAT_Y) / STAT_STEP);
        }
        if (rowUnder != hoveredRow) {
            hoveredRow = rowUnder;
            refreshHint();
        }
        if (!click || dismissed) return;
        // Hidden controls share space with the look grid and the text box, so only enabled ones
        // take the click, and the last one added (drawn on top) wins.
        for (int i = buttons.size() - 1; i >= 0; i--) {
            Btn b = buttons.get(i);
            if (b.enabled && b.contains(mx, my)) {
                act(b.action);
                return;
            }
        }
        if (focus != Focus.NONE) {
            focus = Focus.NONE;
            refresh();
        }
    }

    @Override
    public void advance(float amount) {
        if (dismissed) return;
        if (position != null && Mouse.isCreated()) {
            float scale = Global.getSettings().getScreenScaleMult();
            if (scale > 0) {
                pointer(Mouse.getX() / scale - position.getX(), position.getY() + PANEL_H - Mouse.getY() / scale,
                        Mouse.isButtonDown(0));
            }
        }
        if (focus != Focus.NONE && Float.isFinite(amount)) {
            boolean before = (caretTimer % 1f) < 0.5f;
            caretTimer += amount;
            if (before != (caretTimer % 1f) < 0.5f) refresh();
        }
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        if (events == null || dismissed) return;
        for (InputEventAPI event : events) {
            if (event.isConsumed()) continue;
            if (event.isMouseScrollEvent() && position != null) {
                float scale = Global.getSettings().getScreenScaleMult();
                float mx = event.getX() / scale - position.getX();
                if (mx >= LIST_X && mx <= LIST_X + LIST_W) {
                    int step = event.getEventValue() > 0 ? -1 : 1;
                    scroll = Math.max(0, Math.min(Math.max(0, designs.size() - LIST_ROWS), scroll + step));
                    event.consume();
                    refresh();
                }
                continue;
            }
            if (!event.isKeyDownEvent()) continue;
            if (focus != Focus.NONE) {
                // The game refuses to hand out the key of an event already marked as used,
                // so read it before consuming.
                typed(event);
                event.consume();
                continue;
            }
            if (event.getEventValue() == Keyboard.KEY_ESCAPE) {
                event.consume();
                if (overlay != Overlay.NONE) {
                    overlay = Overlay.NONE;
                    refresh();
                    continue;
                }
                finish();
                return;
            }
        }
    }

    private void finish() {
        if (dismissed) return;
        dismissed = true;
        if (callbacks != null) callbacks.dismissDialog();
    }

    public void markDismissed() {
        dismissed = true;
    }

    @Override
    public void positionChanged(PositionAPI position) {
        this.position = position;
    }

    private float gx(float x) {
        return position.getX() + x;
    }

    private float gy(float top, float h) {
        return position.getY() + PANEL_H - top - h;
    }

    private void rect(float x, float y, float w, float h, Color c, float a) {
        GLDraw.quad(gx(x), gy(y, h), w, h, c, a);
    }

    private void frame(float x, float y, float w, float h, Color c, float a) {
        GLDraw.frame(gx(x), gy(y, h), w, h, c, 1f, a);
    }

    @Override
    public void renderBelow(float alpha) {
        if (position == null) return;
        // Only write GL state. Synchronous queries crash the Fast Rendering bridge some players use.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            rect(0, 0, PANEL_W, PANEL_H, BG, alpha);
            rect(LIST_X - 6, 56, LIST_W + 12, LIST_ROWS * ROW_STEP + 4, AREA, alpha);
            rect(MID_X - 6, STAT_Y - 6, MID_W + 12, AREA_H + 6, AREA, alpha);
            rect(SIDE_X, 16, SIDE_W, PREVIEW_H, AREA, alpha);
            frame(SIDE_X, 16, SIDE_W, PREVIEW_H, Misc.getDarkPlayerColor(), alpha);
            Color base = Misc.getBasePlayerColor();
            for (Btn b : buttons) {
                if (b.action.startsWith("row:")) {
                    if (!b.enabled) continue;
                    if (b.checked) rect(b.x, b.y, b.w, b.h, BUTTON_ON, alpha);
                    else if (b.hovered) rect(b.x, b.y, b.w, b.h, BUTTON_HOVER, alpha * 0.7f);
                    continue;
                }
                if (b.action.startsWith("cell:")) {
                    if (!b.enabled) continue;
                    rect(b.x, b.y, b.w, b.h, b.checked ? BUTTON_ON : b.hovered ? BUTTON_HOVER : BUTTON, alpha * 0.8f);
                    frame(b.x, b.y, b.w, b.h, b.checked ? Misc.getBrightPlayerColor() : base, alpha * 0.4f);
                    continue;
                }
                if (b.action.equals("text")) {
                    if (!b.enabled) continue;
                    frame(b.x, b.y, b.w, b.h, focus == Focus.TEXT ? Misc.getBrightPlayerColor() : base,
                            alpha * (focus == Focus.TEXT ? 0.7f : 0.4f));
                    continue;
                }
                if (b.label != null && b.label.getText().isEmpty() && !b.action.equals("name")) continue;
                Color fill = b.action.equals("name") ? AREA
                        : !b.enabled ? BUTTON_OFF : b.checked ? BUTTON_ON : b.hovered ? BUTTON_HOVER : BUTTON;
                GLDraw.bevelledPanel(gx(b.x), gy(b.y, b.h), b.w, b.h, fill, 2f, alpha);
                frame(b.x, b.y, b.w, b.h, b.checked ? Misc.getBrightPlayerColor() : base,
                        alpha * (b.enabled ? 0.6f : 0.2f));
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    @Override
    public void render(float alpha) {
        if (position == null) return;
        preview.render(gx(SIDE_X + SIDE_W / 2f), gy(16 + PREVIEW_H / 2f, 0), SIDE_W - 20, PREVIEW_H - 20, 3f, alpha);
        if (overlay != Overlay.LOOKS) return;
        for (int i = 0; i < GRID_CELLS; i++) {
            Btn cell = find("cell:" + i);
            if (!cell.enabled) continue;
            cellArt[i].render(gx(cell.x + cell.w / 2f), gy(cell.y + cell.h / 2f, 0), cell.w - 12, cell.h - 12, 2f, alpha);
        }
    }
}
