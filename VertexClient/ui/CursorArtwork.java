package ui;

import theme.ThemeColor;
import theme.ThemeManager;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Shape;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

/**
 * CursorArtwork
 * -------------
 * The mouse-cursor pictures, all drawn in code (nothing copied, nothing to ship, crisp at any size). Each cursor
 * set is its own MODEL, not a recolour of one arrow: Classic is the familiar sharp pointer, Claude a soft rounded
 * paper-plane pointer, Crystal a faceted gem, Ember a flame, Neon a reticle (its click point is the centre). Every
 * set has an arrow, a "link" version (the same pointer with a small ring) and a text I-beam, so a set still looks
 * like one family. A soft glow can be switched on in Settings (CursorManager.isGlow) - off by default.
 * Shapes are laid out on a 32x32 grid and scaled to whatever size the platform's best cursor size is.
 */
public final class CursorArtwork
{
    private CursorArtwork() { }

    /** The three cursor roles the app actually uses (Swing's default / hand / text cursors). */
    public enum Role { ARROW, LINK, TEXT }

    /** A named cursor design. SYSTEM means "leave the operating system's cursors alone". */
    public enum CursorSet
    {
        SYSTEM("System default"),
        CLAUDE("Claude"),
        CLASSIC("Classic"),
        CRYSTAL("Crystal"),
        EMBER("Ember"),
        NEON("Neon"),
        APP("Match my theme");

        private final String label;
        CursorSet(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    /** Where the click lands, in 32x32 grid units - the tip for the pointer designs, the centre for the Neon reticle and the I-beam. */
    public static Point hotspot(CursorSet set, Role role)
    {
        if (role == Role.TEXT || set == CursorSet.NEON)
        {
            return new Point(16, 16);
        }
        return new Point(3, 2);
    }

    /** Draws one cursor at the given pixel size (square, transparent background); the glow follows the user's Settings choice. */
    public static BufferedImage draw(CursorSet set, Role role, int size)
    {
        return draw(set, role, size, CursorManager.isGlow());
    }

    public static BufferedImage draw(CursorSet set, Role role, int size, boolean glow)
    {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        double s = size / 32.0;
        g.scale(s, s);

        Style style = styleFor(set);
        if (!glow)
        {
            style.glow = new Color(0, 0, 0, 0);
        }

        if (role == Role.TEXT)
        {
            drawIBeam(g, set, style);
        }
        else if (set == CursorSet.NEON)
        {
            drawReticle(g, style, role == Role.LINK);
        }
        else
        {
            Shape body = bodyShape(set);
            drawGlow(g, body, style);
            g.setPaint(new GradientPaint(3, 2, style.top, 13, 27, style.bottom));
            g.fill(body);
            if (set == CursorSet.CRYSTAL)
            {
                drawFacets(g);
            }
            g.setColor(style.outline);
            g.setStroke(new BasicStroke(style.outlineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(body);
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
    }

    private static Style styleFor(CursorSet set)
    {
        Style st = new Style();
        switch (set)
        {
            case CLAUDE:
                st.top = new Color(224, 128, 96);
                st.bottom = new Color(193, 95, 60);
                st.outline = new Color(92, 40, 24);
                st.glow = new Color(217, 119, 87, 100);
                st.outlineWidth = 1.5f;
                break;
            case CLASSIC:
                st.top = Color.WHITE;
                st.bottom = new Color(228, 228, 228);
                st.outline = new Color(15, 15, 15);
                st.glow = new Color(255, 255, 255, 110);
                break;
            case CRYSTAL:
                st.top = new Color(170, 240, 255);
                st.bottom = new Color(60, 150, 225);
                st.outline = new Color(12, 40, 85);
                st.glow = new Color(90, 200, 255, 100);
                break;
            case EMBER:
                st.top = new Color(255, 205, 90);
                st.bottom = new Color(228, 62, 28);
                st.outline = new Color(75, 16, 10);
                st.glow = new Color(255, 120, 40, 100);
                break;
            case NEON:
                st.top = new Color(255, 120, 235);
                st.bottom = new Color(255, 70, 225);
                st.outline = new Color(30, 8, 40);
                st.glow = new Color(255, 70, 225, 120);
                st.outlineWidth = 1.8f;
                break;
            case APP:
            default:
                st.top = ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_START);
                st.bottom = ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_END);
                Color accent = ThemeManager.getColor(ThemeColor.ACCENT);
                st.outline = accent.darker().darker();
                st.glow = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 100);
                break;
        }
        return st;
    }

    /** The silhouette of each pointer design - the tip sits at (3,2). */
    private static Shape bodyShape(CursorSet set)
    {
        Path2D p = new Path2D.Double();
        switch (set)
        {
            case CLASSIC:
                p.moveTo(3, 2);
                p.lineTo(3, 24.5);
                p.lineTo(8.6, 19.2);
                p.lineTo(12.6, 28);
                p.lineTo(16.6, 26.2);
                p.lineTo(12.7, 17.6);
                p.lineTo(20.5, 17.4);
                p.closePath();
                break;
            case CRYSTAL:
                // a cut gem: six straight edges, tip at the upper left
                p.moveTo(3, 2);
                p.lineTo(14, 4.5);
                p.lineTo(23, 12);
                p.lineTo(21.5, 24);
                p.lineTo(11, 28);
                p.lineTo(5, 16);
                p.closePath();
                break;
            case EMBER:
                // a flame leaning toward the tip
                p.moveTo(3, 2);
                p.curveTo(9, 5, 17.5, 10, 19, 18);
                p.curveTo(20, 23.5, 16.5, 28.5, 11.5, 28.5);
                p.curveTo(7.5, 28.5, 5.5, 25.5, 6.5, 21.5);
                p.curveTo(7.4, 18.2, 9.6, 17.2, 8.2, 13.6);
                p.curveTo(7, 10.5, 4, 8, 3, 2);
                p.closePath();
                break;
            case CLAUDE:
            case APP:
            default:
                // a soft, fully rounded paper-plane pointer
                p.moveTo(4.2, 2.9);
                p.lineTo(23.2, 12.9);
                p.quadTo(25.6, 14.3, 23, 15.5);
                p.lineTo(15.2, 18.4);
                p.lineTo(12, 26.2);
                p.quadTo(10.8, 28.8, 9.6, 26.2);
                p.lineTo(3.2, 4.4);
                p.quadTo(2.6, 2.3, 4.2, 2.9);
                p.closePath();
                break;
        }
        return p;
    }

    private static void drawGlow(Graphics2D g, Shape shape, Style style)
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

    /** Light and dark facets across the gem. */
    private static void drawFacets(Graphics2D g)
    {
        Path2D light = new Path2D.Double();
        light.moveTo(3, 2);
        light.lineTo(14, 4.5);
        light.lineTo(13, 15);
        light.lineTo(5, 16);
        light.closePath();
        g.setColor(new Color(255, 255, 255, 120));
        g.fill(light);

        Path2D dark = new Path2D.Double();
        dark.moveTo(13, 15);
        dark.lineTo(21.5, 24);
        dark.lineTo(11, 28);
        dark.lineTo(5, 16);
        dark.closePath();
        g.setColor(new Color(10, 40, 100, 80));
        g.fill(dark);
    }

    /** The Neon design: a targeting reticle (ring, four ticks, centre dot) - the click lands at its centre. */
    private static void drawReticle(Graphics2D g, Style style, boolean filled)
    {
        Shape ring = new java.awt.geom.Ellipse2D.Double(9, 9, 14, 14);
        Path2D ticks = new Path2D.Double();
        ticks.moveTo(16, 2.5);  ticks.lineTo(16, 7);
        ticks.moveTo(16, 25);   ticks.lineTo(16, 29.5);
        ticks.moveTo(2.5, 16);  ticks.lineTo(7, 16);
        ticks.moveTo(25, 16);   ticks.lineTo(29.5, 16);
        Path2D all = new Path2D.Double();
        all.append(ring, false);
        all.append(ticks, false);

        if (style.glow.getAlpha() != 0)
        {
            g.setColor(style.glow);
            g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(all);
        }
        g.setColor(style.outline);
        g.setStroke(new BasicStroke(4.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(all);
        g.setColor(style.top);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(all);
        if (filled)
        {
            g.setColor(style.outline);
            g.fill(new java.awt.geom.Ellipse2D.Double(11.8, 11.8, 8.4, 8.4));
            g.setColor(style.bottom);
            g.fill(new java.awt.geom.Ellipse2D.Double(13, 13, 6, 6));
        }
        else
        {
            g.setColor(style.outline);
            g.fill(new java.awt.geom.Ellipse2D.Double(13.2, 13.2, 5.6, 5.6));
            g.setColor(style.top);
            g.fill(new java.awt.geom.Ellipse2D.Double(14.2, 14.2, 3.6, 3.6));
        }
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

    /** The text I-beam, with a design-specific cap: Classic plain, Claude rounded, Crystal and Ember pointed, Neon thin. */
    private static void drawIBeam(Graphics2D g, CursorSet set, Style style)
    {
        Path2D beam = new Path2D.Double();
        double cap = set == CursorSet.CRYSTAL || set == CursorSet.EMBER ? 6 : 5;
        beam.moveTo(16 - cap, 5);   beam.lineTo(16 + cap, 5);
        beam.moveTo(16, 5);         beam.lineTo(16, 27);
        beam.moveTo(16 - cap, 27);  beam.lineTo(16 + cap, 27);
        if (set == CursorSet.CRYSTAL || set == CursorSet.EMBER)
        {
            beam.moveTo(16 - cap, 5);   beam.lineTo(16, 8);   beam.lineTo(16 + cap, 5);
            beam.moveTo(16 - cap, 27);  beam.lineTo(16, 24);  beam.lineTo(16 + cap, 27);
        }
        float width = set == CursorSet.NEON ? 1.5f : 2.2f;
        // outline pass, then the coloured pass on top, so it reads on light and dark backgrounds
        if (style.glow.getAlpha() != 0)
        {
            g.setColor(style.glow);
            g.setStroke(new BasicStroke(6.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(beam);
        }
        g.setColor(style.outline);
        g.setStroke(new BasicStroke(width + 2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(beam);
        g.setColor(set == CursorSet.CLASSIC ? Color.WHITE : style.top);
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(beam);
    }
}
