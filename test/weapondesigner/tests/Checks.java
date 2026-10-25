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
import weapondesigner.Stat;

/** Standalone checks for the model, the pricing and the reserved weapon files. No game needed. */
public final class Checks {
    private static int failures;

    public static void main(String[] args) throws Exception {
        defaultsMatchTheBaseGame();
        steppingUpNeverGetsCheaper();
        everyRungIsReachable();
        jsonRoundTrip();
        priceGrowsWithOp();
        weaponIds();
        freeWeaponIds();
        reserveFiles();
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
            System.err.println("FAIL: " + what);
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
        for (Kind kind : Kind.values()) {
            for (WeaponSize size : WeaponSize.values()) {
                check(Costing.withinCap(design(kind, size)), "default " + kind + " " + size + " fits its cap");
            }
        }
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
                        while (walk.canStep(stat, 1)) {
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

    private static void everyRungIsReachable() {
        for (Kind kind : Kind.values()) {
            for (WeaponSize size : WeaponSize.values()) {
                Design d = design(kind, size);
                for (Stat stat : kind.stats()) {
                    float[] ladder = stat.ladder(kind, size);
                    check(ladder.length > 1, stat + " has a ladder for " + kind + " " + size);
                    check(d.rung(stat) < ladder.length, stat + " default rung inside the ladder");
                    for (int i = 1; i < ladder.length; i++) {
                        check(ladder[i] != ladder[i - 1], stat + " ladder repeats a value for " + kind);
                    }
                }
            }
        }
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
        Design back = Design.fromJson(d.toJson());
        check(d.toJson().toString().equals(back.toJson().toString()), "design survives a JSON round trip");
        check(back.kind == Kind.MISSILE, "kind comes back from the weapon id");
        check("harpoonpod".equals(back.look), "the picked look comes back");
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
    }

    private static void priceGrowsWithOp() {
        int last = 0;
        for (int rung = 0; rung < 18; rung++) {
            Design d = design(Kind.SHOT, WeaponSize.LARGE);
            d.set(Stat.DAMAGE, rung);
            int price = Costing.price(d);
            check(price >= last, "price never drops as OP rises");
            last = price;
        }
    }

    private static void weaponIds() {
        check("wd_beam_042".equals(Kind.BEAM.weaponId(42)), "weapon id format");
        check(Kind.ofWeaponId("wd_laser_100") == Kind.LASER, "laser id parses");
        check(Kind.ofWeaponId("wd_missile_001") == Kind.MISSILE, "missile id parses");
        check(Kind.ofWeaponId("lightac") == null, "other weapons are not ours");
        check(Kind.ofWeaponId(null) == null, "null id is not ours");
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
        check(ids.size() == Kind.values().length * Kind.RESERVE, "weapon_data.csv holds the whole reserve, got " + ids.size());
        for (Kind kind : Kind.values()) {
            for (int i = 1; i <= Kind.RESERVE; i++) {
                String id = kind.weaponId(i);
                check(ids.contains(id), id + " has a weapon_data.csv row");
                check(descriptions.contains(id), id + " has a description");
                check(Files.isRegularFile(root.resolve("data/weapons/" + id + ".wpn")), id + ".wpn exists");
                if (kind != Kind.BEAM) {
                    check(Files.isRegularFile(root.resolve("data/weapons/proj/" + id + ".proj")), id + ".proj exists");
                }
            }
        }
        for (String name : new String[]{"mod_info.json", "weapon_designer.version", "data/weapons/weapon_data.csv",
                "data/strings/descriptions.csv", "data/campaign/rules.csv", "data/config/version/version_files.csv"}) {
            byte[] b = Files.readAllBytes(root.resolve(name));
            check(!(b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF),
                    name + " has no UTF-8 BOM");
        }
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
        float[] ladder = stat.ladder(d.kind, d.size);
        int best = 0;
        for (int i = 1; i < ladder.length; i++) {
            if (Math.abs(ladder[i] - value) < Math.abs(ladder[best] - value)) best = i;
        }
        d.set(stat, best);
    }

    private static void print(String name, Design d) {
        System.out.printf("  %-24s %2d OP  %6d credits  (raw %.1f)%n", name, Costing.op(d), Costing.price(d), Costing.raw(d));
    }
}
