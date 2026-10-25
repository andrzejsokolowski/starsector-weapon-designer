package weapondesigner.combat;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;

/**
 * Named in every blank shot, laser and missile .proj file. Runs the scripts of whichever design
 * the weapon carries right now; does nothing for designs without scripts.
 */
public class ScriptHitEffect implements OnHitEffectPlugin {
    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit,
                      ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        if (projectile == null) return;
        int[] levels = ScriptBook.of(projectile.getProjectileSpecId());
        if (levels == null) return;
        float hullDamage = damageResult == null ? 0f : damageResult.getDamageToHull();
        Effects.hit(engine, projectile.getSource(), target, point, shieldHit, projectile.getDamageAmount(),
                projectile.getDamageType(), hullDamage, levels);
    }
}
