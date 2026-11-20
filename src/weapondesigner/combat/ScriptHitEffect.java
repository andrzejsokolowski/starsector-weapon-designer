package weapondesigner.combat;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;

import weapondesigner.Kind;

/**
 * Named in every blank shot, laser and missile .proj file. Runs the scripts of whichever design
 * the weapon carries right now, the mod's own and the on-hit scripts it borrows from other
 * weapons; does nothing for designs without scripts.
 */
public class ScriptHitEffect implements OnHitEffectPlugin {
    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit,
                      ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        if (projectile == null) return;
        String weaponId = designOf(projectile);
        int[] levels = ScriptBook.of(weaponId);
        if (levels != null) {
            float hullDamage = damageResult == null ? 0f : damageResult.getDamageToHull();
            Effects.hit(engine, projectile.getSource(), target, point, shieldHit, projectile.getDamageAmount(),
                    projectile.getDamageType(), hullDamage, levels);
        }
        for (ScriptBook.Lent<OnHitEffectPlugin> lent : ScriptBook.borrowedHits(engine, weaponId)) {
            if (Guard.isBroken(engine, lent.key)) continue;
            try {
                lent.script.onHit(projectile, target, point, shieldHit, damageResult, engine);
            } catch (Throwable t) {
                Guard.fail(engine, lent.key, t);
            }
        }
    }

    /**
     * The design a hit belongs to: the projectile's own id, or for a warhead a design's splitting
     * missile let loose (its id is the launcher's warhead id), the weapon that fired the missile.
     * This script is only named on the mod's own projectiles and warheads, so the weapon is ours.
     */
    static String designOf(DamagingProjectileAPI projectile) {
        String id = projectile.getProjectileSpecId();
        if (Kind.ofWeaponId(id) != null) return id;
        WeaponAPI weapon = projectile.getWeapon();
        return weapon != null && Kind.ofWeaponId(weapon.getId()) != null ? weapon.getId() : id;
    }
}
