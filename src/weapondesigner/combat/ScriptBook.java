package weapondesigner.combat;

import java.util.HashMap;
import java.util.Map;

import weapondesigner.Design;
import weapondesigner.Kind;
import weapondesigner.Script;
import weapondesigner.Stat;

/**
 * The script levels of every designed weapon, by weapon id. Filled whenever a design is written
 * onto its weapon, so like the weapon definitions it always matches the save that is loaded.
 */
public final class ScriptBook {
    private static final Map<String, int[]> LEVELS = new HashMap<>();
    private static final Map<String, Float> BEAM_PULSE_DAMAGE = new HashMap<>();

    private ScriptBook() {
    }

    public static void put(Design d) {
        if (d.weaponId == null) return;
        int[] levels = new int[Script.values().length];
        boolean any = false;
        for (Script script : Script.values()) {
            levels[script.ordinal()] = d.level(script);
            any |= levels[script.ordinal()] > 0;
        }
        if (any) LEVELS.put(d.weaponId, levels);
        else LEVELS.remove(d.weaponId);
        if (d.kind == Kind.BEAM) BEAM_PULSE_DAMAGE.put(d.weaponId, d.value(Stat.DAMAGE) * Script.BEAM_PULSE);
    }

    public static void clear(String weaponId) {
        LEVELS.remove(weaponId);
        BEAM_PULSE_DAMAGE.remove(weaponId);
    }

    /** The weapon's script levels by {@link Script#ordinal()}, or null when it has none. */
    public static int[] of(String weaponId) {
        return weaponId == null ? null : LEVELS.get(weaponId);
    }

    /** Damage a beam counts per pulse: half a second of its damage per second. */
    public static float beamPulseDamage(String weaponId) {
        Float damage = BEAM_PULSE_DAMAGE.get(weaponId);
        return damage == null ? 0f : damage;
    }
}
