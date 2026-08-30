import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.JButton;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

/**
 * QuickPlayDropdown
 * ------------------
 * Top bar "Play" dropdown - lists every playable game from
 * GameManager's cache, click one to launch it via GameLauncher without
 * needing to navigate to the Games page first.
 */
public class QuickPlayDropdown extends JButton
{
    private boolean hover = false;

    public QuickPlayDropdown()
    {
        setPreferredSize(new Dimension(96, 38));
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        addMouseListener(new MouseAdapter()
        {
            public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
            public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
            public void mouseClicked(MouseEvent e) { showPopup(); }
        });

        ThemeManager.addListener(new Runnable()
        {
            public void run() { repaint(); }
        });
    }

    private void showPopup()
    {
        JPopupMenu popup = new JPopupMenu();
        popup.setBorder(new LineBorder(ThemeManager.getColor(ThemeColor.BORDER), 1));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ThemeManager.getColor(ThemeColor.BG_PANEL));
        content.setBorder(new EmptyBorder(8, 8, 8, 8));
        content.setPreferredSize(new Dimension(220, -1));

        List<GameInfo> games = GameManager.getCachedGames();
        if (games.isEmpty())
        {
            JLabel empty = new JLabel("No games loaded yet");
            empty.setFont(UITheme.FONT_BODY);
            empty.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
            empty.setBorder(new EmptyBorder(6, 6, 6, 6));
            content.add(empty);
        }
        else
        {
            for (int i = 0; i < games.size(); i++)
            {
                content.add(buildRow(games.get(i), popup));
            }
        }

        popup.add(content);
        popup.show(this, 0, getHeight() + 6);
    }

    private JPanel buildRow(final GameInfo game, final JPopupMenu popup)
    {
        final JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(true);
        row.setBackground(ThemeManager.getColor(ThemeColor.BG_PANEL));
        row.setBorder(new EmptyBorder(8, 10, 8, 10));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(2000, 40));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel name = new JLabel(game.getName());
        name.setFont(UITheme.FONT_BODY);
        name.setForeground(game.isComingSoon()
            ? ThemeManager.getColor(ThemeColor.TEXT_MUTED)
            : ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));

        StatusPill pill = new StatusPill(game.getStatusText(), game.isComingSoon()
            ? ThemeManager.getColor(ThemeColor.ACCENT)
            : (game.isOnline() ? ThemeManager.getColor(ThemeColor.SUCCESS) : ThemeManager.getColor(ThemeColor.TEXT_MUTED)));

        row.add(name);
        row.add(javax.swing.Box.createHorizontalGlue());
        row.add(pill);

        row.addMouseListener(new MouseAdapter()
        {
            public void mouseEntered(MouseEvent e) { row.setBackground(ThemeManager.getColor(ThemeColor.BG_PANEL_HOVER)); }
            public void mouseExited(MouseEvent e)  { row.setBackground(ThemeManager.getColor(ThemeColor.BG_PANEL)); }
            public void mouseClicked(MouseEvent e)
            {
                popup.setVisible(false);
                GameLauncher.launch(row, game);
            }
        });

        return row;
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        UITheme.applyAntialiasing(g2);

        int w = getWidth();
        int h = getHeight();

        Color base = hover
            ? ThemeManager.getColor(ThemeColor.BG_PANEL_HOVER)
            : ThemeManager.getColor(ThemeColor.BG_SIDEBAR);
        g2.setColor(base);
        g2.fillRoundRect(0, 0, w, h, UITheme.RADIUS_BUTTON, UITheme.RADIUS_BUTTON);

        g2.setColor(ThemeManager.getColor(ThemeColor.BORDER));
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawRoundRect(0, 0, w - 1, h - 1, UITheme.RADIUS_BUTTON, UITheme.RADIUS_BUTTON);

        g2.setColor(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        g2.setFont(UITheme.FONT_BODY);
        int textY = h / 2 + g2.getFontMetrics().getAscent() / 2 - 2;
        g2.drawString("Play", 14, textY);

        g2.setColor(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int cx = w - 18;
        int cy = h / 2 - 2;
        g2.drawLine(cx - 4, cy, cx, cy + 5);
        g2.drawLine(cx, cy + 5, cx + 4, cy);

        g2.dispose();
        super.paintComponent(g);
    }
}
