import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JButton;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

/**
 * ThemeDropdown
 * -------------
 * A themed dropdown button showing the current theme (with a small
 * gradient swatch) and a chevron - clicking opens a themed popup
 * listing every available theme, each row showing its own swatch and
 * name. Replaces the old grid-of-swatches picker in Settings.
 */
public class ThemeDropdown extends JButton
{
    private boolean hover = false;
    private boolean open = false;

    public ThemeDropdown()
    {
        setPreferredSize(new Dimension(240, 46));
        setMaximumSize(new Dimension(240, 46));
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        addMouseListener(new MouseAdapter()
        {
            public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
            public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
        });

        addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { showPopup(); }
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

        for (final Theme theme : ThemeManager.getAvailableThemes())
        {
            content.add(buildRow(theme, popup));
        }

        popup.add(content);
        open = true;
        repaint();
        popup.addPopupMenuListener(new javax.swing.event.PopupMenuListener()
        {
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) { open = false; repaint(); }
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) { open = false; repaint(); }
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) { }
        });
        popup.show(this, 0, getHeight() + 6);
    }

    private JPanel buildRow(final Theme theme, final JPopupMenu popup)
    {
        boolean selected = theme == ThemeManager.getCurrentTheme();

        final JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(true);
        row.setBackground(selected
            ? ThemeManager.getColor(ThemeColor.BG_PANEL_HOVER)
            : ThemeManager.getColor(ThemeColor.BG_PANEL));
        row.setBorder(new EmptyBorder(8, 10, 8, 10));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(2000, 42));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel swatch = new JPanel()
        {
            @Override
            protected void paintComponent(Graphics g)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                UITheme.applyAntialiasing(g2);
                LinearGradientPaint gradient = new LinearGradientPaint(
                    0, 0, getWidth(), getHeight(),
                    new float[] {0f, 1f}, new Color[] {theme.accentGradientStart(), theme.accentGradientEnd()});
                g2.setPaint(gradient);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                g2.dispose();
            }
        };
        swatch.setOpaque(false);
        swatch.setPreferredSize(new Dimension(22, 22));
        swatch.setMaximumSize(new Dimension(22, 22));
        swatch.setAlignmentY(Component.CENTER_ALIGNMENT);

        JLabel name = new JLabel(theme.getName());
        name.setFont(UITheme.FONT_BODY);
        name.setForeground(selected
            ? ThemeManager.getColor(ThemeColor.ACCENT)
            : ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        name.setBorder(new EmptyBorder(0, 10, 0, 0));
        name.setAlignmentY(Component.CENTER_ALIGNMENT);

        row.add(swatch);
        row.add(name);
        row.add(Box.createHorizontalGlue());

        if (selected)
        {
            JLabel check = new JLabel("\u2713");
            check.setFont(UITheme.FONT_NAV_BOLD);
            check.setForeground(ThemeManager.getColor(ThemeColor.ACCENT));
            row.add(check);
        }

        row.addMouseListener(new MouseAdapter()
        {
            public void mouseEntered(MouseEvent e) { row.setBackground(ThemeManager.getColor(ThemeColor.BG_PANEL_HOVER)); }
            public void mouseExited(MouseEvent e)  { row.setBackground(theme == ThemeManager.getCurrentTheme()
                ? ThemeManager.getColor(ThemeColor.BG_PANEL_HOVER) : ThemeManager.getColor(ThemeColor.BG_PANEL)); }
            public void mouseClicked(MouseEvent e)
            {
                ThemeManager.setTheme(theme);
                popup.setVisible(false);
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

        Color base = (hover || open)
            ? ThemeManager.getColor(ThemeColor.BG_PANEL_HOVER)
            : ThemeManager.getColor(ThemeColor.BG_SIDEBAR);
        g2.setColor(base);
        g2.fillRoundRect(0, 0, w, h, UITheme.RADIUS_BUTTON, UITheme.RADIUS_BUTTON);

        g2.setColor(ThemeManager.getColor(ThemeColor.BORDER));
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawRoundRect(0, 0, w - 1, h - 1, UITheme.RADIUS_BUTTON, UITheme.RADIUS_BUTTON);

        Theme current = ThemeManager.getCurrentTheme();
        int swatchSize = 22;
        int sx = 12;
        int sy = (h - swatchSize) / 2;
        LinearGradientPaint gradient = new LinearGradientPaint(
            sx, sy, sx + swatchSize, sy + swatchSize,
            new float[] {0f, 1f}, new Color[] {current.accentGradientStart(), current.accentGradientEnd()});
        g2.setPaint(gradient);
        g2.fillRoundRect(sx, sy, swatchSize, swatchSize, 6, 6);

        g2.setColor(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        g2.setFont(UITheme.FONT_BODY);
        int textY = h / 2 + g2.getFontMetrics().getAscent() / 2 - 2;
        g2.drawString(current.getName(), sx + swatchSize + 10, textY);

        // Chevron
        g2.setColor(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int cx = w - 20;
        int cy = h / 2 - 2;
        g2.drawLine(cx - 4, cy, cx, cy + 5);
        g2.drawLine(cx, cy + 5, cx + 4, cy);

        g2.dispose();
        super.paintComponent(g);
    }
}
