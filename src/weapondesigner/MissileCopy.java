package weapondesigner;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.json.JSONObject;

import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.loading.specs.BaseWeaponSpec;
import com.fs.starfarer.loading.specs.D;
import com.fs.starfarer.loading.specs.OOO0;
import com.fs.starfarer.loading.specs.OoOo;
import com.fs.starfarer.loading.specs.Y;
import com.fs.starfarer.loading.specs.g;
import com.fs.starfarer.loading.specs.h;

/**
 * A missile design whose look the player picked fires a copy of that launcher's own missile: its
 * sprite, engine trails and the way it flies (a Pilum's second stage, a Hurricane's warheads, a
 * mine), with the design's own numbers written on top by {@link SpecWriter}.
 *
 * The game keeps the kind of missile (plain, rocket, splitting, mine) inside its missile object
 * with no way to change it, so a copy is built new, the way the game's missile loader builds one
 * (WeaponSpecLoader in 0.98.5a), and handed to the blank weapon. The copy has its own hull (the
 * missile's sprite and engine trails, its own engine, and the blank weapon's id, so the design's
 * scripts still find it), its own damage, blast and behavior settings. Parts the design never
 * changes (sprite, engine slots, the warheads' own missile files) are shared and never written.
 * Checked against the game's classes outside the game on 2026-11-19 (the launcher stays untouched).
 *
 * Names here are the game's scrambled class names in 0.98.5a and change between releases. Every
 * caller catches anything thrown, so a game update only turns copying off: the design then fires
 * the blank weapon's plain missile, as before 1.1.0.
 */
public final class MissileCopy {
    /** The blank weapon's own missile, by weapon id, kept the first time it is touched in a session. */
    private static final Map<String, h> ORIGINALS = new HashMap<>();
    /**
     * Behavior settings made here, so the design's numbers are never written into a launcher's own.
     * The game's class keeps Object's equals, so this weak set goes by identity.
     */
    private static final Set<Object> OWN_BEHAVIORS = Collections.newSetFromMap(new WeakHashMap<>());
    private static MethodHandle setter;

    private MissileCopy() {
    }

    /**
     * Gives a blank missile weapon a copy of the picked launcher's missile, or its own plain
     * missile back when there is no pick (automatic look, blank weapon) or nothing to copy.
     */
    public static void apply(BaseWeaponSpec spec, Design d, WeaponSpecAPI picked) throws Throwable {
        if (d.weaponId == null || !(spec.getProjectileSpec() instanceof h)) return;
        h original = ORIGINALS.get(d.weaponId);
        if (original == null) {
            original = (h) spec.getProjectileSpec();
            ORIGINALS.put(d.weaponId, original);
        }
        Object from = picked == null ? null : picked.getProjectileSpec();
        if (from instanceof h && from != original) {
            set(spec, build((h) from, original, d));
        } else if (spec.getProjectileSpec() != original) {
            set(spec, original);
        }
    }

    /** True for behavior settings this class made, which the design may write its numbers into. */
    public static boolean ownsBehavior(Object behavior) {
        return behavior != null && OWN_BEHAVIORS.contains(behavior);
    }

    /**
     * Shows the launcher's loaded missiles on the design's launcher, as the launcher does. The
     * game can add these drawing hints but not take them off, so a blank weapon keeps them until
     * the game restarts.
     */
    public static void copyDrawing(BaseWeaponSpec spec, WeaponSpecAPI picked) {
        if (!(picked instanceof BaseWeaponSpec)) return;
        for (BaseWeaponSpec.o drawing : BaseWeaponSpec.o.values()) {
            if (((BaseWeaponSpec) picked).hasHint(drawing) && !spec.hasHint(drawing)) spec.addRenderHint(drawing);
        }
    }

    /**
     * A missile's hull is built the way the game's own missile loader builds one: its id, sprite,
     * engine, armor, collision, mass and engine slots. (The hull's clone() expects ship parts a
     * missile does not have, so it cannot be used.) The engine is the copy's own, because the
     * design writes its speed and turn rate into it. Health and mass stay the blank weapon's, so
     * a look changes no number the design does not price.
     */
    private static g hull(g from, g ours, String id) {
        g hull = new g(id);
        hull.setHullId(id);
        hull.setSpriteSpec(from.getSpriteSpec());
        hull.setEngineSpec(((Y) from.getEngineSpec()).clone());
        hull.setArmorSpec(ours.getArmorSpec());
        hull.setCollisionClass(from.getCollisionClass());
        hull.setMass(ours.getMass());
        for (D slot : from.getEngineSlots()) hull.addEngineSlot(slot);
        return hull;
    }

    private static h build(h from, h ours, Design d) throws Exception {
        g hull = hull(from.getHullSpec(), ours.getHullSpec(), d.weaponId);
        h.o type = from.getType();
        // A rocket look on a design that turns keeps the plain guided missile, so the turn rate it paid for works.
        if ("ROCKET".equals(from.getTypeString()) && d.value(Stat.GUIDANCE) > 0f) type = ours.getType();
        h copy = new h(type, hull, from.getDamage().clone(), from.getGlowColor(), from.getGlowSpriteName(),
                from.getLaunchSpeed(), from.getMaxFlightTime());
        copy.setArmingTime(from.getArmingTime());
        copy.setFlameoutTime(from.getFlameoutTime());
        copy.setNoEngineGlowTime(from.getNoEngineGlowTime());
        copy.setFadeTime(from.getFadeTime());
        copy.setAfterFlameoutCollisionClass(from.getAfterFlameoutCollisionClass());
        copy.setExplosionColor(from.getExplosionColor());
        copy.setGlowRadius(from.getGlowRadius());
        copy.setExplosionRadius(from.getExplosionRadius());
        copy.setImpactStrength(from.getImpactStrength());
        copy.setDudProbabilityOnFlameout(from.getDudProbabilityOnFlameout());
        copy.setUseHitGlowWhenDestroyed(from.isUseHitGlowWhenDestroyed());
        copy.setFizzleOnReachingWeaponRange(from.isFizzleOnReachingWeaponRange());
        copy.setUseHitGlowWhenDealingDamage(from.isUseHitGlowWhenDealingDamage());
        copy.setNoCollisionWhileFading(from.isNoCollisionWhileFading());
        copy.setReduceDamageWhileFading(from.isReduceDamageWhileFading());
        copy.setAlwaysAccelerate(from.isAlwaysAccelerate());
        copy.setUseProjectileRangeCalculation(from.isUseProjectileRangeCalculation());
        copy.setRenderTargetIndicator(from.isRenderTargetIndicator());
        copy.setNoDebrisWhenDestroyed(from.isNoDebrisWhenDestroyed());
        if (!from.isDefaultDestroyedExplosionColor()) copy.setDestroyedExplosionColor(from.getDestroyedExplosionColor());
        copy.setPassThroughMissiles(from.isPassThroughMissiles());
        copy.setPassThroughFighters(from.isPassThroughFighters());
        copy.setApplyOnHitEffectWhenPassThrough(from.isApplyOnHitEffectWhenPassThrough());
        copy.setPassThroughFightersOnlyWhenDestroyed(from.isPassThroughFightersOnlyWhenDestroyed());
        copy.setExplodesWhenHittingFlares(from.isExplodesWhenHittingFlares());
        DamagingExplosionSpec blast = from.getExplosionSpec();
        copy.setExplosionSpec(blast == null ? null : blast.clone());
        OOO0 behavior = from.getBehaviorSpec();
        if (behavior != null) {
            JSONObject params = behavior.getParams();
            OOO0 own = new OOO0(behavior.getBehavor(), params == null ? null : new JSONObject(params.toString()));
            own.setOnExplosionClassName(behavior.getOnExplosionClassName());
            own.setOnHitClassName(behavior.getOnHitClassName());
            copy.setBehaviorSpec(own);
            OWN_BEHAVIORS.add(own);
        }
        // The design's own scripts, not the launcher's: those are borrowed on the Borrowed tab.
        copy.setOnHitClassName(ours.getOnHitClassName());
        copy.setOnFireClassName(ours.getOnFireClassName());
        return copy;
    }

    /**
     * The projectile weapon class's name is a Java keyword in 0.98.5a, so its public setter is
     * called through a method handle (the same way LunaLib reaches the game's classes). The game
     * stores a copy of what it is given.
     */
    private static void set(BaseWeaponSpec spec, h missile) throws Throwable {
        MethodHandle handle = setter;
        if (handle == null || !handle.type().parameterType(0).isInstance(spec)) {
            handle = MethodHandles.publicLookup().findVirtual(spec.getClass(), "setProjectileSpec",
                    MethodType.methodType(void.class, OoOo.class));
            setter = handle;
        }
        handle.invoke(spec, missile);
    }
}
