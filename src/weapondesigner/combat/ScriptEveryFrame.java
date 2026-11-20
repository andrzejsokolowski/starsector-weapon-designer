package weapondesigner.combat;

import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPluginWithAdvanceAfter;
import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponEffectPluginWithInit;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.loading.specs.BaseWeaponSpec;

/**
 * Named in every blank .wpn file. The game makes one of these per weapon, and this makes one copy
 * of each borrowed every-frame script for that weapon (from the donor's own spec, the way the
 * game makes them) and runs it behind the safety net. Like the game, it also hands each shot to
 * a borrowed script that wants to hear about firing.
 */
public class ScriptEveryFrame implements EveryFrameWeaponEffectPluginWithAdvanceAfter, OnFireEffectPlugin,
        WeaponEffectPluginWithInit {
    private final List<EveryFrameWeaponEffectPlugin> scripts = new ArrayList<>();
    private final List<String> keys = new ArrayList<>();
    private boolean ready;
    /** The game called init: borrowed scripts that want it get it before their first frame. */
    private WeaponAPI initFor;

    /** Kept for the first frame, where the battle is known, rather than run here. */
    @Override
    public void init(WeaponAPI weapon) {
        initFor = weapon;
    }

    private void prepare(CombatEngineAPI engine, WeaponAPI weapon) {
        ready = true;
        for (String donorId : ScriptBook.borrowedFrames(weapon.getId())) {
            String key = Guard.key(weapon.getId(), donorId, "FRAME");
            try {
                WeaponSpecAPI donor = Global.getSettings().getWeaponSpec(donorId);
                EveryFrameWeaponEffectPlugin script = donor instanceof BaseWeaponSpec
                        ? ((BaseWeaponSpec) donor).getEveryFrameEffect() : null;
                if (script == null) continue;
                // The game calls init on a weapon's own script; a borrowed one hears it here.
                if (initFor != null && script instanceof WeaponEffectPluginWithInit) {
                    ((WeaponEffectPluginWithInit) script).init(initFor);
                }
                scripts.add(script);
                keys.add(key);
            } catch (Throwable t) {
                Guard.fail(engine, key, t);
            }
        }
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine == null || weapon == null) return;
        if (!ready) prepare(engine, weapon);
        for (int i = 0; i < scripts.size(); i++) {
            if (Guard.isBroken(engine, keys.get(i))) continue;
            try {
                scripts.get(i).advance(amount, engine, weapon);
            } catch (Throwable t) {
                Guard.fail(engine, keys.get(i), t);
            }
        }
    }

    @Override
    public void advanceAfter(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine == null || weapon == null) return;
        for (int i = 0; i < scripts.size(); i++) {
            if (!(scripts.get(i) instanceof EveryFrameWeaponEffectPluginWithAdvanceAfter)) continue;
            if (Guard.isBroken(engine, keys.get(i))) continue;
            try {
                ((EveryFrameWeaponEffectPluginWithAdvanceAfter) scripts.get(i)).advanceAfter(amount, engine, weapon);
            } catch (Throwable t) {
                Guard.fail(engine, keys.get(i), t);
            }
        }
    }

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        if (engine == null || weapon == null) return;
        for (int i = 0; i < scripts.size(); i++) {
            if (!(scripts.get(i) instanceof OnFireEffectPlugin)) continue;
            if (Guard.isBroken(engine, keys.get(i))) continue;
            try {
                ((OnFireEffectPlugin) scripts.get(i)).onFire(projectile, weapon, engine);
            } catch (Throwable t) {
                Guard.fail(engine, keys.get(i), t);
            }
        }
    }
}
