package weapondesigner;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import com.fs.starfarer.api.combat.WeaponAPI.AIHints;

/**
 * The AI hints a design can carry (the "hints" column of weapon_data.csv). They only tell ship AI
 * how to use the weapon, so they cost nothing. Point defense itself stays a paid script, and
 * turning missiles still get "fire off target" by themselves.
 *
 * Descriptions follow the game's API notes and the Starsector wiki's weapon_data.csv page.
 */
public enum Hint {
    PD_ONLY(AIHints.PD_ONLY, Scope.ALL, "Point defense only",
            "Fires only at missiles, and at fighters too when Anti-fighter is on."),
    PD_ALSO(AIHints.PD_ALSO, Scope.ALL, "Ships first",
            "With the Point defense script: fires at missiles only when no other target is in range."),
    ANTI_FTR(AIHints.ANTI_FTR, Scope.ALL, "Anti-fighter",
            "Also fires at fighters. With Point defense only it hunts fighters as well as missiles; missiles with it are fired at fighters too."),
    STRIKE(AIHints.STRIKE, Scope.ALL, "Strike",
            "Used only against destroyers and bigger ships, like the base game's torpedoes."),
    USE_VS_FRIGATES(AIHints.USE_VS_FRIGATES, Scope.ALL, "Frigates too",
            "With Strike: may also be fired at frigates."),
    DANGEROUS(AIHints.DANGEROUS, Scope.ALL, "Dangerous",
            "Enemy ships treat it like a strike weapon and keep away from it. Does not change how your own ships use it."),
    FIRE_WHEN_INEFFICIENT(AIHints.FIRE_WHEN_INEFFICIENT, Scope.ALL, "Fire when wasteful",
            "Fired even when the flux it costs is poor value against the target. Meant for weapons with side effects, like the Ion Beam."),
    USE_LESS_VS_SHIELDS(AIHints.USE_LESS_VS_SHIELDS, Scope.ALL, "Hold vs shields",
            "Fired less at raised shields."),
    IMPORTANT(AIHints.IMPORTANT, Scope.ALL, "Aim first",
            "A ship's AI prefers to aim this weapon's group itself instead of leaving it on autofire."),
    NO_MANUAL_FIRE(AIHints.NO_MANUAL_FIRE, Scope.ALL, "Autofire only",
            "A ship's AI leaves this weapon's group on autofire and aims other groups itself, unless there is no other choice."),
    DO_NOT_AIM(AIHints.DO_NOT_AIM, Scope.MISSILE, "Fire off target",
            "Fired even when the target is outside the weapon's arc. Missiles that turn get this by themselves."),
    GUIDED_POOR(AIHints.GUIDED_POOR, Scope.MISSILE, "Fire 10 degrees off",
            "Fired at targets up to 10 degrees outside the weapon's arc."),
    HEATSEEKER(AIHints.HEATSEEKER, Scope.MISSILE, "Engine seeker",
            "Its missiles go for the target's engines, like the Salamander's."),
    DIRECT_AIM(AIHints.DIRECT_AIM, Scope.MISSILE, "No weaving",
            "Its missiles fly straight at the target instead of weaving on the way."),
    MISSILE_SPREAD(AIHints.MISSILE_SPREAD, Scope.MISSILE, "Spread hits",
            "Its missiles aim at random points around the target's center, like the Breach's and the Squall's."),
    DO_NOT_CONSERVE(AIHints.DO_NOT_CONSERVE, Scope.AMMO, "Fire freely",
            "Fired at every chance, even when ammo runs low."),
    CONSERVE(null, Scope.AMMO, "Save last shots",
            "Holds back the last 1 to 5 shots (bursts and salvos count as one) for a good target. Each click saves one more, then off."),
    CONSERVE_FOR_ANTI_ARMOR(AIHints.CONSERVE_FOR_ANTI_ARMOR, Scope.AMMO, "Save for armor",
            "Holds fire where the target's armor is already stripped, saving shots for armored parts, like the Breach.");

    /** The most shots "Save last shots" can hold back (the game's CONSERVE_1 to CONSERVE_5). */
    public static final int MAX_CONSERVE = 5;

    private static final AIHints[] CONSERVE_HINTS = {AIHints.CONSERVE_1, AIHints.CONSERVE_2, AIHints.CONSERVE_3,
            AIHints.CONSERVE_4, AIHints.CONSERVE_5};

    /** Which designs a hint is offered to. */
    public enum Scope { ALL, MISSILE, AMMO }

    /** The game's hint, or null for {@link #CONSERVE}, which picks one of five by the design's level. */
    public final AIHints aiHint;
    public final Scope scope;
    public final String label;
    public final String description;

    Hint(AIHints aiHint, Scope scope, String label, String description) {
        this.aiHint = aiHint;
        this.scope = scope;
        this.label = label;
        this.description = description;
    }

    /** Whether the Hints tab lists this hint for the design's kind. */
    public boolean shown(Design d) {
        switch (scope) {
            case MISSILE:
                return d.kind == Kind.MISSILE;
            case AMMO:
                return d.kind != Kind.BEAM;
            default:
                return true;
        }
    }

    /** Why a listed hint cannot be switched right now, or null when it can. */
    public String blocked(Design d) {
        if (!shown(d)) return "Not for this kind of weapon.";
        if (this == PD_ALSO && d.level(Script.POINT_DEFENSE) <= 0)
            return "Needs the Point defense script (Scripts tab).";
        if (scope == Scope.AMMO && d.kind.isGun() && d.unlimitedAmmo())
            return "Only for guns with limited ammo (Ammo on the Stats tab).";
        if (automatic(d)) return "On by itself: this design's missiles turn.";
        return null;
    }

    /** On without the player's choice: turning missiles never wait to be aimed. */
    public boolean automatic(Design d) {
        return this == DO_NOT_AIM && d.kind == Kind.MISSILE && d.value(Stat.GUIDANCE) > 0f;
    }

    /** Whether the hint is on, by choice or by itself. */
    public boolean on(Design d) {
        if (automatic(d)) return true;
        return this == CONSERVE ? d.conserve() > 0 : d.has(this);
    }

    /** The button text: the name, with the level for "Save last shots". */
    public String buttonText(Design d) {
        if (this != CONSERVE) return label;
        int n = d.conserve();
        return n <= 0 ? label + ": off" : "Save last " + n + (n == 1 ? " shot" : " shots");
    }

    /** The hints the Hints tab lists for a design, in order. */
    public static List<Hint> listed(Design d) {
        List<Hint> out = new ArrayList<>();
        for (Hint h : values()) if (h.shown(d)) out.add(h);
        return out;
    }

    /**
     * Every AI hint the design's weapon gets: point defense from its script, "fire off target"
     * for turning missiles, and the player's hints that apply to it.
     */
    public static EnumSet<AIHints> effective(Design d) {
        EnumSet<AIHints> out = EnumSet.noneOf(AIHints.class);
        if (d.level(Script.POINT_DEFENSE) > 0) out.add(AIHints.PD);
        for (Hint h : values()) {
            if (!h.on(d) || (h.blocked(d) != null && !h.automatic(d))) continue;
            if (h == CONSERVE) out.add(CONSERVE_HINTS[Math.min(MAX_CONSERVE, d.conserve()) - 1]);
            else out.add(h.aiHint);
        }
        return out;
    }
}
