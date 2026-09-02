import javax.swing.JButton;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * TicTacToeCellButton
 * --------------------
 * One cell of the Tic-Tac-Toe board. Custom-painted (not relying on
 * JButton text) so the X/O can animate in with a smooth scale-up
 * reveal instead of just appearing instantly - reuses
 * HoverGlowAnimator's fade mechanics for the 0-1 reveal progress.
 */
public class TicTacToeCellButton extends JButton
{
    private char value = ' ';
    private boolean hover = false;
    private boolean highlighted = false;
    private final HoverGlowAnimator reveal = new HoverGlowAnimator(this);

    public TicTacToeCellButton()
    {
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

        ThemeManager.addListener(new Runnable()
        {
            public void run() { repaint(); }
        });
    }

    /** Sets the cell's mark ('X', 'O', or ' ' for empty). Animates a reveal the first time it's filled. */
    public void setValue(char newValue)
    {
        boolean wasEmpty = (value != 'X' && value != 'O');
        boolean nowFilled = (newValue == 'X' || newValue == 'O');
        value = newValue;
        if (wasEmpty && nowFilled)
        {
            reveal.animateIn();
        }
        repaint();
    }

    public char getValue()
    {
        return value;
    }

    /** Marks this cell as part of the winning line - draws a glowing highlight. */
    public void setHighlighted(boolean highlighted)
    {
        this.highlighted = highlighted;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        UITheme.applyAntialiasing(g2);

        int w = getWidth();
        int h = getHeight();

        Color base = (hover && isEnabled())
            ? ThemeManager.getColor(ThemeColor.BG_PANEL_HOVER)
            : ThemeManager.getColor(ThemeColor.BG_SIDEBAR);
        g2.setColor(base);
        g2.fillRoundRect(0, 0, w, h, 12, 12);

        if (highlighted)
        {
            Color glow = ThemeManager.getColor(ThemeColor.ACCENT);
            g2.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 60));
            g2.fillRoundRect(0, 0, w, h, 12, 12);
            g2.setColor(glow);
            g2.setStroke(new BasicStroke(2.5f));
            g2.drawRoundRect(1, 1, w - 3, h - 3, 12, 12);
        }
        else
        {
            g2.setColor(ThemeManager.getColor(ThemeColor.BORDER));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 12, 12);
        }

        if (value == 'X' || value == 'O')
        {
            paintMark(g2, w, h, value, reveal.getIntensity());
        }

        g2.dispose();
    }

    private void paintMark(Graphics2D g2, int w, int h, char mark, float scale)
    {
        if (scale <= 0f)
        {
            return;
        }
        int cx = w / 2;
        int cy = h / 2;
        int size = (int) (Math.min(w, h) * 0.5f * scale);
        if (size <= 0)
        {
            return;
        }

        Color start = ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_START);
        Color end = ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_END);
        Color color = (mark == 'X') ? start : end;

        g2.setColor(color);
        g2.setStroke(new BasicStroke(Math.max(3f, size * 0.16f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        if (mark == 'X')
        {
            g2.drawLine(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);
            g2.drawLine(cx + size / 2, cy - size / 2, cx - size / 2, cy + size / 2);
        }
        else
        {
            g2.drawOval(cx - size / 2, cy - size / 2, size, size);
        }
    }
}
