package weapondesigner.combat;

import java.util.Map;
import java.util.WeakHashMap;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;

/**
 * Fire rates above what the game fires reliably. The game fires a weapon at most once per frame,
 * so a design faster than {@link #NATIVE_RATE} has its weapon fire at that rate, and every real
 * shot is topped up here with extra projectiles until the total matches the design (Andrzej's
 * idea: 90 shots a second at 60 real shots is two projectiles half the time).
 *
 * The count follows the time actually passed since the weapon's last shot, so frame drops and
 * the game's own rounding of refire times cannot lose shots. The extras are placed along the
 * shot's path as if they had been fired in between, so they read as a stream. Past
 * {@code maxPerShot} projectiles per real shot, the rest of the rate makes every projectile of
 * that shot hit harder instead, so an absurd rate cannot fill the battle with bullets.
 *
 * Named in every blank shot, laser and missile .proj file. The game calls it from the weapon's own
 * firing code; projectiles spawned from a script do not call it again.
 */
public class RapidFire implements OnFireEffectPlugin {
    /** Shots per second the weapon itself fires at most; the rest are extras. */
    public static final float NATIVE_RATE = 30f;
    /** Projectiles per real shot for guns and lasers before shots get heavier instead. */
    public static final int MAX_PER_SHOT = 6;
    /** The same for missiles, which are far heavier for the game to fly. */
    public static final int MAX_PER_MISSILE = 2;

    private static final String KEY = "weapondesigner_rapid_fire";

    /** What one real shot gets. */
    public static final class Plan {
        /** Extra projectiles to spawn. */
        public int extras;
        /** Damage multiplier for every projectile of this shot (1 unless past the limit). */
        public float scale = 1f;
        /** Shots the extras and the heavier damage stand for, beyond the real one. */
        public double represented;
        /** Fraction of a shot carried to the next real shot. */
        public double carry;
    }

    /**
     * @param rate       designed shots per second
     * @param elapsed    seconds since the weapon's previous real shot
     * @param carry      what the previous shot carried over
     * @param maxPerShot projectiles allowed per real shot, the real one included
     * @param affordable most extra shots flux or ammo allow, or infinity
     */
    public static Plan plan(double rate, double elapsed, double carry, int maxPerShot, double affordable) {
        Plan plan = new Plan();
        double owed = rate * elapsed - 1.0 + carry;
        double whole = Math.max(0.0, Math.floor(owed));
        plan.carry = owed - whole;
        whole = Math.min(whole, Math.max(0.0, Math.floor(affordable)));
        plan.represented = whole;
        plan.extras = (int) Math.min(whole, maxPerShot - 1);
        plan.scale = (float) ((whole + 1.0) / (plan.extras + 1.0));
        return plan;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        if (projectile == null || weapon == null || engine == null) return;
        ScriptBook.Rate rate = ScriptBook.rateOf(projectile.getProjectileSpecId());
        ShipAPI ship = weapon.getShip();
        if (rate == null || ship == null) return;

        Object stored = engine.getCustomData().get(KEY);
        if (!(stored instanceof Map)) {
            stored = new WeakHashMap<WeaponAPI, double[]>();
            engine.getCustomData().put(KEY, stored);
        }
        Map<WeaponAPI, double[]> state = (Map<WeaponAPI, double[]>) stored;
        double now = engine.getTotalElapsedTime(false);
        double[] last = state.get(weapon);
        double elapsed;
        if (last == null) {
            last = new double[]{now, 0.0};
            state.put(weapon, last);
            elapsed = 1.0 / rate.shotsPerSecond;
        } else {
            // A weapon that was idle starts over instead of firing everything it "missed".
            elapsed = Math.min(now - last[0], rate.longestGap * 1.5);
            last[0] = now;
        }
        elapsed *= ship.getMutableStats().getTimeMult().getModifiedValue();

        double affordable = Double.POSITIVE_INFINITY;
        float fluxPerShot = weapon.getFluxCostToFire();
        if (fluxPerShot > 0f && ship.getFluxTracker() != null) {
            float room = ship.getFluxTracker().getMaxFlux() - ship.getFluxTracker().getCurrFlux();
            affordable = Math.max(0f, room) / fluxPerShot;
        }
        if (weapon.usesAmmo()) affordable = Math.min(affordable, weapon.getAmmo());

        Plan plan = plan(rate.shotsPerSecond, Math.max(0.0, elapsed), last[1], rate.maxPerShot, affordable);
        last[1] = plan.carry;
        if (plan.represented <= 0.0) return;

        if (fluxPerShot > 0f && ship.getFluxTracker() != null) {
            ship.getFluxTracker().increaseFlux((float) (fluxPerShot * plan.represented), false);
        }
        if (weapon.usesAmmo()) weapon.setAmmo((int) Math.max(0, weapon.getAmmo() - (long) plan.represented));
        if (plan.scale > 1f) heavier(projectile, plan.scale);

        Vector2f from = new Vector2f(projectile.getLocation());
        float spread = weapon.getCurrSpread();
        for (int i = 1; i <= plan.extras; i++) {
            float angle = projectile.getFacing() + ((float) Math.random() - 0.5f) * spread;
            CombatEntityAPI extra = engine.spawnProjectile(ship, weapon, weapon.getId(), new Vector2f(from), angle,
                    ship.getVelocity());
            if (extra == null) continue;
            // As if fired a little earlier: already a little way down its path.
            float age = (float) (elapsed * i / (plan.extras + 1.0));
            Vector2f v = extra.getVelocity();
            extra.getLocation().translate(v.x * age, v.y * age);
            if (plan.scale > 1f && extra instanceof DamagingProjectileAPI) {
                heavier((DamagingProjectileAPI) extra, plan.scale);
            }
        }
    }

    private static void heavier(DamagingProjectileAPI projectile, float scale) {
        projectile.setDamageAmount(projectile.getDamageAmount() * scale);
        if (projectile.getDamage() != null) {
            projectile.getDamage().setFluxComponent(projectile.getDamage().getFluxComponent() * scale);
        }
    }
}
