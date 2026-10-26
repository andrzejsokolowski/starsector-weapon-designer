package weapondesigner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import com.fs.starfarer.api.loading.BeamWeaponSpecAPI;
import com.fs.starfarer.api.loading.MissileSpecAPI;
import com.fs.starfarer.api.loading.ProjectileSpecAPI;
import com.fs.starfarer.api.loading.ProjectileWeaponSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.loading.specs.BaseWeaponSpec;

/**
 * Which loaded weapons, base game or modded, have scripts a design can borrow, and of what type.
 *
 * Weapons fall into three families by what they fire: guns (any projectile that is not a
 * missile, so lasers and plasma too), missiles and beams. A design only borrows from its own
 * family, because many scripts take for granted the kind of shot they get.
 */
public final class Borrowing {
    /** A weapon with at least one borrowable script. */
    public static final class Donor {
        public final WeaponSpecAPI spec;
        public final Family family;
        public final EnumSet<Borrowed.Type> types;

        Donor(WeaponSpecAPI spec, Family family, EnumSet<Borrowed.Type> types) {
            this.spec = spec;
            this.family = family;
            this.types = types;
        }
    }

    public enum Family {
        GUN, MISSILE, BEAM;

        public static Family of(Kind kind) {
            return kind == Kind.BEAM ? BEAM : kind == Kind.MISSILE ? MISSILE : GUN;
        }
    }

    private static final List<Donor> DONORS = new ArrayList<>();

    private Borrowing() {
    }

    /** Called once the game has loaded every weapon. */
    public static void index() {
        DONORS.clear();
        for (WeaponSpecAPI w : Global.getSettings().getAllWeaponSpecs()) {
            try {
                Donor donor = examine(w);
                if (donor != null) DONORS.add(donor);
            } catch (Throwable t) {
                // A weapon that does not answer like a normal one is simply not offered.
            }
        }
        DONORS.sort(Comparator.comparing((Donor d) -> String.valueOf(d.spec.getWeaponName()).toLowerCase())
                .thenComparing(d -> d.spec.getWeaponId()));
        Global.getLogger(Borrowing.class).info("Weapon Designer: " + DONORS.size() + " weapons have scripts to borrow");
    }

    private static Donor examine(WeaponSpecAPI w) {
        String id = w.getWeaponId();
        if (id == null || Kind.ofWeaponId(id) != null || w.getAIHints().contains(AIHints.SYSTEM)) return null;
        Family family = familyOf(w);
        if (family == null) return null;
        EnumSet<Borrowed.Type> types = EnumSet.noneOf(Borrowed.Type.class);
        Object shot = w instanceof ProjectileWeaponSpecAPI ? w.getProjectileSpec() : null;
        if (shot instanceof ProjectileSpecAPI) {
            if (foreign(((ProjectileSpecAPI) shot).getOnHitEffect())) types.add(Borrowed.Type.HIT);
            if (foreign(((ProjectileSpecAPI) shot).getOnFireEffect())) types.add(Borrowed.Type.FIRE);
        } else if (shot instanceof MissileSpecAPI) {
            if (foreign(((MissileSpecAPI) shot).getOnHitEffect())) types.add(Borrowed.Type.HIT);
            if (foreign(((MissileSpecAPI) shot).getOnFireEffect())) types.add(Borrowed.Type.FIRE);
        }
        if (w instanceof BaseWeaponSpec && foreign(((BaseWeaponSpec) w).getEveryFrameClassName())) {
            types.add(Borrowed.Type.FRAME);
        }
        if (w instanceof BeamWeaponSpecAPI && foreign(((BeamWeaponSpecAPI) w).getEffectClassName())) {
            types.add(Borrowed.Type.BEAM);
        }
        return types.isEmpty() ? null : new Donor(w, family, types);
    }

    /** Present and not one of this mod's own classes. */
    private static boolean foreign(Object script) {
        if (script == null) return false;
        String name = script instanceof String ? (String) script : script.getClass().getName();
        return !name.isEmpty() && !name.startsWith("weapondesigner.");
    }

    static Family familyOf(WeaponSpecAPI w) {
        if (w instanceof BeamWeaponSpecAPI) return Family.BEAM;
        if (!(w instanceof ProjectileWeaponSpecAPI)) return null;
        Object shot = w.getProjectileSpec();
        if (shot instanceof MissileSpecAPI) return Family.MISSILE;
        if (shot instanceof ProjectileSpecAPI) return Family.GUN;
        return null;
    }

    /** Weapons a design of this kind can borrow from: its family, with a type it can carry. */
    public static List<Donor> choices(Kind kind) {
        Family family = Family.of(kind);
        List<Donor> out = new ArrayList<>();
        for (Donor d : DONORS) {
            if (d.family != family) continue;
            for (Borrowed.Type type : d.types) {
                if (type.fits(kind)) {
                    out.add(d);
                    break;
                }
            }
        }
        return out;
    }

    public static Donor donor(String weaponId) {
        for (Donor d : DONORS) if (d.spec.getWeaponId().equals(weaponId)) return d;
        return null;
    }

    public static float opOf(WeaponSpecAPI w) {
        try {
            return Math.max(0f, w.getOrdnancePointCost(null));
        } catch (Throwable t) {
            return 0f;
        }
    }

    public static Borrowed borrow(Donor donor, Borrowed.Type type) {
        return new Borrowed(donor.spec.getWeaponId(), type, opOf(donor.spec), donor.spec.getWeaponName());
    }

    /** Brings the stored OP and name of a design's borrowed scripts up to date with the loaded donors. */
    public static void refresh(Design d) {
        for (Borrowed b : d.borrowed()) {
            Donor donor = donor(b.donorId);
            if (donor == null) continue;
            b.donorOp = opOf(donor.spec);
            b.donorName = donor.spec.getWeaponName();
        }
    }
}
