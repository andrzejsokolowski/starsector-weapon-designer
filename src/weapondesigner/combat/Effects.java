package weapondesigner.combat;

import java.awt.Color;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;

import weapondesigner.Script;

/** What a script does when its weapon hits. Shared by projectiles, missiles and beam pulses. */
final class Effects {
    /** Arcs per hit are capped here and each arc grows instead, so high levels do not flood the screen. */
    private static final int MAX_ARCS = 8;
    private static final Color ARC_FRINGE = new Color(100, 170, 255, 255);
    private static final Color ARC_CORE = new Color(255, 255, 255, 255);
    private static final Color BREACH_FRINGE = new Color(190, 120, 255, 255);
    private static final Color BLAST_COLOR = new Color(255, 170, 90, 255);

    private Effects() {
    }

    /**
     * @param damage     the hit's damage
     * @param hullDamage damage that reached the hull, for Leech
     */
    static void hit(CombatEngineAPI engine, ShipAPI source, CombatEntityAPI target, Vector2f point, boolean shieldHit,
                    float damage, DamageType type, float hullDamage, int[] levels) {
        if (engine == null || target == null || point == null || levels == null || damage <= 0f) return;
        ShipAPI ship = target instanceof ShipAPI ? (ShipAPI) target : null;
        boolean shipAlive = ship != null && ship.isAlive() && !ship.isHulk();

        int arc = levels[Script.ARC.ordinal()];
        if (arc > 0 && shipAlive && !shieldHit) {
            arcs(engine, source, ship, point, Script.arcsPerHit(arc), damage, false);
        }
        int breach = levels[Script.BREACH.ordinal()];
        if (breach > 0 && shipAlive && shieldHit) {
            arcs(engine, source, ship, point, Script.breachArcsPerHit(breach), damage, true);
        }
        int shred = levels[Script.SHRED.ordinal()];
        if (shred > 0 && ship != null && !shieldHit) {
            engine.applyDamage(target, point, damage * Script.shredShare(shred), DamageType.HIGH_EXPLOSIVE, 0f,
                    true, false, source);
        }
        int overload = levels[Script.OVERLOAD.ordinal()];
        if (overload > 0 && shipAlive && shieldHit && ship.getFluxTracker() != null) {
            ship.getFluxTracker().increaseFlux(damage * Script.overloadShare(overload), true);
        }
        int leech = levels[Script.LEECH.ordinal()];
        if (leech > 0 && source != null && source.isAlive() && hullDamage > 0f) {
            float hp = source.getHitpoints() + hullDamage * Script.leechShare(leech);
            source.setHitpoints(Math.min(source.getMaxHitpoints(), hp));
        }
        int slow = levels[Script.SLOW.ordinal()];
        if (slow > 0 && shipAlive && !shieldHit) {
            Slowdowns.of(engine).slow(ship, Script.slowMult(slow));
        }
        int blast = levels[Script.BLAST.ordinal()];
        if (blast > 0) blast(engine, source, point, damage * Script.blastShare(blast), Script.blastRadius(blast), type);
    }

    private static void arcs(CombatEngineAPI engine, ShipAPI source, ShipAPI target, Vector2f point, float expected,
                             float damage, boolean pierce) {
        int count = (int) expected;
        if (Math.random() < expected - count) count++;
        if (count <= 0) return;
        float scale = 1f;
        if (count > MAX_ARCS) {
            scale = count / (float) MAX_ARCS;
            count = MAX_ARCS;
        }
        float each = damage * 0.5f * scale;
        float thickness = Math.min(60f, 15f + (float) Math.sqrt(each));
        for (int i = 0; i < count; i++) {
            if (pierce) {
                engine.spawnEmpArcPierceShields(source, point, target, target, DamageType.ENERGY, each, each, 100000f,
                        "tachyon_lance_emp_impact", thickness, BREACH_FRINGE, ARC_CORE);
            } else {
                engine.spawnEmpArc(source, point, target, target, DamageType.ENERGY, each, each, 100000f,
                        "shock_repeater_emp_impact", thickness, ARC_FRINGE, ARC_CORE);
            }
        }
    }

    private static void blast(CombatEngineAPI engine, ShipAPI source, Vector2f point, float damage, float radius,
                              DamageType type) {
        DamagingExplosionSpec spec = new DamagingExplosionSpec(0.12f, radius, radius * 0.5f, damage, damage * 0.5f,
                CollisionClass.PROJECTILE_NO_FF, CollisionClass.PROJECTILE_FIGHTER, 4f, 4f, 0.6f,
                (int) Math.min(60f, 10f + radius / 10f), BLAST_COLOR, BLAST_COLOR);
        spec.setDamageType(type == null ? DamageType.HIGH_EXPLOSIVE : type);
        engine.spawnDamagingExplosion(spec, source, point);
        Global.getSoundPlayer().playSound("explosion_flak", 1f, Math.min(1f, 0.4f + radius / 400f), point, new Vector2f());
    }
}
