package weapondesigner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.loading.BeamWeaponSpecAPI;
import com.fs.starfarer.api.loading.MissileSpecAPI;
import com.fs.starfarer.api.loading.ProjectileSpawnType;
import com.fs.starfarer.api.loading.ProjectileSpecAPI;
import com.fs.starfarer.api.loading.ProjectileWeaponSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.loading.specs.BaseWeaponSpec;

/**
 * A design borrows the art, barrel layout and sounds of an existing weapon of the same kind, size
 * and mount type. Only art the game has already loaded is used, so nothing new has to be read.
 */
public final class Looks {
    /** Weapons an automatic look is taken from: no restricted ones. */
    private static final List<WeaponSpecAPI> DONORS = new ArrayList<>();
    /** Every weapon the player may pick a look from, sorted by name. */
    private static final List<WeaponSpecAPI> PICKABLE = new ArrayList<>();

    private Looks() {
    }

    /** Indexes the weapons a design may borrow from. Called once the game has loaded every weapon. */
    public static void index() {
        DONORS.clear();
        PICKABLE.clear();
        for (WeaponSpecAPI w : Global.getSettings().getAllWeaponSpecs()) {
            try {
                if (kindOf(w) == null || !usable(w)) continue;
                PICKABLE.add(w);
                if (!w.hasTag("restricted")) DONORS.add(w);
            } catch (Exception e) {
                // A weapon from another mod that does not answer like a normal one is simply skipped.
            }
        }
        DONORS.sort(Comparator.comparing(WeaponSpecAPI::getWeaponId));
        PICKABLE.sort(Comparator.comparing((WeaponSpecAPI w) -> String.valueOf(w.getWeaponName()).toLowerCase())
                .thenComparing(WeaponSpecAPI::getWeaponId));
    }

    /** The weapons whose look a design of this kind and size can wear. */
    public static List<WeaponSpecAPI> choices(Kind kind, WeaponSize size) {
        List<WeaponSpecAPI> out = new ArrayList<>();
        for (WeaponSpecAPI w : PICKABLE) {
            if (kindOf(w) == kind && w.getSize() == size) out.add(w);
        }
        return out;
    }

    /** The weapon the player picked, when it still fits the design's kind and size. */
    public static WeaponSpecAPI chosen(Design d) {
        if (d.look == null) return null;
        for (WeaponSpecAPI w : PICKABLE) {
            if (w.getWeaponId().equals(d.look)) return kindOf(w) == d.kind && w.getSize() == d.size ? w : null;
        }
        return null;
    }

    /** The weapon whose look this design wears: the player's pick, or one chosen automatically. */
    public static WeaponSpecAPI donorFor(Design d) {
        WeaponSpecAPI picked = chosen(d);
        if (picked != null) return picked;
        List<WeaponSpecAPI> best = new ArrayList<>();
        List<WeaponSpecAPI> sameSize = new ArrayList<>();
        List<WeaponSpecAPI> sameKind = new ArrayList<>();
        for (WeaponSpecAPI w : DONORS) {
            if (kindOf(w) != d.kind) continue;
            sameKind.add(w);
            if (w.getSize() != d.size) continue;
            sameSize.add(w);
            if (w.getType() == d.mount) best.add(w);
        }
        List<WeaponSpecAPI> pool = preferBase(!best.isEmpty() ? best : !sameSize.isEmpty() ? sameSize : sameKind);
        if (pool.isEmpty()) return null;
        String seed = d.weaponId != null ? d.weaponId : d.name;
        return pool.get(Math.floorMod(seed == null ? 0 : seed.hashCode(), pool.size()));
    }

    /** Copies the donor's art, barrels and sounds onto the design's weapon. */
    public static void apply(BaseWeaponSpec spec, Design d) {
        WeaponSpecAPI donor = donorFor(d);
        if (donor == null) return;
        spec.setTurretSpriteName(donor.getTurretSpriteName());
        spec.setHardpointSpriteName(donor.getHardpointSpriteName());
        spec.setTurretUnderSpriteName(donor.getTurretUnderSpriteName());
        spec.setHardpointUnderSpriteName(donor.getHardpointUnderSpriteName());
        copyPoints(donor.getTurretFireOffsets(), spec.getTurretFireOffsets());
        copyAngles(donor.getTurretAngleOffsets(), spec.getTurretAngleOffsets());
        copyPoints(donor.getHardpointFireOffsets(), spec.getHardpointFireOffsets());
        copyAngles(donor.getHardpointAngleOffsets(), spec.getHardpointAngleOffsets());
        copyPoints(donor.getHiddenFireOffsets(), spec.getHiddenFireOffsets());
        copyAngles(donor.getHiddenAngleOffsets(), spec.getHiddenAngleOffsets());
        if (donor instanceof BaseWeaponSpec) {
            BaseWeaponSpec base = (BaseWeaponSpec) donor;
            spec.setFireSoundOne(base.getFireSoundOne());
            spec.setFireSoundTwo(base.getFireSoundTwo());
        }

        if (spec instanceof ProjectileWeaponSpecAPI && donor instanceof ProjectileWeaponSpecAPI) {
            ProjectileWeaponSpecAPI to = (ProjectileWeaponSpecAPI) spec;
            ProjectileWeaponSpecAPI from = (ProjectileWeaponSpecAPI) donor;
            to.setTurretGunSpriteName(from.getTurretGunSpriteName());
            to.setHardpointGunSpriteName(from.getHardpointGunSpriteName());
            to.setVisualRecoil(from.getTurretGunSpriteName() == null ? 0f : from.getVisualRecoil());
            if (from.getTurretGlowSpriteName() != null) {
                to.setTurretGlowSpriteName(from.getTurretGlowSpriteName());
                to.setHardpointGlowSpriteName(from.getHardpointGlowSpriteName());
            }
            if (to.getProjectileSpec() instanceof ProjectileSpecAPI
                    && from.getProjectileSpec() instanceof ProjectileSpecAPI) {
                copyShot((ProjectileSpecAPI) from.getProjectileSpec(), (ProjectileSpecAPI) to.getProjectileSpec());
            }
        }
        if (spec instanceof BeamWeaponSpecAPI && donor instanceof BeamWeaponSpecAPI) {
            BeamWeaponSpecAPI to = (BeamWeaponSpecAPI) spec;
            BeamWeaponSpecAPI from = (BeamWeaponSpecAPI) donor;
            if (from.getTurretGlowSpriteName() != null) {
                to.setTurretGlowSpriteName(from.getTurretGlowSpriteName());
                to.setHardpointGlowSpriteName(from.getHardpointGlowSpriteName());
            }
            to.setFringeColor(from.getFringeColor());
            to.setCoreColor(from.getCoreColor());
            to.setGlowColor(from.getGlowColor());
            to.setWidth(from.getWidth());
            if (from.getFringeTex() != null) to.setFringeTex(from.getFringeTex());
            if (from.getCoreTex() != null) to.setCoreTex(from.getCoreTex());
            to.setTextureScrollSpeed(from.getTextureScrollSpeed());
            to.setPixelsPerTexel(from.getPixelsPerTexel());
        }
    }

    static Kind kindOf(WeaponSpecAPI w) {
        if (w instanceof BeamWeaponSpecAPI) return Kind.BEAM;
        if (!(w instanceof ProjectileWeaponSpecAPI)) return null;
        Object shot = w.getProjectileSpec();
        if (shot instanceof MissileSpecAPI) return Kind.MISSILE;
        if (shot instanceof ProjectileSpecAPI) {
            ProjectileSpawnType type = ((ProjectileSpecAPI) shot).getSpawnType();
            if (type == ProjectileSpawnType.BALLISTIC) return Kind.SHOT;
            if (type == ProjectileSpawnType.BALLISTIC_AS_BEAM) return Kind.LASER;
        }
        return null;
    }

    private static boolean usable(WeaponSpecAPI w) {
        String id = w.getWeaponId();
        if (id == null || id.startsWith("wd_") || id.contains("fighter")) return false;
        WeaponType type = w.getType();
        if (type != WeaponType.BALLISTIC && type != WeaponType.ENERGY && type != WeaponType.MISSILE) return false;
        if (w.getSize() != WeaponSize.SMALL && w.getSize() != WeaponSize.MEDIUM && w.getSize() != WeaponSize.LARGE)
            return false;
        if (w.getAIHints().contains(AIHints.SYSTEM)) return false;
        if (w.getTurretSpriteName() == null || w.getTurretSpriteName().isEmpty()) return false;
        if (w.getHardpointSpriteName() == null || w.getHardpointSpriteName().isEmpty()) return false;
        if (w.getTurretFireOffsets().isEmpty() || w.getHardpointFireOffsets().isEmpty()) return false;
        if (kindOf(w) == Kind.LASER) {
            ProjectileWeaponSpecAPI p = (ProjectileWeaponSpecAPI) w;
            if (p.getTurretGlowSpriteName() == null || p.getHardpointGlowSpriteName() == null) return false;
        }
        return true;
    }

    private static List<WeaponSpecAPI> preferBase(List<WeaponSpecAPI> pool) {
        List<WeaponSpecAPI> base = new ArrayList<>();
        for (WeaponSpecAPI w : pool) if (w.getSourceMod() == null) base.add(w);
        return base.isEmpty() ? pool : base;
    }

    private static void copyShot(ProjectileSpecAPI from, ProjectileSpecAPI to) {
        if (from.getBulletSpriteName() != null) to.setBulletSpriteName(from.getBulletSpriteName());
        if (from.getFringeTex() != null) to.setFringeTex(from.getFringeTex());
        if (from.getCoreTex() != null) to.setCoreTex(from.getCoreTex());
        to.setLength(from.getLength());
        to.setWidth(from.getWidth());
        to.setFadeTime(from.getFadeTime());
        to.setFringeColor(from.getFringeColor());
        to.setCoreColor(from.getCoreColor());
        to.setGlowColor(from.getGlowColor());
        to.setGlowRadius(from.getGlowRadius());
        to.setHitGlowRadius(from.getHitGlowRadius());
        to.setTextureScrollSpeed(from.getTextureScrollSpeed());
        to.setPixelsPerTexel(from.getPixelsPerTexel());
    }

    private static void copyPoints(List<Vector2f> from, List<Vector2f> to) {
        if (from == null || to == null || from == to) return;
        to.clear();
        for (Vector2f v : from) to.add(new Vector2f(v));
    }

    private static void copyAngles(List<Float> from, List<Float> to) {
        if (from == null || to == null || from == to) return;
        to.clear();
        to.addAll(from);
    }
}
