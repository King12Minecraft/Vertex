package ui;
import games.GameCardArt;
import theme.ThemeColor;
import theme.UITheme;
import games.GameLauncher;
import theme.ThemeManager;
import games.GameInfo;

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
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.RoundRectangle2D;

/**
 * HeroBanner
 * ----------
 * The large featured-game banner at the top of the Games page. Aurora
 * Glass version: a moody full-bleed dark background with a soft
 * ambient accent glow, a small cyan tracked-caps kicker ("FEATURED
 * NOW") above a huge bold title, both anchored bottom-left, and a
 * rounded outlined-glow "PLAY NOW" pill sitting under the title -
 * replacing the earlier chamfered shape and full-width solid CTA bar,
 * which read as flatter and more "Opera GX blocky" than the rest of
 * the reskinned shell. The featured game's own icon
 * (GameCardArt.paintIconOnly) is still rendered huge and faint off to
 * the right as atmosphere, in place of separate hero artwork.
 */
public class HeroBanner extends JPanel
{
    private static final int PAD_X = 40;
    private static final int PAD_BOTTOM = 40;
    private static final int BUTTON_HEIGHT = 52;

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
        int radius = UITheme.RADIUS_PANEL + 6;

        Shape shape = new RoundRectangle2D.Float(0, 0, w, h, radius, radius);
        g2.setClip(shape);

        // Flat: the card colour, and the game's own tile (same art as its catalogue card) on the right.
        g2.setColor(ThemeManager.getColor(ThemeColor.BG_PANEL));
        g2.fillRect(0, 0, w, h);

        int tileH = h - 48;
        int tileW = Math.min((int) (tileH * 1.5), w / 2 - 24);
        Graphics2D tileG2 = (Graphics2D) g2.create();
        tileG2.translate(w - tileW - 24, 24);
        games.GameCardArt art = new games.GameCardArt(game.getGameId());
        art.setSize(tileW, tileH);
        art.print(tileG2);
        tileG2.dispose();

        int buttonY = h - PAD_BOTTOM - BUTTON_HEIGHT;
        int titleY = buttonY - 22;
        int kickerY = titleY - 46;

        g2.setColor(ThemeManager.getColor(ThemeColor.ACCENT));
        g2.setFont(UITheme.FONT_NAV_BOLD);
        String kicker = game.isOnline() ? "Featured - multiplayer" : "Featured - practice mode";
        g2.drawString(kicker, PAD_X, kickerY);

        g2.setColor(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        g2.setFont(UITheme.FONT_HEADING.deriveFont(Font.PLAIN, 40f));
        g2.drawString(game.getName(), PAD_X, titleY);

        paintCtaButton(g2, PAD_X, buttonY);

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

    /** The Play button under the title: solid accent, label readable on it, no glow. */
    private void paintCtaButton(Graphics2D g2, int x, int y)
    {
        String label = "Play now";
        g2.setFont(UITheme.FONT_NAV_BOLD);
        FontMetrics fm = g2.getFontMetrics();
        int buttonW = fm.stringWidth(label) + 48;
        int radius = UITheme.RADIUS_BUTTON;

        playButtonBounds = new Rectangle(x, y, buttonW, BUTTON_HEIGHT);

        g2.setColor(ThemeManager.getColor(playHover ? ThemeColor.ACCENT_HOVER : ThemeColor.ACCENT));
        g2.fillRoundRect(x, y, buttonW, BUTTON_HEIGHT, radius, radius);

        g2.setColor(ThemeManager.onAccent());
        int textW = fm.stringWidth(label);
        g2.drawString(label, x + (buttonW - textW) / 2, y + (BUTTON_HEIGHT + fm.getAscent() - fm.getDescent()) / 2);
    }
}
