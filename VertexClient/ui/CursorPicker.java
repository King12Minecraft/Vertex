package ui;

import theme.ThemeColor;
import theme.ThemeManager;
import theme.UITheme;
import ui.CursorArtwork.CursorSet;
import ui.CursorArtwork.Role;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * CursorPicker
 * ------------
 * The Settings control for the mouse cursor: one card per cursor set, each showing that set's
 * arrow, link pointer and text cursor drawn large, so what you pick is what you'll get. Click
 * a card to switch instantly (CursorManager applies it everywhere). "System default" shows the
 * ordinary arrow; "Match my theme" redraws itself when the app theme changes.
 */
public class CursorPicker extends JPanel
{
    private static final int CARD_W = 132;
    private static final int CARD_H = 92;
    private static final int PREVIEW = 30;

    public CursorPicker()
    {
        super(new GridLayout(0, 3, 10, 10));
        setOpaque(false);
        setMaximumSize(new Dimension(3 * CARD_W + 20, 2 * CARD_H + 10));
        for (CursorSet set : CursorSet.values())
        {
            add(new Card(set));
        }
        ThemeManager.addListener(new Runnable()
        {
            public void run() { repaint(); }
        });
    }

    private final class Card extends JComponent
    {
        private final CursorSet set;
        private boolean hover = false;

        Card(CursorSet set)
        {
            this.set = set;
            setPreferredSize(new Dimension(CARD_W, CARD_H));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter()
            {
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                public void mouseClicked(MouseEvent e)
                {
                    CursorManager.setSet(Card.this.set);
                    CursorPicker.this.repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.applyAntialiasing(g2);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            boolean selected = CursorManager.getSet() == set;
            g2.setColor(ThemeManager.getColor(hover ? ThemeColor.BG_PANEL_HOVER : ThemeColor.BG_APP));
            g2.fill(new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, 14, 14));
            g2.setColor(ThemeManager.getColor(selected ? ThemeColor.ACCENT : ThemeColor.BORDER));
            g2.setStroke(new BasicStroke(selected ? 2.4f : 1f));
            g2.draw(new RoundRectangle2D.Double(1, 1, getWidth() - 3, getHeight() - 3, 14, 14));

            int top = 12;
            if (set == CursorSet.SYSTEM)
            {
                drawSystemPreview(g2, top);
            }
            else
            {
                int x = (getWidth() - (3 * PREVIEW + 2 * 6)) / 2;
                Role[] roles = { Role.ARROW, Role.LINK, Role.TEXT };
                for (int i = 0; i < roles.length; i++)
                {
                    g2.drawImage(CursorArtwork.draw(set, roles[i], PREVIEW * 2), x + i * (PREVIEW + 6), top, PREVIEW, PREVIEW, null);
                }
            }

            g2.setFont(UITheme.FONT_SMALL);
            g2.setColor(ThemeManager.getColor(selected ? ThemeColor.TEXT_PRIMARY : ThemeColor.TEXT_SECONDARY));
            String label = set.getLabel();
            int w = g2.getFontMetrics().stringWidth(label);
            g2.drawString(label, (getWidth() - w) / 2, getHeight() - 14);
            g2.dispose();
        }

        /** A plain arrow and hand silhouette in the theme's text colour, standing in for "whatever your system uses". */
        private void drawSystemPreview(Graphics2D g2, int top)
        {
            g2.setColor(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
            g2.setFont(UITheme.FONT_SMALL);
            String note = "your system's own";
            int w = g2.getFontMetrics().stringWidth(note);
            g2.drawString(note, (getWidth() - w) / 2, top + 18);
        }
    }
}
