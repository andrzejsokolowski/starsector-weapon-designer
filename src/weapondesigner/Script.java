package weapondesigner;

/**
 * Weapon effects a design can buy in levels. Level 0 is off. The effects themselves run in
 * {@code weapondesigner.combat}; this holds what each level does and what it is worth.
 */
public enum Script {
    ARC("Arc discharge",
            "Hits on hull or armor throw lightning arcs at the target. Each arc deals half the hit's damage as energy damage plus as much EMP. Every level adds 0.2 arcs per hit."),
    BREACH("Shield breach",
            "Hits on shields throw arcs that pass through the shield. Each arc deals half the hit's damage as energy damage plus as much EMP. Every level adds 0.15 arcs per hit."),
    BLAST("Detonation",
            "Every hit explodes, damaging everything nearby except allies. Every level adds 25% of the hit's damage to the blast and 20 su to its radius."),
    SHRED("Armor breaker",
            "Hits on hull or armor deal extra high explosive damage that ignores shields. Every level adds 15% of the hit's damage."),
    OVERLOAD("Shield overload",
            "Hits on shields push extra hard flux into the target. Every level adds 20% of the hit's damage."),
    LEECH("Leech",
            "Hull damage dealt repairs the firing ship's hull. Every level repairs 5% of that damage."),
    SLOW("Engine jammer",
            "Hits on hull or armor slow the target for 3 seconds. Every level makes it 15% slower than the level before."),
    POINT_DEFENSE("Point defense",
            "The ship's AI fires this weapon at missiles and fighters first, like a point defense weapon.");

    /** Beams have no separate hits, so they count one every this many seconds of contact. */
    public static final float BEAM_PULSE = 0.5f;

    public final String label;
    public final String description;

    Script(String label, String description) {
        this.label = label;
        this.description = description;
    }

    /** The highest level: point defense is on or off, the rest keep going. */
    public int top() {
        return this == POINT_DEFENSE ? 1 : Stat.MAX_RUNG;
    }

    public static float arcsPerHit(int level) {
        return 0.2f * level;
    }

    public static float breachArcsPerHit(int level) {
        return 0.15f * level;
    }

    public static float blastShare(int level) {
        return 0.25f * level;
    }

    public static float blastRadius(int level) {
        return 40f + 20f * level;
    }

    public static float shredShare(int level) {
        return 0.15f * level;
    }

    public static float overloadShare(int level) {
        return 0.2f * level;
    }

    public static float leechShare(int level) {
        return 0.05f * level;
    }

    /** What the target's top speed is multiplied by. */
    public static float slowMult(int level) {
        return (float) Math.pow(0.85, level);
    }

    /**
     * What a level adds to the weapon's worth, as a share of its stats' worth. Roughly the extra
     * damage it brings, discounted when it only works on one kind of hit.
     */
    public float worth(int level) {
        if (level <= 0) return 0f;
        switch (this) {
            case ARC:
                return 0.12f * level;
            case BREACH:
                return 0.1f * level;
            case BLAST:
                return 0.12f * level;
            case SHRED:
                return 0.1f * level;
            case OVERLOAD:
                return 0.08f * level;
            case LEECH:
                return 0.05f * level;
            case SLOW:
                return 0.06f * level;
            case POINT_DEFENSE:
                return 0.1f;
            default:
                return 0f;
        }
    }

    /** Short text for the designer's value column. */
    public String valueText(int level) {
        if (level <= 0) return "Off";
        switch (this) {
            case ARC:
                return Text.number(arcsPerHit(level)) + " per hit";
            case BREACH:
                return Text.number(breachArcsPerHit(level)) + " per hit";
            case BLAST:
                return Text.percent(blastShare(level)) + ", " + Text.number(blastRadius(level)) + " su";
            case SHRED:
                return "+" + Text.percent(shredShare(level));
            case OVERLOAD:
                return "+" + Text.percent(overloadShare(level)) + " flux";
            case LEECH:
                return Text.percent(leechShare(level));
            case SLOW:
                return "-" + Text.percent(1f - slowMult(level)) + " speed";
            case POINT_DEFENSE:
                return "On";
            default:
                return String.valueOf(level);
        }
    }
}
