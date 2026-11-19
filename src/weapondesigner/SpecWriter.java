package weapondesigner;

import java.util.EnumSet;

import org.json.JSONObject;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipHullSpecAPI.EngineSpecAPI;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import com.fs.starfarer.api.loading.BeamWeaponSpecAPI;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.loading.MissileSpecAPI;
import com.fs.starfarer.api.loading.ShotBehaviorSpecAPI;
import com.fs.starfarer.api.loading.ProjectileSpecAPI;
import com.fs.starfarer.api.loading.ProjectileWeaponSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.loading.WeaponSpreadsheetLoader;
import com.fs.starfarer.loading.specs.BaseWeaponSpec;

import weapondesigner.combat.RapidFire;
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

    /** Writes a design onto its weapon. */
    public static void apply(Design d) {
        WeaponSpecAPI api = Global.getSettings().getWeaponSpec(d.weaponId);
        if (!(api instanceof BaseWeaponSpec)) return;
        BaseWeaponSpec spec = (BaseWeaponSpec) api;
        int op = Costing.op(d);

        spec.setWeaponName(d.name == null || d.name.trim().isEmpty() ? BLANK_NAME : Text.fontSafe(d.name.trim()));
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
        // The blank files carry no hints the design does not set, so the set is rebuilt whole.
        EnumSet<AIHints> hints = spec.getAIHints();
        hints.clear();
        hints.addAll(Hint.effective(d));
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
            description.setText1(text.isEmpty() ? DEFAULT_DESCRIPTION : Text.fontSafe(text));
        } catch (Exception e) {
            Global.getLogger(SpecWriter.class).warn("Weapon Designer: description not updated for " + d.weaponId, e);
        }
    }

    private static void writeShot(ProjectileWeaponSpecAPI spec, Design d) {
        float damage = d.value(Stat.DAMAGE);
        float spread = d.value(Stat.ACCURACY);
        float rate = d.value(Stat.RATE);
        int burst = Costing.burstSize(d);
        float gap = Costing.burstDelay(d);
        spec.setChargeTime(0f);
        // A burst and its pause take as long as its shots at the designed average rate. Faster
        // designs fire at the game's reliable rate; RapidFire adds the rest of the shots.
        spec.setRefireDelay(Math.max(burst / rate - gap * (burst - 1), 1f / RapidFire.NATIVE_RATE));
        spec.setBurstSize(burst);
        spec.setBurstDelay(gap);
        if (d.unlimitedAmmo()) {
            spec.setMaxAmmo(Integer.MAX_VALUE); // the game's "unlimited"
            spec.setAmmoPerSecond(0f);
        } else {
            spec.setMaxAmmo((int) Math.max(1, Math.min(1_000_000, d.value(Stat.AMMO))));
            spec.setAmmoPerSecond(d.value(Stat.REGEN));
        }
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
        spec.setRefireDelay(Math.max(d.value(Stat.RELOAD), 1f / RapidFire.NATIVE_RATE));
        spec.setBurstSize(Math.max(1, Math.min(ammo, Math.round(d.value(Stat.SALVO)))));
        spec.setBurstDelay(ScriptBook.MISSILE_BURST_DELAY);
        spec.setEnergyPerShot(0f);
        spec.setMaxAmmo(ammo);
        spec.setAmmoPerSecond(d.value(Stat.REGEN) / 60f);
        spec.setProjectileSpeed(speed);
        spec.setMinSpread(0f);
        spec.setMaxSpread(guidance > 0f ? 0f : 3f);
        if (spec.getProjectileSpec() instanceof MissileSpecAPI) {
            MissileSpecAPI missile = (MissileSpecAPI) spec.getProjectileSpec();
            missile.getDamage().setDamage(damage);
            missile.getDamage().setType(d.damage);
            missile.getDamage().setFluxComponent(Costing.empPerShot(d));
            missile.setMaxFlightTime(Costing.flightTime(d));
            missile.setLaunchSpeed(Math.min(speed, 150f));
            missile.setExplosionRadius(Math.max(50f, Math.min(300f, (float) Math.sqrt(damage) * 5f)));
            writeCopiedDamage(missile, d);
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
     * A missile copied from a picked look may deal its damage through a blast or through warheads
     * it splits into. Both are this design's own copies ({@link MissileCopy}), so they carry the
     * design's numbers: a blast up to the missile's damage, and the missile's damage shared out
     * between its warheads.
     */
    private static void writeCopiedDamage(MissileSpecAPI missile, Design d) {
        float damage = d.value(Stat.DAMAGE);
        float emp = Costing.empPerShot(d);
        DamagingExplosionSpec blast = missile.getExplosionSpec();
        if (blast != null && blast.getMaxDamage() > 0f) {
            float minShare = Math.max(0f, Math.min(1f, blast.getMinDamage() / blast.getMaxDamage()));
            blast.setMaxDamage(damage);
            blast.setMinDamage(damage * minShare);
            blast.setDamageType(d.damage);
            float empShare = blast.getMaxEMPDamage() > 0f ? blast.getMinEMPDamage() / blast.getMaxEMPDamage() : 0.5f;
            blast.setMaxEMPDamage(emp);
            blast.setMinEMPDamage(emp * Math.max(0f, Math.min(1f, empShare)));
        }
        try {
            if (!MissileCopy.ownsBehavior(missile.getBehaviorSpec())) return;
            ShotBehaviorSpecAPI behavior = missile.getBehaviorSpec();
            JSONObject params = behavior.getParams();
            if (params == null || !"MIRV".equals(behavior.getBehavorString())) return;
            int warheads = Math.max(1, params.optInt("numShots", 1));
            params.put("damage", (double) (damage / warheads));
            params.put("emp", (double) (emp / warheads));
            params.put("damageType", d.damage.name());
        } catch (Throwable t) {
            Global.getLogger(SpecWriter.class).warn("Weapon Designer: warhead damage not updated for " + d.weaponId, t);
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
                default: {
                    int burst = Costing.burstSize(d);
                    float rate = d.value(Stat.RATE);
                    stats.damagePerShot = damage;
                    stats.empPerShot = Costing.empPerShot(d);
                    stats.dps = dps;
                    stats.emp = Costing.empPerSecond(d);
                    stats.burstDamage = burst > 1 ? damage * burst : -1f;
                    stats.burstFireDuration = Costing.burstDelay(d) * (burst - 1);
                    stats.sustainedDps = Costing.sustainedDamagePerSecond(d);
                    stats.fluxPerDam = d.value(Stat.FLUX);
                    stats.rof = rate;
                    float shots = rate * 30f;
                    if (!d.unlimitedAmmo()) shots = Math.min(shots, d.value(Stat.AMMO) + d.value(Stat.REGEN) * 30f);
                    stats.damageOver30Sec = (damage + stats.empPerShot) * shots;
                    break;
                }
            }
        } catch (Throwable t) {
            Global.getLogger(SpecWriter.class).warn("Weapon Designer: tooltip numbers not updated for " + d.weaponId, t);
        }
    }
}
