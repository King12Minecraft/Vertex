import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;

/**
 * WinLineOverlay
 * --------------
 * A transparent panel that sits directly on top of the Tic-Tac-Toe
 * grid (same bounds, higher layer in a JLayeredPane) and draws an
 * actual strike-through line through the three winning cells - a real
 * geometric line connecting their centers, not just a highlighted
 * border on each cell. Reads each cell's live on-screen bounds
 * directly, so it stays correct regardless of window size.
 */
public class WinLineOverlay extends JPanel
{
    private final TicTacToeCellButton[] cells;
    private int[] winningLine;

    public WinLineOverlay(TicTacToeCellButton[] cells)
    {
        this.cells = cells;
        setOpaque(false);
    }

    public void setWinningLine(int[] line)
    {
        this.winningLine = line;
        repaint();
    }

    public void clear()
    {
        this.winningLine = null;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        if (winningLine == null || winningLine.length != 3)
        {
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        UITheme.applyAntialiasing(g2);

        Point start = centerOf(cells[winningLine[0]]);
        Point end = centerOf(cells[winningLine[2]]);

        Color accent = ThemeManager.getColor(ThemeColor.ACCENT);

        // A soft glow pass beneath the solid line, matching the app's glow language elsewhere.
        g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 90));
        g2.setStroke(new BasicStroke(12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(start.x, start.y, end.x, end.y);

        g2.setColor(accent);
        g2.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(start.x, start.y, end.x, end.y);

        g2.dispose();
    }

    private Point centerOf(Component c)
    {
        Rectangle b = c.getBounds();
        return new Point(b.x + b.width / 2, b.y + b.height / 2);
    }
}
