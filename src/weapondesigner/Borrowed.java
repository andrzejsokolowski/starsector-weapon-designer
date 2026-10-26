package weapondesigner;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A script a design borrows from another loaded weapon: that weapon's on-hit, on-fire, every-frame
 * or beam effect. The donor's OP and name are kept with the design, so its cost and its row in the
 * designer stay the same if the mod that adds the donor is removed later.
 */
public final class Borrowed {
    public enum Type {
        HIT("On hit"),
        FIRE("On fire"),
        FRAME("Every frame"),
        BEAM("Beam");

        public final String label;

        Type(String label) {
            this.label = label;
        }

        /** Whether a design of this kind can carry this type: hits and firing need projectiles, beam effects a beam. */
        public boolean fits(Kind kind) {
            switch (this) {
                case HIT:
                case FIRE:
                    return kind != Kind.BEAM;
                case BEAM:
                    return kind == Kind.BEAM;
                default:
                    return true;
            }
        }
    }

    public final String donorId;
    public final Type type;
    /** The donor weapon's OP when it was borrowed (refreshed on load while the donor exists). */
    public float donorOp;
    public String donorName;

    public Borrowed(String donorId, Type type, float donorOp, String donorName) {
        this.donorId = donorId;
        this.type = type;
        this.donorOp = donorOp;
        this.donorName = donorName == null ? donorId : donorName;
    }

    public Borrowed copy() {
        return new Borrowed(donorId, type, donorOp, donorName);
    }

    public boolean same(String otherDonor, Type otherType) {
        return donorId.equals(otherDonor) && type == otherType;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("donor", donorId);
        json.put("type", type.name());
        json.put("op", (double) donorOp);
        json.put("name", donorName);
        return json;
    }

    /** Null when the entry cannot be read. */
    public static Borrowed fromJson(JSONObject json) {
        if (json == null || json.isNull("donor") || json.isNull("type")) return null;
        String donor = json.optString("donor", "");
        if (donor.isEmpty()) return null;
        Type type;
        try {
            type = Type.valueOf(json.optString("type"));
        } catch (IllegalArgumentException e) {
            return null;
        }
        float op = (float) json.optDouble("op", 0.0);
        String name = json.isNull("name") ? donor : json.optString("name", donor);
        return new Borrowed(donor, type, Float.isFinite(op) ? Math.max(0f, op) : 0f, name);
    }
}
