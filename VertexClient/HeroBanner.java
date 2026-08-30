import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.GeneralPath;

/**
 * HeroBanner
 * ----------
 * The large featured-game banner at the top of the Games page.
 * Matches the reference directly: a moody full-bleed dark background,
 * a small cyan tracked-caps kicker ("FEATURED NOW") sitting above a
 * huge bold title, both anchored bottom-left, and one full-width solid
 * cyan CTA bar flush against the very bottom edge - not a small pill
 * button floating in the text block. The featured game's own icon
 * (GameCardArt.paintIconOnly) is rendered huge and faint off to the
 * right as atmosphere, in place of separate hero artwork.
 */
public class HeroBanner extends JPanel
{
    private static final int CTA_HEIGHT = 54;

    private final GameInfo game;
    private boolean playHover = false;
    private Rectangle playButtonBounds = new Rectangle();

    public HeroBanner(GameInfo game)
    {
        this.game = game;
        setOpaque(false);
        setPreferredSize(new Dimension(0, 320));
        setCursor(Cursor.getDefaultCursor());

        ThemeManager.addListener(new Runnable()
        {
            public void run() { repaint(); }
        });

        addMouseMotionListener(new MouseMotionAdapter()
        {
            public void mouseMoved(MouseEvent e)
            {
                boolean over = playButtonBounds.contains(e.getPoint());
                if (over != playHover)
                {
                    playHover = over;
                    setCursor(over ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
                    repaint();
                }
            }
        });

        addMouseListener(new MouseAdapter()
        {
            public void mouseClicked(MouseEvent e)
            {
                if (playButtonBounds.contains(e.getPoint()))
                {
                    GameLauncher.launch(HeroBanner.this, game);
                }
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        UITheme.applyAntialiasing(g2);

        int w = getWidth();
        int h = getHeight();
        int cut = 22;

        GeneralPath shape = ChamferShape.build(0, 0, w, h, cut);
        g2.setClip(shape);

        Color start = ThemeManager.getColor(ThemeColor.BG_PANEL);
        Color end = ThemeManager.getColor(ThemeColor.BG_APP);
        LinearGradientPaint base = new LinearGradientPaint(
            0, 0, Math.max(w, 1), Math.max(h, 1), new float[] {0f, 1f}, new Color[] {start, end});
        g2.setPaint(base);
        g2.fillRect(0, 0, w, h);

        Color accent = ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_START);
        RadialGradientPaint glow = new RadialGradientPaint(
            w * 0.78f, h * 0.42f, Math.max(Math.max(w, h) * 0.5f, 1f),
            new float[] {0f, 1f},
            new Color[] {
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 70),
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 0)
            });
        g2.setPaint(glow);
        g2.fillRect(0, 0, w, h);

        Graphics2D iconG2 = (Graphics2D) g2.create();
        iconG2.translate(w - h * 0.85, h * 0.06);
        iconG2.setColor(new Color(255, 255, 255, 20));
        iconG2.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        GameCardArt.paintIconOnly(iconG2, (int) (h * 0.8), (int) (h * 0.8), game.getGameId());
        iconG2.dispose();

        int padX = 40;
        int ctaTop = h - CTA_HEIGHT;

        g2.setColor(ThemeManager.getColor(ThemeColor.ACCENT));
        g2.setFont(UITheme.FONT_SMALL.deriveFont(Font.BOLD, 12f));
        String kicker = game.isOnline() ? "FEATURED NOW - MULTIPLAYER" : "FEATURED NOW - PRACTICE MODE";
        g2.drawString(trackedCaps(kicker), padX, ctaTop - 68);

        g2.setColor(Color.WHITE);
        g2.setFont(UITheme.FONT_HEADING.deriveFont(Font.BOLD, 42f));
        g2.drawString(game.getName().toUpperCase(), padX, ctaTop - 24);

        paintCtaBar(g2, w, ctaTop, CTA_HEIGHT);

        g2.setClip(null);
        g2.setColor(ThemeManager.getColor(ThemeColor.BORDER));
        g2.setStroke(new BasicStroke(1.5f));
        g2.draw(shape);

        g2.dispose();
    }

    /** Cheap letter-spacing for small tracked-caps labels - Graphics2D has no native tracking control. */
    private String trackedCaps(String text)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++)
        {
            sb.append(text.charAt(i));
            if (i < text.length() - 1)
            {
                sb.append('\u200A');
            }
        }
        return sb.toString();
    }

    /** A full-width solid cyan bar flush at the very bottom edge - the reference's signature CTA treatment. */
    private void paintCtaBar(Graphics2D g2, int w, int y, int barH)
    {
        playButtonBounds = new Rectangle(0, y, w, barH);

        Color base = ThemeManager.getColor(ThemeColor.ACCENT);
        Color fill = playHover ? ThemeManager.getColor(ThemeColor.ACCENT_HOVER) : base;
        g2.setColor(fill);
        g2.fillRect(0, y, w, barH);

        g2.setColor(ThemeManager.getColor(ThemeColor.BG_APP));
        g2.setFont(UITheme.FONT_NAV_BOLD.deriveFont(Font.BOLD, 15f));
        String label = "\u25B6  PLAY NOW";
        FontMetrics fm = g2.getFontMetrics();
        int textW = fm.stringWidth(label);
        g2.drawString(label, (w - textW) / 2, y + barH / 2 + fm.getAscent() / 2 - 4);
    }
}
