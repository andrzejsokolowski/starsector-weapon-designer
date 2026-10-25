package weapondesigner.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

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
import weapondesigner.SpecWriter;
import weapondesigner.Stat;

/**
 * The designer screen. Controls are drawn and hit-tested here rather than with the game's
 * tooltip buttons, the same way as Gambling Den's tables, so every click lands where it is drawn.
 */
public final class DesignerPanel extends BaseCustomUIPanelPlugin {
    public static final float PANEL_W = 1100, PANEL_H = 680;

    private static final float LIST_X = 16, LIST_W = 236, ROW_Y = 60, ROW_STEP = 34, ROW_H = 30;
    private static final int LIST_ROWS = 16;
    private static final float MID_X = 268, CHOICE_X = 364, CHOICE_W = 444;
    private static final float STAT_Y = 212, STAT_STEP = 36;
    private static final int STAT_ROWS = 10;
    private static final float SIDE_X = 824, SIDE_W = 260, PREVIEW_H = 160;
    private static final int SUMMARY_ROWS = 6;
    private static final int GRID_COLS = 6, GRID_ROWS = 4, GRID_CELLS = GRID_COLS * GRID_ROWS;
    private static final float CELL_W = 90, CELL_H = 80, LOOK_NAV_Y = 536;

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
    private LabelAPI nameText, status;
    private Design draft = new Design();
    private int selected = -1;
    private int scroll;
    private boolean nameFocused, dismissed, wasMouseDown;
    private String message = "";
    private boolean messageBad;
    private float caretTimer;
    private final Art preview = new Art();
    private WeaponSpecAPI previewDonor;
    private LabelAPI lookLabel;
    private boolean picking;
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
            rowNames[i] = label("", Misc.getTextColor(), LIST_X + 10, y + 6, LIST_W - 70, 18, Alignment.LMID);
            rowCosts[i] = label("", Misc.getGrayColor(), LIST_X + LIST_W - 64, y + 6, 56, 18, Alignment.RMID);
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

        for (int i = 0; i < STAT_ROWS; i++) {
            float y = STAT_Y + i * STAT_STEP;
            statNames[i] = label("", Misc.getTextColor(), MID_X, y + 6, 210, 18, Alignment.LMID);
            statValues[i] = label("", Misc.getHighlightColor(), MID_X + 210, y + 6, 110, 18, Alignment.MID);
            button("less:" + i, "", MID_X + 326, y, 104, 30);
            button("more:" + i, "", MID_X + 436, y, 104, 30);
        }

        for (int i = 0; i < GRID_CELLS; i++) {
            cellArt[i] = new Art();
            button("cell:" + i, "", MID_X + (i % GRID_COLS) * CELL_W, STAT_Y + (i / GRID_COLS) * CELL_H,
                    CELL_W - 4, CELL_H - 4);
        }
        button("lookprev", "", MID_X, LOOK_NAV_Y, 80, 30);
        lookLabel = label("", Misc.getTextColor(), MID_X + 86, LOOK_NAV_Y + 6, 368, 18, Alignment.MID);
        button("looknext", "", MID_X + 460, LOOK_NAV_Y, 80, 30);
        button("look", "Change look", SIDE_X, 16 + PREVIEW_H + 6, SIDE_W, 28);

        for (int i = 0; i < SUMMARY_ROWS; i++) {
            float y = 220 + i * 26;
            summaryNames[i] = label("", Misc.getGrayColor(), SIDE_X, y, 150, 18, Alignment.LMID);
            summaryValues[i] = label("", Misc.getTextColor(), SIDE_X + 150, y, SIDE_W - 150, 18, Alignment.RMID);
        }
        status = label("", Misc.getHighlightColor(), SIDE_X, 540, SIDE_W, 60, Alignment.TL);
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

    private void refresh() {
        List<Stat> stats = draft.kind.stats();
        int op = Costing.op(draft);
        int cap = Costing.cap(draft.size);

        for (int i = 0; i < LIST_ROWS; i++) {
            int index = scroll + i;
            Btn row = find("row:" + i);
            boolean shown = index < designs.size();
            row.enabled = shown;
            row.checked = shown && index == selected;
            rowNames[i].setText(shown ? designs.get(index).name : "");
            rowCosts[i].setText(shown ? Costing.op(designs.get(index)) + " OP" : "");
        }
        find("up").enabled = scroll > 0;
        find("down").enabled = scroll + LIST_ROWS < designs.size();
        find("new").checked = selected < 0;

        String name = draft.name == null ? "" : draft.name;
        boolean caret = nameFocused && (caretTimer % 1f) < 0.5f;
        nameText.setText(name + (caret ? "_" : ""));
        find("name").checked = nameFocused;

        for (int i = 0; i < SIZES.length; i++) find("size:" + i).checked = draft.size == SIZES[i];
        for (int i = 0; i < MOUNTS.length; i++) find("mount:" + i).checked = draft.mount == MOUNTS[i];
        for (int i = 0; i < DAMAGES.length; i++) find("damage:" + i).checked = draft.damage == DAMAGES[i];
        for (int i = 0; i < Kind.values().length; i++) {
            Btn b = find("kind:" + i);
            b.checked = draft.kind == Kind.values()[i];
            b.enabled = draft.weaponId == null || b.checked;
        }

        for (int i = 0; i < STAT_ROWS; i++) {
            Btn less = find("less:" + i);
            Btn more = find("more:" + i);
            if (picking || i >= stats.size()) {
                statNames[i].setText("");
                statValues[i].setText("");
                hide(less);
                hide(more);
                continue;
            }
            Stat stat = stats.get(i);
            statNames[i].setText(stat.label(draft.kind));
            statValues[i].setText(valueText(stat));
            stepButton(less, stat, -1, op, cap);
            stepButton(more, stat, 1, op, cap);
        }
        refreshLooks();

        summary(0, "Weapon OP", op + " / " + cap, op > cap ? Misc.getNegativeHighlightColor() : Misc.getHighlightColor());
        summary(1, "Production cost", Misc.getWithDGS(Costing.price(draft)) + " credits", Misc.getTextColor());
        switch (draft.kind) {
            case MISSILE:
                summary(2, "Damage per second", number(Costing.damagePerSecond(draft)), Misc.getTextColor());
                summary(3, "Salvo damage", number(draft.value(Stat.DAMAGE)
                        * Math.min(draft.value(Stat.SALVO), draft.value(Stat.AMMO))), Misc.getTextColor());
                summary(4, "Total damage", number(Costing.totalDamage(draft)), Misc.getTextColor());
                summary(5, "Flight time", number(Costing.flightTime(draft)) + " s", Misc.getTextColor());
                break;
            case BEAM:
                summary(2, "Flux per second", number(Costing.fluxPerSecond(draft)), Misc.getTextColor());
                summary(3, "", "", Misc.getTextColor());
                summary(4, "", "", Misc.getTextColor());
                summary(5, "", "", Misc.getTextColor());
                break;
            default:
                summary(2, "Damage per second", number(Costing.damagePerSecond(draft)), Misc.getTextColor());
                summary(3, "Flux per second", number(Costing.fluxPerSecond(draft)), Misc.getTextColor());
                summary(4, "EMP per second", number(Costing.empPerSecond(draft)), Misc.getTextColor());
                summary(5, "", "", Misc.getTextColor());
                break;
        }

        boolean over = op > cap;
        boolean full = draft.weaponId == null && DesignStore.freeWeaponId(draft.kind, designs) == null;
        Btn save = find("save");
        save.enabled = !over && !full;
        save.label.setText(draft.weaponId == null ? "Save design" : "Save changes");
        if (over) {
            status.setText("Over the weapon OP limit for this size");
            status.setColor(Misc.getNegativeHighlightColor());
        } else if (full) {
            status.setText("No blank " + draft.kind.label.toLowerCase() + " weapons left");
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
        find("look").label.setText(picking ? "Done" : "Change look");
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
        if (!picking) {
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
        picking = true;
        lookPage = 0;
        WeaponSpecAPI chosen = Looks.chosen(draft);
        if (chosen != null) {
            int entry = Looks.choices(draft.kind, draft.size).indexOf(chosen) + 1;
            lookPage = entry / GRID_CELLS;
        }
    }

    private void stepButton(Btn b, Stat stat, int delta, int op, int cap) {
        if (!draft.canStep(stat, delta)) {
            hide(b);
            return;
        }
        Design next = draft.copy();
        next.step(stat, delta);
        int nextOp = Costing.op(next);
        int change = nextOp - op;
        b.enabled = delta < 0 || nextOp <= cap;
        b.label.setText((change >= 0 ? "+" : "") + change + " OP");
    }

    private void summary(int row, String name, String value, Color color) {
        summaryNames[row].setText(name);
        summaryValues[row].setText(value);
        summaryValues[row].setColor(color);
    }

    private String valueText(Stat stat) {
        float v = draft.value(stat);
        switch (stat) {
            case EMP:
                return number(draft.kind == Kind.BEAM ? Costing.empPerSecond(draft) : Costing.empPerShot(draft));
            case GUIDANCE:
                return v == 0f ? "None" : number(v);
            case RELOAD:
                return number(v) + " s";
            default:
                return number(v);
        }
    }

    private static String number(float v) {
        if (Math.abs(v - Math.round(v)) < 0.005f) return Misc.getWithDGS(Math.round(v));
        String text = String.format("%.2f", v);
        while (text.endsWith("0")) text = text.substring(0, text.length() - 1);
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    private void loadPreview() {
        WeaponSpecAPI donor = Looks.donorFor(draft);
        if (donor == previewDonor) return;
        previewDonor = donor;
        preview.load(donor);
    }

    private void act(String action) {
        if (dismissed) return;
        if (!action.equals("name")) nameFocused = false;
        message = "";
        if (action.equals("leave")) {
            finish();
            return;
        }
        if (action.equals("new")) {
            draft = new Design();
            selected = -1;
        } else if (action.equals("name")) {
            nameFocused = true;
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
        } else if (action.equals("look")) {
            if (picking) picking = false;
            else openLooks();
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
            int row = Integer.parseInt(action.substring(5));
            List<Stat> stats = draft.kind.stats();
            if (row < stats.size()) draft.step(stats.get(row), action.startsWith("more:") ? 1 : -1);
        }
        Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 0.5f);
        refresh();
    }

    private void save() {
        if (!Costing.withinCap(draft)) return;
        draft.name = draft.name == null ? "" : draft.name.trim();
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

    private void typed(InputEventAPI event) {
        int key = event.getEventValue();
        if (key == Keyboard.KEY_BACK) {
            if (draft.name != null && !draft.name.isEmpty()) draft.name = draft.name.substring(0, draft.name.length() - 1);
        } else if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER || key == Keyboard.KEY_ESCAPE) {
            nameFocused = false;
        } else {
            char c = event.getEventChar();
            if (c >= 32 && c != 127 && (draft.name == null || draft.name.length() < Design.MAX_NAME)) {
                draft.name = (draft.name == null ? "" : draft.name) + c;
            }
        }
        caretTimer = 0f;
        refresh();
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
        if (!click || dismissed) return;
        // Hidden controls share space with the look grid, so only enabled ones take the click.
        for (Btn b : buttons) {
            if (b.enabled && b.contains(mx, my)) {
                act(b.action);
                return;
            }
        }
        if (nameFocused) {
            nameFocused = false;
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
        if (nameFocused && Float.isFinite(amount)) {
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
            if (nameFocused) {
                // The game refuses to hand out the key of an event already marked as used,
                // so read it before consuming.
                typed(event);
                event.consume();
                continue;
            }
            if (event.getEventValue() == Keyboard.KEY_ESCAPE) {
                event.consume();
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
            rect(MID_X - 6, STAT_Y - 6, CHOICE_X + CHOICE_W - MID_X + 12, STAT_ROWS * STAT_STEP + 6, AREA, alpha);
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
        if (!picking) return;
        for (int i = 0; i < GRID_CELLS; i++) {
            Btn cell = find("cell:" + i);
            if (!cell.enabled) continue;
            cellArt[i].render(gx(cell.x + cell.w / 2f), gy(cell.y + cell.h / 2f, 0), cell.w - 12, cell.h - 12, 2f, alpha);
        }
    }
}
