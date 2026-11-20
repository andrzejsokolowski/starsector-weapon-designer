package weapondesigner;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModSpecAPI;

/**
 * The game can only ask for one exact version of a required mod: it turns this mod off when the
 * installed OddiszCommons is any other version. So mod_info.json names OddiszCommons without a
 * version, and the lowest version whose Manage tabs can always be reached is checked here: 1.0.2,
 * which shows them through the intel screen when no Manage button on the bottom bar can.
 */
public final class OddiszCommonsVersion {
    static final String ID = "oddisz_commons";
    static final int[] MINIMUM = {1, 0, 2};
    private static final String TOPIC = "https://fractalsoftworks.com/forum/index.php?topic=36282";

    private OddiszCommonsVersion() {
    }

    /** Stops the game at start with a clear message when OddiszCommons is too old. */
    static void require() {
        ModSpecAPI spec;
        try {
            spec = Global.getSettings().getModManager().getModSpec(ID);
        } catch (RuntimeException e) {
            return; // Cannot look: let the game start rather than crash it here.
        }
        if (spec == null) return; // The launcher already refuses to start without it.
        if (atLeast(spec.getVersion(), MINIMUM)) return;
        throw new RuntimeException("Weapon Designer needs OddiszCommons " + MINIMUM[0] + "." + MINIMUM[1] + "."
                + MINIMUM[2] + " or newer, but " + spec.getVersion() + " is installed. Update it: " + TOPIC);
    }

    /**
     * Reads the version text itself ("1.0.4"). The game's own split cannot be used: it reads a
     * version that starts with 0 as major "0.2", minor "1". A version this cannot read is let
     * through rather than blocking the game.
     */
    public static boolean atLeast(String version, int[] minimum) {
        if (version == null) return true;
        String[] parts = version.trim().split("[.]");
        for (int i = 0; i < minimum.length; i++) {
            Integer have = i < parts.length ? number(parts[i]) : Integer.valueOf(0);
            if (have == null) return true;
            if (have.intValue() != minimum[i]) return have.intValue() > minimum[i];
        }
        return true;
    }

    private static Integer number(String text) {
        int end = 0;
        while (end < text.length() && Character.isDigit(text.charAt(end))) end++;
        if (end == 0) return null;
        try {
            return Integer.valueOf(text.substring(0, end));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
