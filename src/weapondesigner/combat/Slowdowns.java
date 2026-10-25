package weapondesigner.combat;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.input.InputEventAPI;

/** Engine jammer: slows ships for a few seconds, the strongest hit winning, then lets go. */
final class Slowdowns extends BaseEveryFrameCombatPlugin {
    private static final String KEY = "weapondesigner_slowdowns";
    private static final String MOD_ID = "weapondesigner_engine_jammer";
    private static final float DURATION = 3f;

    private final CombatEngineAPI engine;
    private final Map<ShipAPI, float[]> slowed = new HashMap<>();

    private Slowdowns(CombatEngineAPI engine) {
        this.engine = engine;
    }

    /** The battle's tracker, added to the battle the first time it is needed. */
    static Slowdowns of(CombatEngineAPI engine) {
        Object existing = engine.getCustomData().get(KEY);
        if (existing instanceof Slowdowns) return (Slowdowns) existing;
        Slowdowns created = new Slowdowns(engine);
        engine.getCustomData().put(KEY, created);
        engine.addPlugin(created);
        return created;
    }

    /** {multiplier, seconds left} per ship. */
    void slow(ShipAPI ship, float mult) {
        float[] state = slowed.get(ship);
        if (state == null) {
            state = new float[]{mult, DURATION};
            slowed.put(ship, state);
        } else {
            state[0] = Math.min(state[0], mult);
            state[1] = DURATION;
        }
        apply(ship, state[0]);
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine.isPaused() || slowed.isEmpty()) return;
        Iterator<Map.Entry<ShipAPI, float[]>> it = slowed.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ShipAPI, float[]> e = it.next();
            ShipAPI ship = e.getKey();
            float[] state = e.getValue();
            state[1] -= amount;
            if (state[1] <= 0f || !ship.isAlive() || !engine.isEntityInPlay(ship)) {
                release(ship);
                it.remove();
            }
        }
    }

    private static void apply(ShipAPI ship, float mult) {
        MutableShipStatsAPI stats = ship.getMutableStats();
        stats.getMaxSpeed().modifyMult(MOD_ID, mult);
        stats.getAcceleration().modifyMult(MOD_ID, mult);
        stats.getMaxTurnRate().modifyMult(MOD_ID, mult);
    }

    private static void release(ShipAPI ship) {
        MutableShipStatsAPI stats = ship.getMutableStats();
        stats.getMaxSpeed().unmodify(MOD_ID);
        stats.getAcceleration().unmodify(MOD_ID);
        stats.getMaxTurnRate().unmodify(MOD_ID);
    }
}
