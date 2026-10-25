package weapondesigner.ui;

import java.awt.Color;

import org.lwjgl.opengl.GL11;

/**
 * Small drawing helpers for the designer screen, from Gambling Den.
 *
 * Everything here draws in screen coordinates, where y grows upwards.
 * Callers are expected to have already set up blending, which the panel does once per frame.
 */
public final class GLDraw {

    private GLDraw() {
    }

    public static Color brighten(Color c, float amount) {
        return new Color(
                Math.min(255, (int) (c.getRed() + (255 - c.getRed()) * amount)),
                Math.min(255, (int) (c.getGreen() + (255 - c.getGreen()) * amount)),
                Math.min(255, (int) (c.getBlue() + (255 - c.getBlue()) * amount)),
                c.getAlpha());
    }

    public static Color darken(Color c, float amount) {
        float keep = 1f - amount;
        return new Color(
                (int) (c.getRed() * keep),
                (int) (c.getGreen() * keep),
                (int) (c.getBlue() * keep),
                c.getAlpha());
    }

    private static void setColor(Color c, float alphaMult) {
        GL11.glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f,
                (c.getAlpha() / 255f) * alphaMult);
    }

    public static void quad(float x, float y, float w, float h, Color color, float alphaMult) {
        setColor(color, alphaMult);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + w, y);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x, y + h);
        GL11.glEnd();
    }

    /** A rectangle lit from the top left, so it reads as a raised panel. */
    public static void bevelledPanel(float x, float y, float w, float h, Color base,
                                     float thickness, float alphaMult) {
        Color light = brighten(base, 0.35f);
        Color dark = darken(base, 0.45f);

        quad(x, y, w, h, dark, alphaMult);
        quad(x, y + h - thickness, w, thickness, light, alphaMult);
        quad(x, y, thickness, h, light, alphaMult);
        quad(x + thickness, y + thickness, w - thickness * 2f, h - thickness * 2f, base, alphaMult);
    }

    /** A hollow frame, drawn as four bars. */
    public static void frame(float x, float y, float w, float h, Color color,
                             float thickness, float alphaMult) {
        quad(x, y + h - thickness, w, thickness, color, alphaMult);
        quad(x, y, w, thickness, color, alphaMult);
        quad(x, y, thickness, h, color, alphaMult);
        quad(x + w - thickness, y, thickness, h, color, alphaMult);
    }
}
