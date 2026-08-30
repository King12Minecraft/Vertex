import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.Point2D;

/**
 * GlowBackdrop
 * ------------
 * Paints a soft radial gradient glow in the corner of a panel, using
 * the active theme's accent gradient. Used on "hero" screens like the
 * login/create account screens to give them presence, matching how
 * Opera GX treats its own first-run/login screens - a glow accent
 * behind the content, not applied to every panel in the app.
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

        Color accent = ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_START);
        Point2D center = new Point2D.Float(width * 0.12f, height * 0.05f);
        float radius = Math.max(width, height) * 0.75f;

        Color glow = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 55);
        Color transparent = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 0);

        RadialGradientPaint paint = new RadialGradientPaint(
            center, radius, new float[] {0f, 1f}, new Color[] {glow, transparent});

        g2.setPaint(paint);
        g2.fillRect(0, 0, width, height);
    }
}
