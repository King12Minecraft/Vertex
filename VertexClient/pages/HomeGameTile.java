package pages;

import games.GameCardArt;
import games.GameInfo;
import games.GameLauncher;
import theme.ThemeColor;
import theme.ThemeManager;
import theme.UITheme;
import ui.GameHubDialog;
import ui.RoundedPanel;
import ui.ThemedLabel;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;

/**
 * HomeGameTile
 * ------------
 * One game as a small clickable card: its art, name, a one-line status, and a star to pin/unpin it. Clicking
 * the card opens the game's detail page through GameLauncher (the one launch entry point every Play button goes
 * through). Used by "Continue playing".
 */
public class HomeGameTile extends RoundedPanel
{
    public static final int WIDTH = 132;
    public static final int HEIGHT = 138;

    private final GameInfo game;
    private boolean hover = false;

    public HomeGameTile(final GameInfo game, String statusLine)
    {
        super(ThemeColor.BG_APP, UITheme.RADIUS_BUTTON);
        this.game = game;
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(8, 8, 8, 8));
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setMaximumSize(new Dimension(WIDTH, HEIGHT));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        GameCardArt art = new GameCardArt(game.getGameId());
        art.setPreferredSize(new Dimension(WIDTH - 16, 66));
        add(art, BorderLayout.NORTH);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.setBorder(new EmptyBorder(8, 2, 0, 0));
        JLabel name = new ThemedLabel(game.getName(), ThemeColor.TEXT_PRIMARY);
        name.setFont(UITheme.FONT_NAV_BOLD.deriveFont(13f));
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel status = new ThemedLabel(statusLine, ThemeColor.TEXT_MUTED);
        status.setFont(UITheme.FONT_SMALL);
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(name);
        text.add(status);
        add(text, BorderLayout.CENTER);

        addMouseListener(new MouseAdapter()
        {
            public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
            public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            public void mouseClicked(MouseEvent e)
            {
                if (isOnStar(e))
                {
                    if (!PinnedGames.toggle(game.getGameId()))
                    {
                        GameHubDialog.show(HomeGameTile.this, "Pinned games",
                            "You can pin up to " + PinnedGames.MAX_PINNED + " games. Unpin one first.");
                    }
                }
                else
                {
                    GameLauncher.launch(HomeGameTile.this, game);
                }
            }
        });
    }

    /** The star sits in the top-right corner of the art. */
    private boolean isOnStar(MouseEvent e)
    {
        return e.getX() >= getWidth() - 34 && e.getY() <= 36;
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        UITheme.applyAntialiasing(g2);

        g2.setColor(ThemeManager.getColor(hover ? ThemeColor.ACCENT : ThemeColor.BORDER));
        g2.setStroke(new BasicStroke(hover ? 1.8f : 1f));
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, UITheme.RADIUS_BUTTON, UITheme.RADIUS_BUTTON);

        // the pin star: filled when pinned, an outline otherwise (only shown on hover when unpinned, so the tiles stay clean)
        boolean pinned = PinnedGames.isPinned(game.getGameId());
        if (pinned || hover)
        {
            Path2D star = star(getWidth() - 22, 22, 8.5, 3.6);
            if (pinned)
            {
                g2.setColor(ThemeManager.getColor(ThemeColor.ACCENT));
                g2.fill(star);
                g2.setColor(new java.awt.Color(0, 0, 0, 90));
                g2.setStroke(new BasicStroke(1f));
                g2.draw(star);
            }
            else
            {
                g2.setColor(new java.awt.Color(255, 255, 255, 200));
                g2.setStroke(new BasicStroke(1.4f));
                g2.draw(star);
            }
        }
        g2.dispose();
    }

    private static Path2D star(double cx, double cy, double outer, double inner)
    {
        Path2D p = new Path2D.Double();
        for (int i = 0; i < 10; i++)
        {
            double r = (i % 2 == 0) ? outer : inner;
            double a = -Math.PI / 2 + i * Math.PI / 5;
            double x = cx + r * Math.cos(a);
            double y = cy + r * Math.sin(a);
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.closePath();
        return p;
    }
}
