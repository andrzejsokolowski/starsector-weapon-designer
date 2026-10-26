package weapondesigner.combat;

import java.awt.Color;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;

import weapondesigner.Script;

/**
 * What a script does when its weapon hits. Shared by projectiles, missiles and beam pulses.
 * Every call here is one the base game's own weapon effects make from their on-hit code
 * (Ion Cannon, Ion Beam, Mote, Disintegrator, Paladin).
 */
final class Effects {
    /** Arcs per hit are capped here and each arc grows instead, so high levels do not flood the screen. */
    private static final int MAX_ARCS = 8;
    /**
     * Arcs and blasts drawn per frame across the whole battle. A fleet of fast guns with high
     * levels could otherwise ask for hundreds at once; past this the damage still lands, unseen.
     */
    private static final int SHOWS_PER_FRAME = 40;
    private static final String BUDGET_KEY = "weapondesigner_shows_this_frame";
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
        if (engine == null || target == null || point == null || levels == null || !(damage > 0f)) return;
        ShipAPI ship = target instanceof ShipAPI ? (ShipAPI) target : null;
        boolean shipAlive = ship != null && ship.isAlive() && !ship.isHulk();

        int arc = levels[Script.ARC.ordinal()];
        if (arc > 0 && shipAlive && !shieldHit && source != null) {
            arcs(engine, source, ship, point, Script.arcsPerHit(arc), damage, false);
        }
        int breach = levels[Script.BREACH.ordinal()];
        if (breach > 0 && shipAlive && shieldHit && source != null) {
            // Like the base game's piercing arcs, ships that resist shield piercing get fewer.
            float resist = ship.getMutableStats().getDynamic().getValue(Stats.SHIELD_PIERCED_MULT);
            arcs(engine, source, ship, point, Script.breachArcsPerHit(breach) * Math.max(0f, resist), damage, true);
        }
        int shred = levels[Script.SHRED.ordinal()];
        if (shred > 0 && shipAlive && !shieldHit) {
            engine.applyDamage(target, point, damage * Script.shredShare(shred), DamageType.HIGH_EXPLOSIVE, 0f,
                    true, false, source);
        }
        int overload = levels[Script.OVERLOAD.ordinal()];
        if (overload > 0 && shipAlive && shieldHit && ship.getFluxTracker() != null) {
            ship.getFluxTracker().increaseFlux(damage * Script.overloadShare(overload), true);
        }
        int leech = levels[Script.LEECH.ordinal()];
        if (leech > 0 && source != null && source.isAlive() && !source.isHulk() && hullDamage > 0f) {
            float hp = source.getHitpoints() + hullDamage * Script.leechShare(leech);
            source.setHitpoints(Math.min(source.getMaxHitpoints(), hp));
        }
        int slow = levels[Script.SLOW.ordinal()];
        if (slow > 0 && shipAlive && !shieldHit) {
            Slowdowns.of(engine).slow(ship, Script.slowMult(slow));
        }
        int blast = levels[Script.BLAST.ordinal()];
        if (blast > 0 && source != null) {
            blast(engine, source, target, point, damage * Script.blastShare(blast), Script.blastRadius(blast), type);
        }
    }

    /** True while this frame still has room for another arc or blast. */
    private static boolean show(CombatEngineAPI engine) {
        float now = engine.getTotalElapsedTime(true);
        Object stored = engine.getCustomData().get(BUDGET_KEY);
        float[] budget = stored instanceof float[] ? (float[]) stored : null;
        if (budget == null) {
            budget = new float[]{-1f, 0f};
            engine.getCustomData().put(BUDGET_KEY, budget);
        }
        if (budget[0] != now) {
            budget[0] = now;
            budget[1] = 0f;
        }
        return budget[1]++ < SHOWS_PER_FRAME;
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
            if (!show(engine)) {
                engine.applyDamage(target, point, each * (count - i), DamageType.ENERGY, each * (count - i), pierce,
                        false, source);
                return;
            }
            if (pierce) {
                engine.spawnEmpArcPierceShields(source, point, target, target, DamageType.ENERGY, each, each, 100000f,
                        "tachyon_lance_emp_impact", thickness, BREACH_FRINGE, ARC_CORE);
            } else {
                engine.spawnEmpArc(source, point, target, target, DamageType.ENERGY, each, each, 100000f,
                        "shock_repeater_emp_impact", thickness, ARC_FRINGE, ARC_CORE);
            }
        }
    }

    private static void blast(CombatEngineAPI engine, ShipAPI source, CombatEntityAPI target, Vector2f point,
                              float damage, float radius, DamageType type) {
        DamageType blastType = type == null ? DamageType.HIGH_EXPLOSIVE : type;
        if (!show(engine)) {
            // Over this frame's budget: the hit target still takes the blast, its neighbours do not.
            if (target instanceof ShipAPI) engine.applyDamage(target, point, damage, blastType, 0f, false, false, source);
            return;
        }
        DamagingExplosionSpec spec = new DamagingExplosionSpec(0.1f, radius, radius * 0.5f, damage, damage * 0.5f,
                CollisionClass.PROJECTILE_NO_FF, CollisionClass.PROJECTILE_FIGHTER, 3f, 3f, 0.5f,
                (int) Math.min(60f, 10f + radius / 10f), BLAST_COLOR, BLAST_COLOR);
        spec.setDamageType(blastType);
        spec.setUseDetailedExplosion(false);
        spec.setSoundSetId("explosion_flak");
        engine.spawnDamagingExplosion(spec, source, point);
    }
}
