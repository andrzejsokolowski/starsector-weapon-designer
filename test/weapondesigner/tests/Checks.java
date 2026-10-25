package weapondesigner.tests;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;

import weapondesigner.Costing;
import weapondesigner.Design;
import weapondesigner.DesignStore;
import weapondesigner.Kind;
import weapondesigner.Script;
import weapondesigner.Settings;
import weapondesigner.Stat;
import weapondesigner.Text;
import weapondesigner.Workshop;

/** Standalone checks for the model, the pricing and the reserved weapon files. No game needed. */
public final class Checks {
    private static int failures;

    public static void main(String[] args) throws Exception {
        defaultsMatchTheBaseGame();
        steppingUpNeverGetsCheaper();
        everyStepCostsAtLeastOneDesignPoint();
        laddersKeepGoing();
        scriptLevels();
        noOpCap();
        opConversion();
        jsonRoundTrip();
        priceGrowsWithWorth();
        weaponIds();
        nanoforges();
        freeWeaponIds();
        oldDesignsMoveIntoTheReserve();
        reserveFiles();
        lunaSettings();
        numberText();
        vanillaComparisons();
        if (failures > 0) {
            System.err.println(failures + " check(s) failed");
            System.exit(1);
        }
        System.out.println("All checks passed");
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            if (failures <= 60) System.err.println("FAIL: " + what);
        }
    }

    private static Design design(Kind kind, WeaponSize size) {
        Design d = new Design();
        d.kind = kind;
        d.size = size;
        d.resetStats();
        return d;
    }

    private static void defaultsMatchTheBaseGame() {
        check(Costing.op(design(Kind.SHOT, WeaponSize.SMALL)) == 4, "default ballistic costs 4 OP like a Light Autocannon");
        check(Costing.op(design(Kind.BEAM, WeaponSize.SMALL)) == 4, "default beam costs 4 OP like a Tactical Laser");
        int laser = Costing.op(design(Kind.LASER, WeaponSize.SMALL));
        check(laser >= 4 && laser <= 6, "default laser costs about an IR Pulse Laser, got " + laser);
        int missile = Costing.op(design(Kind.MISSILE, WeaponSize.SMALL));
        check(missile >= 3 && missile <= 5, "default missile costs about a Harpoon, got " + missile);
        int dp = Costing.designPoints(design(Kind.SHOT, WeaponSize.SMALL));
        check(dp >= 38 && dp <= 42, "default ballistic is worth about 40 design points, got " + dp);
    }

    private static void steppingUpNeverGetsCheaper() {
        for (Kind kind : Kind.values()) {
            for (WeaponSize size : WeaponSize.values()) {
                for (DamageType damage : DamageType.values()) {
                    if (damage == DamageType.OTHER) continue;
                    Design d = design(kind, size);
                    d.damage = damage;
                    for (Stat stat : kind.stats()) {
                        Design walk = d.copy();
                        walk.set(stat, 0);
                        float last = Costing.raw(walk);
                        int stop = stat.table(kind, size).length + 30;
                        while (walk.rung(stat) < stop && walk.canStep(stat, 1)) {
                            walk.step(stat, 1);
                            float now = Costing.raw(walk);
                            check(now + 1e-4f >= last, kind + " " + size + " " + stat + " got cheaper at rung "
                                    + walk.rung(stat));
                            last = now;
                        }
                    }
                }
            }
        }
    }

    /** No step, up or down, may show a change of 0 design points, past the table too. */
    private static void everyStepCostsAtLeastOneDesignPoint() {
        for (Kind kind : Kind.values()) {
            for (WeaponSize size : WeaponSize.values()) {
                for (DamageType damage : new DamageType[]{DamageType.KINETIC, DamageType.FRAGMENTATION}) {
                    Design d = design(kind, size);
                    d.damage = damage;
                    for (Stat stat : kind.stats()) {
                        Design walk = d.copy();
                        int stop = stat.table(kind, size).length + 40;
                        while (walk.rung(stat) < stop && walk.canStep(stat, 1)) {
                            long before = Costing.designPointsExact(walk);
                            walk.step(stat, 1);
                            long after = Costing.designPointsExact(walk);
                            check(after - before >= 1, kind + " " + size + " " + stat + " step up to rung "
                                    + walk.rung(stat) + " costs " + (after - before));
                        }
                        walk = d.copy();
                        while (walk.canStep(stat, -1)) {
                            long before = Costing.designPointsExact(walk);
                            walk.step(stat, -1);
                            long after = Costing.designPointsExact(walk);
                            check(after - before <= -1, kind + " " + size + " " + stat + " step down gives back "
                                    + (before - after));
                            check(after >= 1, kind + " " + size + " " + stat + " went under 1 design point");
                        }
                    }
                }
            }
        }
    }

    /** Ladders keep going past their tables, always in the better direction, with no repeats. */
    private static void laddersKeepGoing() {
        for (Kind kind : Kind.values()) {
            for (WeaponSize size : WeaponSize.values()) {
                Design d = design(kind, size);
                for (Stat stat : kind.stats()) {
                    float[] table = stat.table(kind, size);
                    check(table.length > 1, stat + " has a ladder for " + kind + " " + size);
                    int end = stat == Stat.ACCURACY ? table.length - 1 : table.length + 80;
                    check(stat.top(kind, size) >= end, stat + " " + kind + " " + size + " stops too early");
                    for (int i = 1; i <= end; i++) {
                        float prev = stat.value(kind, size, i - 1);
                        float now = stat.value(kind, size, i);
                        check(Float.isFinite(now), stat + " " + kind + " " + size + " rung " + i + " is not a number");
                        boolean better = stat.lowerIsBetter() ? now < prev : now > prev;
                        check(better, stat + " " + kind + " " + size + " rung " + i + " (" + now
                                + ") is not better than " + prev);
                    }
                    check(d.rung(stat) < table.length, stat + " default rung inside the table");
                }
            }
        }
        Design perfect = design(Kind.SHOT, WeaponSize.SMALL);
        perfect.set(Stat.ACCURACY, 99);
        check(perfect.value(Stat.ACCURACY) == 0f && !perfect.canStep(Stat.ACCURACY, 1), "perfect accuracy is the top");
    }

    private static void scriptLevels() {
        for (Kind kind : Kind.values()) {
            Design d = design(kind, WeaponSize.MEDIUM);
            for (Script script : Script.values()) {
                Design walk = d.copy();
                check(!walk.canStep(script, -1), script + " cannot go under off");
                for (int i = 0; i < 25 && walk.canStep(script, 1); i++) {
                    long before = Costing.designPointsExact(walk);
                    walk.step(script, 1);
                    long after = Costing.designPointsExact(walk);
                    check(after - before >= 1, kind + " " + script + " level " + walk.level(script) + " costs "
                            + (after - before));
                }
                if (script == Script.POINT_DEFENSE) {
                    check(walk.level(script) == 1, "point defense is on or off");
                } else {
                    check(walk.level(script) == 25, script + " keeps going past level 24");
                }
                check(!script.valueText(walk.level(script)).isEmpty(), script + " shows a value");
            }
        }
        Design d = design(Kind.SHOT, WeaponSize.SMALL);
        check(d.activeScripts() == 0, "a new design has no scripts");
        d.setLevel(Script.ARC, 3);
        d.setLevel(Script.LEECH, 1);
        check(d.activeScripts() == 2, "active scripts are counted");
        d.resetStats();
        check(d.level(Script.ARC) == 3, "resetting stats keeps the scripts");
        check(Script.slowMult(1) < 1f && Script.slowMult(30) > 0f, "engine jammer never stops a ship dead");
    }

    /** Andrzej's example: 4000 range and 1000 damage per second is allowed, whatever the OP. */
    private static void noOpCap() {
        Design d = design(Kind.BEAM, WeaponSize.LARGE);
        while (d.value(Stat.RANGE) < 4000f && stepOrFail(d, Stat.RANGE)) { }
        while (d.value(Stat.DAMAGE) < 1000f && stepOrFail(d, Stat.DAMAGE)) { }
        check(d.value(Stat.RANGE) >= 4000f && d.value(Stat.DAMAGE) >= 1000f, "beam reaches 4000 range and 1000 DPS");
        check(Costing.op(d) > 40, "a 4000 range, 1000 DPS beam costs more than the old 40 OP cap, got " + Costing.op(d));
        check(d.canStep(Stat.RANGE, 1) && d.canStep(Stat.DAMAGE, 1), "and it can keep going");
        check(!Costing.overLimit(d), "no OP limit by default");
        System.out.printf("  %-24s %4d OP  %6d DP  %8d credits%n", "4000 range 1000 DPS beam", Costing.op(d),
                Costing.designPoints(d), Costing.price(d));
    }

    private static boolean stepOrFail(Design d, Stat stat) {
        if (!d.canStep(stat, 1)) return false;
        d.step(stat, 1);
        return true;
    }

    private static void opConversion() {
        check(Settings.opRate() == Settings.DEFAULT_OP_RATE && Settings.DEFAULT_OP_RATE == 10, "default conversion is 10%");
        check(Costing.op(100, 10) == 10, "100 design points at 10% are 10 OP");
        check(Costing.op(100, 1) == 1, "100 design points at 1% are 1 OP");
        check(Costing.op(3, 10) == 1, "OP never drops under 1");
        check(Costing.op(Costing.MAX_DESIGN_POINTS, 100) == (int) Costing.MAX_DESIGN_POINTS, "the ceiling fits in an int");
    }

    private static void jsonRoundTrip() throws Exception {
        Design d = design(Kind.MISSILE, WeaponSize.MEDIUM);
        d.weaponId = "wd_missile_007";
        d.name = "Test \"quoted\", name";
        d.mount = WeaponType.ENERGY;
        d.damage = DamageType.FRAGMENTATION;
        d.step(Stat.AMMO, 3);
        d.step(Stat.GUIDANCE, -2);
        d.look = "harpoonpod";
        d.description = "Line one, \"quoted\".\nLine two.";
        d.setLevel(Script.BLAST, 4);
        d.setLevel(Script.POINT_DEFENSE, 1);
        Design back = Design.fromJson(d.toJson());
        check(d.toJson().toString().equals(back.toJson().toString()), "design survives a JSON round trip");
        check(back.kind == Kind.MISSILE, "kind comes back from the weapon id");
        check("harpoonpod".equals(back.look), "the picked look comes back");
        check(back.level(Script.BLAST) == 4 && back.level(Script.POINT_DEFENSE) == 1, "scripts come back");
        check(d.description.equals(back.description), "the description comes back");
        d.look = null;
        check(Design.fromJson(d.toJson()).look == null, "an automatic look stays automatic");

        org.json.JSONObject json = new org.json.JSONObject();
        json.put("weaponId", org.json.JSONObject.NULL);
        json.put("name", org.json.JSONObject.NULL);
        json.put("size", "HUGE");
        Design odd = Design.fromJson(json);
        check(odd.weaponId == null, "a JSON null weapon id stays null, not the text null");
        check("".equals(odd.name), "a JSON null name becomes empty");
        check(odd.size == WeaponSize.SMALL, "an unknown size falls back to small");
        check("".equals(odd.description) && odd.activeScripts() == 0,
                "designs saved before 0.3.0 have no description or scripts");
    }

    private static void priceGrowsWithWorth() {
        int last = 0;
        for (int rung = 0; rung < 60; rung++) {
            Design d = design(Kind.SHOT, WeaponSize.LARGE);
            d.set(Stat.DAMAGE, rung);
            int price = Costing.price(d);
            check(price >= last, "price never drops as damage rises, rung " + rung);
            last = price;
        }
    }

    private static void weaponIds() {
        check("wd_beam_042".equals(Kind.BEAM.weaponId(42)), "weapon id format");
        check(Kind.ofWeaponId("wd_laser_100") == Kind.LASER, "laser id parses");
        check(Kind.ofWeaponId("wd_missile_001") == Kind.MISSILE, "missile id parses");
        check(Kind.ofWeaponId("lightac") == null, "other weapons are not ours");
        check(Kind.ofWeaponId(null) == null, "null id is not ours");
        check(Kind.indexOf("wd_missile_010") == 10 && Kind.indexOf("lightac") == -1, "reserve index parses");
    }

    private static void nanoforges() {
        check(Workshop.unlocks("pristine_nanoforge"), "the Pristine Nanoforge unlocks the designer");
        check(Workshop.unlocks("aotd_shrouded_nanoforge"), "the Tenebrian Nanoforge unlocks the designer");
        check(!Workshop.unlocks("corrupted_nanoforge"), "the Corrupted Nanoforge does not");
        check(!Workshop.unlocks(null), "no item does not");
    }

    private static void freeWeaponIds() {
        List<Design> designs = new ArrayList<>();
        check("wd_shot_001".equals(DesignStore.freeWeaponId(Kind.SHOT, designs)), "first free ballistic is 001");
        Design taken = design(Kind.SHOT, WeaponSize.SMALL);
        taken.weaponId = "wd_shot_001";
        designs.add(taken);
        check("wd_shot_002".equals(DesignStore.freeWeaponId(Kind.SHOT, designs)), "taken ids are skipped");
        check("wd_beam_001".equals(DesignStore.freeWeaponId(Kind.BEAM, designs)), "kinds have separate reserves");
        for (int i = 2; i <= Kind.RESERVE; i++) {
            Design d = design(Kind.SHOT, WeaponSize.SMALL);
            d.weaponId = Kind.SHOT.weaponId(i);
            designs.add(d);
        }
        check(DesignStore.freeWeaponId(Kind.SHOT, designs) == null, "a full reserve has no free id");
    }

    private static void oldDesignsMoveIntoTheReserve() {
        List<Design> designs = new ArrayList<>();
        Design inside = design(Kind.SHOT, WeaponSize.SMALL);
        inside.weaponId = "wd_shot_001";
        Design outside = design(Kind.SHOT, WeaponSize.SMALL);
        outside.weaponId = "wd_shot_042";
        designs.add(inside);
        designs.add(outside);
        check(DesignStore.fitReserve(designs), "a design past the reserve is noticed");
        check("wd_shot_002".equals(outside.weaponId), "and moved to the first free blank weapon, got " + outside.weaponId);
        check(!DesignStore.fitReserve(designs), "designs inside the reserve stay put");
    }

    private static void reserveFiles() throws IOException {
        Path root = Paths.get("");
        List<String> rows = Files.readAllLines(root.resolve("data/weapons/weapon_data.csv"), StandardCharsets.UTF_8);
        Set<String> ids = new HashSet<>();
        for (String row : rows.subList(1, rows.size())) {
            if (row.isEmpty()) continue;
            ids.add(row.split(",")[1]);
        }
        Set<String> descriptions = new HashSet<>();
        for (String row : Files.readAllLines(root.resolve("data/strings/descriptions.csv"), StandardCharsets.UTF_8)) {
            descriptions.add(row.split(",")[0]);
        }
        check(Kind.RESERVE == 10, "10 blank weapons per projectile type");
        check(ids.size() == Kind.values().length * Kind.RESERVE, "weapon_data.csv holds the whole reserve, got " + ids.size());
        for (Kind kind : Kind.values()) {
            for (int i = 1; i <= Kind.RESERVE; i++) {
                String id = kind.weaponId(i);
                check(ids.contains(id), id + " has a weapon_data.csv row");
                check(descriptions.contains(id), id + " has a description");
                Path wpn = root.resolve("data/weapons/" + id + ".wpn");
                check(Files.isRegularFile(wpn), id + ".wpn exists");
                if (kind != Kind.BEAM) {
                    Path proj = root.resolve("data/weapons/proj/" + id + ".proj");
                    check(Files.isRegularFile(proj) && read(proj).contains("weapondesigner.combat.ScriptHitEffect"),
                            id + ".proj exists and runs the hit scripts");
                } else {
                    check(Files.isRegularFile(wpn) && read(wpn).contains("weapondesigner.combat.ScriptBeamEffect"),
                            id + ".wpn runs the beam scripts");
                }
            }
        }
        check(!Files.exists(root.resolve("data/weapons/wd_shot_011.wpn")), "no blank weapons past the reserve");
        for (String name : new String[]{"mod_info.json", "weapon_designer.version", "data/weapons/weapon_data.csv",
                "data/strings/descriptions.csv", "data/campaign/rules.csv", "data/config/version/version_files.csv",
                "data/config/LunaSettings.csv", "data/config/LunaSettingsConfig.json"}) {
            byte[] b = Files.readAllBytes(root.resolve(name));
            check(!(b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF),
                    name + " has no UTF-8 BOM");
        }
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static void lunaSettings() throws IOException {
        String csv = read(Paths.get("data/config/LunaSettings.csv"));
        for (String key : new String[]{Settings.OP_RATE, Settings.PRICE_PERCENT, Settings.OP_LIMIT}) {
            check(csv.contains("\n" + key + ","), "LunaSettings.csv defines " + key);
        }
        check(csv.contains("\n" + Settings.OP_RATE + ",Design points to OP conversion rate,Int,10,"),
                "the conversion rate setting defaults to 10");
    }

    private static void numberText() {
        check("1,250".equals(Text.number(1250f)), "thousands are grouped");
        check("0.094".equals(Text.number(0.094f)), "small numbers keep two digits, got " + Text.number(0.094f));
        check("2.5".equals(Text.number(2.5f)), "halves show one decimal");
        check("-12".equals(Text.grouped(-12)), "negative numbers keep their sign");
    }

    /** Prints what designs copying a few base game weapons cost, for tuning. */
    private static void vanillaComparisons() {
        print("Light Autocannon (4)", shot(WeaponSize.SMALL, DamageType.KINETIC, 50, 2, 700, 0.8f, 800, 8, 20));
        print("Light Assault Gun (5)", shot(WeaponSize.SMALL, DamageType.HIGH_EXPLOSIVE, 40, 4, 700, 1f, 800, 5, 40));
        print("Heavy Autocannon (10)", shot(WeaponSize.MEDIUM, DamageType.KINETIC, 50, 4, 800, 0.9f, 900, 10, 20));
        print("Heavy Mauler (12)", shot(WeaponSize.MEDIUM, DamageType.HIGH_EXPLOSIVE, 200, 0.6f, 1000, 1f, 900, 5, 20));
        print("Mark IX (18)", shot(WeaponSize.LARGE, DamageType.KINETIC, 100, 3.5f, 900, 1f, 1000, 10, 15));
        print("Hellbore (16)", shot(WeaponSize.LARGE, DamageType.HIGH_EXPLOSIVE, 750, 0.33f, 900, 1f, 500, 10, 5));
    }

    private static Design shot(WeaponSize size, DamageType type, float damage, float rate, float range, float flux,
                               float speed, float spread, float turn) {
        Design d = design(Kind.SHOT, size);
        d.damage = type;
        nearest(d, Stat.DAMAGE, damage);
        nearest(d, Stat.RATE, rate);
        nearest(d, Stat.RANGE, range);
        nearest(d, Stat.FLUX, flux);
        nearest(d, Stat.SPEED, speed);
        nearest(d, Stat.ACCURACY, spread);
        nearest(d, Stat.TURN, turn);
        return d;
    }

    private static void nearest(Design d, Stat stat, float value) {
        float[] ladder = stat.table(d.kind, d.size);
        int best = 0;
        for (int i = 1; i < ladder.length; i++) {
            if (Math.abs(ladder[i] - value) < Math.abs(ladder[best] - value)) best = i;
        }
        d.set(stat, best);
    }

    private static void print(String name, Design d) {
        System.out.printf("  %-24s %2d OP  %4d DP  %6d credits  (raw %.1f)%n", name, Costing.op(d), Costing.designPoints(d),
                Costing.price(d), Costing.raw(d));
    }
}
