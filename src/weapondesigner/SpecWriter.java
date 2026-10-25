package weapondesigner;

import java.util.EnumSet;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipHullSpecAPI.EngineSpecAPI;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import com.fs.starfarer.api.loading.BeamWeaponSpecAPI;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.loading.MissileSpecAPI;
import com.fs.starfarer.api.loading.ProjectileSpecAPI;
import com.fs.starfarer.api.loading.ProjectileWeaponSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.loading.WeaponSpreadsheetLoader;
import com.fs.starfarer.loading.specs.BaseWeaponSpec;

import weapondesigner.combat.ScriptBook;

/**
 * Writes a design onto its reserved weapon. The game shares one weapon definition between every
 * copy, so this changes the weapon in cargo, in refit and on every ship at once.
 */
public final class SpecWriter {
    public static final String BLANK_NAME = "Blank weapon design";
    public static final String DEFAULT_DESCRIPTION = "Custom weapon design.";

    private SpecWriter() {
    }

    /** Puts a reserved weapon back to its blank state, for saves where no design uses it. */
    public static void blank(String weaponId) {
        Kind kind = Kind.ofWeaponId(weaponId);
        if (kind == null) return;
        Design d = new Design();
        d.kind = kind;
        d.resetStats();
        d.weaponId = weaponId;
        d.name = BLANK_NAME;
        apply(d);
    }

    public static void apply(Design d) {
        WeaponSpecAPI api = Global.getSettings().getWeaponSpec(d.weaponId);
        if (!(api instanceof BaseWeaponSpec)) return;
        BaseWeaponSpec spec = (BaseWeaponSpec) api;
        int op = Costing.op(d);

        spec.setWeaponName(d.name == null || d.name.trim().isEmpty() ? BLANK_NAME : d.name.trim());
        spec.setSize(d.size);
        spec.setType(d.mount);
        spec.setMountType(d.mount);
        spec.setDamageType(d.damage);
        spec.setOrdnancePointCost(op);
        spec.setBaseValue(Costing.price(d));
        spec.setRarity(0f);
        spec.setTurnRate(d.value(Stat.TURN));
        spec.setMaxRange(d.value(Stat.RANGE));
        Looks.apply(spec, d);

        switch (d.kind) {
            case SHOT:
            case LASER:
                writeShot((ProjectileWeaponSpecAPI) api, d);
                break;
            case BEAM:
                writeBeam((BeamWeaponSpecAPI) api, d);
                break;
            case MISSILE:
                writeMissile((ProjectileWeaponSpecAPI) api, d);
                break;
        }
        EnumSet<AIHints> hints = spec.getAIHints();
        if (d.level(Script.POINT_DEFENSE) > 0) hints.add(AIHints.PD);
        else hints.remove(AIHints.PD);
        ScriptBook.put(d);
        writeDescription(d);
        writeTooltipNumbers(spec, d);
    }

    /** The description object is shared like the weapon definition, so blank weapons get the default back. */
    private static void writeDescription(Design d) {
        try {
            Description description = Global.getSettings().getDescription(d.weaponId, Description.Type.WEAPON);
            if (description == null) return;
            String text = d.description == null ? "" : d.description.trim();
            description.setText1(text.isEmpty() ? DEFAULT_DESCRIPTION : text);
        } catch (Exception e) {
            Global.getLogger(SpecWriter.class).warn("Weapon Designer: description not updated for " + d.weaponId, e);
        }
    }

    private static void writeShot(ProjectileWeaponSpecAPI spec, Design d) {
        float damage = d.value(Stat.DAMAGE);
        float spread = d.value(Stat.ACCURACY);
        spec.setChargeTime(0f);
        spec.setRefireDelay(1f / d.value(Stat.RATE));
        spec.setBurstSize(1);
        spec.setBurstDelay(0f);
        spec.setEnergyPerShot(damage * d.value(Stat.FLUX));
        spec.setProjectileSpeed(d.value(Stat.SPEED));
        spec.setMinSpread(0f);
        spec.setMaxSpread(spread);
        spec.setSpreadBuildup(spread / 3f);
        spec.setSpreadDecayRate(Math.max(5f, spread * 2f));
        if (spec.getProjectileSpec() instanceof ProjectileSpecAPI) {
            ProjectileSpecAPI shot = (ProjectileSpecAPI) spec.getProjectileSpec();
            shot.getDamage().setDamage(damage);
            shot.getDamage().setType(d.damage);
            shot.getDamage().setFluxComponent(Costing.empPerShot(d));
            shot.setMoveSpeed(d.value(Stat.SPEED));
            shot.setMaxRange(d.value(Stat.RANGE));
        }
    }

    private static void writeBeam(BeamWeaponSpecAPI spec, Design d) {
        float dps = d.value(Stat.DAMAGE);
        spec.setDamagePerSecond(dps);
        spec.setEnergyPerSecond(dps * d.value(Stat.FLUX));
        spec.setEMPPerSecond(Costing.empPerSecond(d));
        spec.setChargeupTime(0.1f);
        spec.setChargedownTime(0.1f);
    }

    private static void writeMissile(ProjectileWeaponSpecAPI spec, Design d) {
        float damage = d.value(Stat.DAMAGE);
        float speed = d.value(Stat.SPEED);
        float guidance = d.value(Stat.GUIDANCE);
        int ammo = Math.round(d.value(Stat.AMMO));
        spec.setChargeTime(0f);
        spec.setRefireDelay(d.value(Stat.RELOAD));
        spec.setBurstSize(Math.max(1, Math.min(ammo, Math.round(d.value(Stat.SALVO)))));
        spec.setBurstDelay(0.12f);
        spec.setEnergyPerShot(0f);
        spec.setMaxAmmo(ammo);
        spec.setAmmoPerSecond(d.value(Stat.REGEN) / 60f);
        spec.setProjectileSpeed(speed);
        spec.setMinSpread(0f);
        spec.setMaxSpread(guidance > 0f ? 0f : 3f);
        EnumSet<AIHints> hints = spec.getAIHints();
        if (guidance > 0f) hints.add(AIHints.DO_NOT_AIM);
        else hints.remove(AIHints.DO_NOT_AIM);
        if (spec.getProjectileSpec() instanceof MissileSpecAPI) {
            MissileSpecAPI missile = (MissileSpecAPI) spec.getProjectileSpec();
            missile.getDamage().setDamage(damage);
            missile.getDamage().setType(d.damage);
            missile.getDamage().setFluxComponent(Costing.empPerShot(d));
            missile.setMaxFlightTime(Costing.flightTime(d));
            missile.setLaunchSpeed(Math.min(speed, 150f));
            missile.setExplosionRadius(Math.max(50f, Math.min(300f, (float) Math.sqrt(damage) * 5f)));
            if (missile.getHullSpec() != null && missile.getHullSpec().getEngineSpec() != null) {
                EngineSpecAPI engine = missile.getHullSpec().getEngineSpec();
                engine.setMaxSpeed(speed);
                engine.setAcceleration(speed * 3f);
                engine.setDeceleration(speed * 1.5f);
                engine.setMaxTurnRate(guidance);
                engine.setTurnAcceleration(guidance * 3f);
            }
        }
    }

    /**
     * The game works out the tooltip numbers (DPS, flux per second and so on) once at startup and
     * keeps them. Recompute them the same way, or tooltips would show the blank weapon's numbers.
     * The holder class is internal and may move in another game version, so failing here only
     * costs the tooltip numbers.
     */
    private static void writeTooltipNumbers(BaseWeaponSpec spec, Design d) {
        try {
            WeaponSpreadsheetLoader.o stats = (WeaponSpreadsheetLoader.o) spec.getDerivedStats();
            if (stats == null) return;
            float damage = d.value(Stat.DAMAGE);
            float dps = Costing.damagePerSecond(d);
            stats.baseValue = Costing.price(d);
            switch (d.kind) {
                case BEAM:
                    stats.dps = dps;
                    stats.sustainedDps = dps;
                    stats.emp = Costing.empPerSecond(d);
                    stats.burstDamage = -1f;
                    stats.burstFireDuration = 0.1f;
                    stats.fluxPerDam = d.value(Stat.FLUX);
                    stats.rof = -1f;
                    stats.damagePerShot = dps;
                    stats.empPerShot = Costing.empPerSecond(d);
                    stats.damageOver30Sec = (dps + stats.emp) * 30f;
                    break;
                case MISSILE: {
                    float ammo = d.value(Stat.AMMO);
                    float salvo = Math.min(d.value(Stat.SALVO), ammo);
                    float cycle = d.value(Stat.RELOAD) + 0.12f * (salvo - 1f);
                    float regen = d.value(Stat.REGEN) / 60f;
                    stats.damagePerShot = damage;
                    stats.empPerShot = Costing.empPerShot(d);
                    stats.dps = damage * salvo / cycle;
                    stats.emp = stats.empPerShot * salvo / cycle;
                    stats.burstDamage = salvo > 1f ? damage * salvo : -1f;
                    stats.burstFireDuration = 0.12f * (salvo - 1f);
                    stats.sustainedDps = regen > 0f ? Math.min(stats.dps, damage * regen) : stats.dps;
                    stats.fluxPerDam = 0f;
                    stats.rof = salvo / cycle;
                    stats.damageOver30Sec = (stats.dps + stats.emp) * Math.min(30f, ammo / stats.rof);
                    break;
                }
                default:
                    stats.damagePerShot = damage;
                    stats.empPerShot = Costing.empPerShot(d);
                    stats.dps = dps;
                    stats.emp = Costing.empPerSecond(d);
                    stats.burstDamage = -1f;
                    stats.burstFireDuration = 0f;
                    stats.sustainedDps = dps;
                    stats.fluxPerDam = d.value(Stat.FLUX);
                    stats.rof = d.value(Stat.RATE);
                    stats.damageOver30Sec = (dps + stats.emp) * 30f;
                    break;
            }
        } catch (Throwable t) {
            Global.getLogger(SpecWriter.class).warn("Weapon Designer: tooltip numbers not updated for " + d.weaponId, t);
        }
    }
}
