package pages;

import theme.ThemeColor;
import theme.ThemeManager;
import theme.UITheme;
import ui.ThemedLabel;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * PickerItem
 * ----------
 * One row in a "pick one from a list" column (the game list on Leaderboards, the board list on Forums): the
 * label, an optional small tag on the right, an accent bar and tint while selected, a hover tint otherwise.
 * Colours come from the theme. The owner calls setSelected(...) as the selection changes.
 */
public class PickerItem extends JPanel
{
    private boolean selected;
    private boolean hover;

    public PickerItem(String text, String tag, final Runnable onClick)
    {
        setOpaque(false);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(7, 12, 7, 10));
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setMaximumSize(new Dimension(4000, 34));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        JLabel label = new ThemedLabel(text, ThemeColor.TEXT_PRIMARY);
        label.setFont(UITheme.FONT_BODY);
        add(label, BorderLayout.CENTER);
        if (tag != null)
        {
            JLabel tagLabel = new ThemedLabel(tag, ThemeColor.ACCENT);
            tagLabel.setFont(UITheme.FONT_SMALL.deriveFont(10f));
            add(tagLabel, BorderLayout.EAST);
        }
        addMouseListener(new MouseAdapter()
        {
            public void mouseClicked(MouseEvent e) { onClick.run(); }
            public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
            public void mouseExited(MouseEvent e) { hover = false; repaint(); }
        });
    }

    public void setSelected(boolean selected)
    {
        this.selected = selected;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        if (selected || hover)
        {
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.applyAntialiasing(g2);
            Color c = ThemeManager.getColor(selected ? ThemeColor.ACCENT : ThemeColor.BG_PANEL_HOVER);
            g2.setColor(selected ? new Color(c.getRed(), c.getGreen(), c.getBlue(), 40) : c);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            if (selected)
            {
                g2.setColor(ThemeManager.getColor(ThemeColor.ACCENT));
                g2.fillRoundRect(0, 6, 3, getHeight() - 12, 3, 3);
            }
            g2.dispose();
        }
        super.paintComponent(g);
    }
}
