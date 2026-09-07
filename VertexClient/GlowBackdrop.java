import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.Point2D;

/**
 * GlowBackdrop
 * ------------
 * Paints two soft radial glows - one from the active theme's accent
 * start color anchored near the top-left, one from its accent end
 * color near the bottom-right - using the active theme's accent
 * gradient. Used on "hero" screens like the login/create account
 * screens to give them presence, matching how modern gaming
 * platforms (Opera GX, Discord's dark themes) treat their own
 * first-run/login screens: an ambient dual-tone glow behind the
 * content, not applied to every panel in the app.
 */
public class GlowBackdrop
{
    private GlowBackdrop()
    {
        // Static utility class - never instantiated.
    }

    public static void paint(Graphics2D g2, int width, int height)
    {
        if (width <= 0 || height <= 0)
        {
            return;
        }

        paintGlow(g2, width, height,
            ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_START), 0.12f, 0.05f, 0.75f, 55);
        paintGlow(g2, width, height,
            ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_END), 0.92f, 0.98f, 0.65f, 40);
    }

    private static void paintGlow(Graphics2D g2, int width, int height, Color accent,
                                   float centerXPct, float centerYPct, float radiusPct, int alpha)
    {
        Point2D center = new Point2D.Float(width * centerXPct, height * centerYPct);
        float radius = Math.max(width, height) * radiusPct;

        Color glow = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), alpha);
        Color transparent = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 0);

        RadialGradientPaint paint = new RadialGradientPaint(
            center, radius, new float[] {0f, 1f}, new Color[] {glow, transparent});

        g2.setPaint(paint);
        g2.fillRect(0, 0, width, height);
    }
}
