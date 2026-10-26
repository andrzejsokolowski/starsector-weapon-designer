package weapondesigner.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.BeamEffectPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.loading.BeamWeaponSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;

import weapondesigner.Script;

/**
 * Named in every blank beam .wpn file. A beam has no separate hits, so for the mod's own scripts
 * every half second of contact counts as one hit of half a second's damage. Borrowed beam scripts
 * get their own copy per beam (made from the donor's spec, the way the game makes them) and run
 * every frame behind the safety net.
 */
public class ScriptBeamEffect implements BeamEffectPlugin {
    private static final String KEY = "weapondesigner_beam_contact";

    private final List<BeamEffectPlugin> borrowed = new ArrayList<>();
    private final List<String> keys = new ArrayList<>();
    private boolean ready;

    @Override
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
        if (engine == null || beam == null || beam.getWeapon() == null) return;
        String weaponId = beam.getWeapon().getId();
        runBorrowed(amount, engine, beam, weaponId);
        if (engine.isPaused()) return;
        int[] levels = ScriptBook.of(weaponId);
        if (levels == null) return;
        CombatEntityAPI target = beam.getDamageTarget();
        if (target == null || !beam.didDamageThisFrame()) return;

        float[] time = contactTime(engine, beam);
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

    @SuppressWarnings("unchecked")
    private static float[] contactTime(CombatEngineAPI engine, BeamAPI beam) {
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
        return time;
    }

    private void runBorrowed(float amount, CombatEngineAPI engine, BeamAPI beam, String weaponId) {
        if (!ready) {
            ready = true;
            for (String donorId : ScriptBook.borrowedBeams(weaponId)) {
                String key = Guard.key(weaponId, donorId, "BEAM");
                try {
                    WeaponSpecAPI donor = Global.getSettings().getWeaponSpec(donorId);
                    BeamEffectPlugin script = donor instanceof BeamWeaponSpecAPI
                            ? ((BeamWeaponSpecAPI) donor).getBeamEffect() : null;
                    if (script == null) continue;
                    borrowed.add(script);
                    keys.add(key);
                } catch (Throwable t) {
                    Guard.fail(engine, key, t);
                }
            }
        }
        for (int i = 0; i < borrowed.size(); i++) {
            if (Guard.isBroken(engine, keys.get(i))) continue;
            try {
                borrowed.get(i).advance(amount, engine, beam);
            } catch (Throwable t) {
                Guard.fail(engine, keys.get(i), t);
            }
        }
    }
}
