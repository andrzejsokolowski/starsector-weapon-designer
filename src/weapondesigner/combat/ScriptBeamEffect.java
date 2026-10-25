package weapondesigner.combat;

import java.util.Map;
import java.util.WeakHashMap;

import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.BeamEffectPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;

import weapondesigner.Script;

/**
 * Named in every blank beam .wpn file. A beam has no separate hits, so every half second of
 * contact counts as one hit of half a second's damage.
 */
public class ScriptBeamEffect implements BeamEffectPlugin {
    private static final String KEY = "weapondesigner_beam_contact";

    @Override
    @SuppressWarnings("unchecked")
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
        if (engine == null || beam == null || beam.getWeapon() == null || engine.isPaused()) return;
        String weaponId = beam.getWeapon().getId();
        int[] levels = ScriptBook.of(weaponId);
        if (levels == null) return;
        CombatEntityAPI target = beam.getDamageTarget();
        if (target == null || !beam.didDamageThisFrame()) return;

        Object stored = engine.getCustomData().get(KEY);
        if (!(stored instanceof Map)) {
            stored = new WeakHashMap<BeamAPI, float[]>();
            engine.getCustomData().put(KEY, stored);
        }
        Map<BeamAPI, float[]> contact = (Map<BeamAPI, float[]>) stored;
        float[] time = contact.get(beam);
        if (time == null) {
            time = new float[1];
            contact.put(beam, time);
        }
        time[0] += amount;
        if (time[0] < Script.BEAM_PULSE) return;
        time[0] -= Script.BEAM_PULSE;

        boolean shieldHit = false;
        if (target instanceof ShipAPI) {
            ShipAPI ship = (ShipAPI) target;
            shieldHit = ship.getShield() != null && ship.getShield().isOn() && ship.getShield().isWithinArc(beam.getTo());
        }
        float damage = ScriptBook.beamPulseDamage(weaponId);
        float hullDamage = target instanceof ShipAPI && !shieldHit ? damage : 0f;
        Effects.hit(engine, beam.getSource(), target, beam.getTo(), shieldHit, damage,
                beam.getDamage() == null ? null : beam.getDamage().getType(), hullDamage, levels);
    }
}
