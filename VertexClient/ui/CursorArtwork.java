package ui;

import theme.ThemeColor;
import theme.ThemeManager;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

/**
 * CursorArtwork
 * -------------
 * The mouse-cursor pictures, all drawn in code (nothing copied, nothing to ship, crisp at any
 * size). Each cursor set is one colour treatment applied to three shapes - the arrow, the "link"
 * pointer and the text I-beam - so a set looks like one family. Shapes are laid out on a 32x32
 * grid and scaled to whatever size the platform's best cursor size is.
 */
public final class CursorArtwork
{
    private CursorArtwork() { }

    /** The three cursor roles the app actually uses (Swing's default / hand / text cursors). */
    public enum Role { ARROW, LINK, TEXT }

    /** A named colour treatment. SYSTEM means "leave the operating system's cursors alone". */
    public enum CursorSet
    {
        SYSTEM("System default"),
        CRYSTAL("Crystal"),
        EMBER("Ember"),
        NEON("Neon"),
        MONO("Mono"),
        APP("Match my theme");

        private final String label;
        CursorSet(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    /** Where the click lands, in 32x32 grid units. */
    public static Point hotspot(Role role)
    {
        if (role == Role.TEXT)
        {
            return new Point(16, 16);
        }
        return new Point(3, 2);
    }

    /** Draws one cursor at the given pixel size (square, transparent background). */
    public static BufferedImage draw(CursorSet set, Role role, int size)
    {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        double s = size / 32.0;
        g.scale(s, s);

        Style style = styleFor(set);
        if (role == Role.TEXT)
        {
            drawIBeam(g, style);
        }
        else
        {
            Path2D arrow = arrowShape();
            drawGlow(g, arrow, style);
            g.setPaint(new GradientPaint(3, 2, style.top, 13, 27, style.bottom));
            g.fill(arrow);
            if (style.facets)
            {
                drawFacets(g);
            }
            g.setColor(style.outline);
            g.setStroke(new BasicStroke(style.outlineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(arrow);
            if (role == Role.LINK)
            {
                drawLinkBadge(g, style);
            }
        }
        g.dispose();
        return image;
    }

    private static final class Style
    {
        Color top, bottom, outline, glow;
        float outlineWidth = 1.6f;
        boolean facets = false;
    }

    private static Style styleFor(CursorSet set)
    {
        Style st = new Style();
        switch (set)
        {
            case CRYSTAL:
                st.top = new Color(170, 240, 255);
                st.bottom = new Color(60, 150, 225);
                st.outline = new Color(12, 40, 85);
                st.glow = new Color(90, 200, 255, 90);
                st.facets = true;
                break;
            case EMBER:
                st.top = new Color(255, 205, 90);
                st.bottom = new Color(228, 62, 28);
                st.outline = new Color(75, 16, 10);
                st.glow = new Color(255, 120, 40, 90);
                break;
            case NEON:
                st.top = new Color(20, 18, 40);
                st.bottom = new Color(40, 20, 70);
                st.outline = new Color(255, 70, 225);
                st.glow = new Color(255, 70, 225, 110);
                st.outlineWidth = 1.9f;
                break;
            case MONO:
                st.top = Color.WHITE;
                st.bottom = new Color(225, 225, 225);
                st.outline = new Color(15, 15, 15);
                st.glow = new Color(0, 0, 0, 0);
                break;
            case APP:
            default:
                st.top = ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_START);
                st.bottom = ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_END);
                st.outline = ThemeManager.getColor(ThemeColor.BG_APP);
                Color accent = ThemeManager.getColor(ThemeColor.ACCENT);
                st.glow = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 90);
                break;
        }
        return st;
    }

    /** A sharp, slightly stylised pointer - the tip sits at (3,2). */
    private static Path2D arrowShape()
    {
        Path2D p = new Path2D.Double();
        p.moveTo(3, 2);
        p.lineTo(3, 24.5);
        p.lineTo(8.6, 19.2);
        p.lineTo(12.6, 28);
        p.lineTo(16.6, 26.2);
        p.lineTo(12.7, 17.6);
        p.lineTo(20.5, 17.4);
        p.closePath();
        return p;
    }

    private static void drawGlow(Graphics2D g, Path2D shape, Style style)
    {
        if (style.glow.getAlpha() == 0)
        {
            return;
        }
        for (int i = 3; i >= 1; i--)
        {
            g.setColor(new Color(style.glow.getRed(), style.glow.getGreen(), style.glow.getBlue(), style.glow.getAlpha() / (i + 1)));
            g.setStroke(new BasicStroke(1.6f + i * 1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(shape);
        }
    }

    /** Light and dark facets across the arrow - the "cut gem" look that echoes the logo. */
    private static void drawFacets(Graphics2D g)
    {
        Path2D light = new Path2D.Double();
        light.moveTo(3, 2);
        light.lineTo(3, 24.5);
        light.lineTo(8.6, 19.2);
        light.closePath();
        g.setColor(new Color(255, 255, 255, 110));
        g.fill(light);

        Path2D dark = new Path2D.Double();
        dark.moveTo(8.6, 19.2);
        dark.lineTo(12.6, 28);
        dark.lineTo(16.6, 26.2);
        dark.lineTo(12.7, 17.6);
        dark.closePath();
        g.setColor(new Color(10, 40, 100, 70));
        g.fill(dark);
    }

    /** A small ring at the lower right marks "this is clickable". */
    private static void drawLinkBadge(Graphics2D g, Style style)
    {
        g.setColor(style.glow.getAlpha() == 0 ? new Color(0, 0, 0, 40) : style.glow);
        g.setStroke(new BasicStroke(4.4f));
        g.drawOval(20, 20, 8, 8);
        g.setColor(style.outline);
        g.setStroke(new BasicStroke(2.2f));
        g.drawOval(20, 20, 8, 8);
        g.setColor(style.top);
        g.setStroke(new BasicStroke(1.0f));
        g.drawOval(20, 20, 8, 8);
    }

    private static void drawIBeam(Graphics2D g, Style style)
    {
        Path2D beam = new Path2D.Double();
        beam.moveTo(11, 5);   beam.lineTo(21, 5);
        beam.moveTo(16, 5);   beam.lineTo(16, 27);
        beam.moveTo(11, 27);  beam.lineTo(21, 27);
        // outline pass, then the coloured pass on top, so it reads on light and dark backgrounds
        g.setColor(style.outline);
        g.setStroke(new BasicStroke(4.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(beam);
        if (style.glow.getAlpha() != 0)
        {
            g.setColor(style.glow);
            g.setStroke(new BasicStroke(6.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(beam);
            g.setColor(style.outline);
            g.setStroke(new BasicStroke(4.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(beam);
        }
        g.setColor(style.top);
        g.setStroke(new BasicStroke(1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(beam);
    }
}
