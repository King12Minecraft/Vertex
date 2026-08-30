import javax.swing.JButton;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * PuzzleTileButton
 * ----------------
 * One tile of the sliding puzzle - a numbered rounded square, or blank
 * (invisible/empty) for value 0. Custom-painted, same visual language
 * as the rest of Vertex's game boards (TicTacToeCellButton, etc).
 */
public class PuzzleTileButton extends JButton
{
    private int value = 0;
    private boolean hover = false;

    public PuzzleTileButton()
    {
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);

        addMouseListener(new MouseAdapter()
        {
            public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
            public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
        });

        ThemeManager.addListener(new Runnable()
        {
            public void run() { repaint(); }
        });
    }

    public void setValue(int value)
    {
        this.value = value;
        setCursor(value == 0 ? Cursor.getDefaultCursor() : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        repaint();
    }

    public int getValue()
    {
        return value;
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        if (value == 0)
        {
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        UITheme.applyAntialiasing(g2);

        int w = getWidth();
        int h = getHeight();

        Color base = hover ? ThemeManager.getColor(ThemeColor.BG_PANEL_HOVER) : ThemeManager.getColor(ThemeColor.BG_PANEL);
        g2.setColor(base);
        g2.fillRoundRect(0, 0, w, h, 10, 10);

        g2.setColor(ThemeManager.getColor(ThemeColor.BORDER));
        g2.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);

        g2.setColor(ThemeManager.getColor(ThemeColor.ACCENT));
        g2.setFont(UITheme.FONT_NAV_BOLD.deriveFont(20f));
        String text = String.valueOf(value);
        FontMetrics fm = g2.getFontMetrics();
        int textX = (w - fm.stringWidth(text)) / 2;
        int textY = (h - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(text, textX, textY);

        g2.dispose();
    }
}
