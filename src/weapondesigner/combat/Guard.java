package weapondesigner.combat;

import java.util.HashSet;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;

/**
 * The safety net around borrowed scripts. They were written for their own weapon, so some fail on
 * a design (they may expect a missile, an animation or a sprite it lacks). A script that throws is
 * switched off for that design for the rest of the battle and written to starsector.log once,
 * instead of crashing the game.
 */
final class Guard {
    private static final String KEY = "weapondesigner_broken_scripts";

    private Guard() {
    }

    static String key(String weaponId, String donorId, String type) {
        return weaponId + "|" + donorId + "|" + type;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> broken(CombatEngineAPI engine) {
        Object stored = engine.getCustomData().get(KEY);
        if (stored instanceof Set) return (Set<String>) stored;
        Set<String> created = new HashSet<>();
        engine.getCustomData().put(KEY, created);
        return created;
    }

    static boolean isBroken(CombatEngineAPI engine, String key) {
        return engine != null && broken(engine).contains(key);
    }

    static void fail(CombatEngineAPI engine, String key, Throwable t) {
        if (engine != null && !broken(engine).add(key)) return;
        Global.getLogger(Guard.class).warn("Weapon Designer: borrowed script " + key
                + " does not work on this design and is off for the rest of the battle", t);
    }
}
