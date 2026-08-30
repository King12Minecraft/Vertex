import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;

/**
 * GameCardArt
 * -----------
 * The gamified art header for a game card: a gradient-filled chamfered
 * panel (matching the button/logo angular treatment) with a real
 * hand-drawn icon for the specific game - all Graphics2D shapes, no
 * external image files anywhere, same principle as GameLogo. Unknown
 * gameIds fall back to a generic controller-ish shape rather than
 * failing to render anything.
 */
public class GameCardArt extends JPanel
{
    private final String gameId;

    public GameCardArt(String gameId)
    {
        this.gameId = gameId == null ? "" : gameId;
        setOpaque(false);

        ThemeManager.addListener(new Runnable()
        {
            public void run() { repaint(); }
        });
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        UITheme.applyAntialiasing(g2);

        int w = getWidth();
        int h = getHeight();
        int cut = Math.min(16, h / 4);

        GeneralPath shape = ChamferShape.build(0, 0, w, h, cut);

        Color start = ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_START);
        Color end = ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_END);
        LinearGradientPaint gradient = new LinearGradientPaint(
            0, 0, Math.max(w, 1), Math.max(h, 1), new float[] {0f, 1f}, new Color[] {start, end});
        g2.setPaint(gradient);
        g2.fill(shape);

        g2.setColor(new Color(255, 255, 255, 210));
        g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        drawIcon(g2, w, h);

        g2.dispose();
    }

    private void drawIcon(Graphics2D g2, int w, int h)
    {
        paintIconOnly(g2, w, h, gameId);
    }

    /**
     * Static entry point so other components (e.g. HeroBanner) can
     * reuse the same per-game icon vocabulary at a different scale,
     * without needing a GameCardArt instance or its gradient
     * background - just the icon strokes themselves.
     */
    public static void paintIconOnly(Graphics2D g2, int w, int h, String gameId)
    {
        String id = gameId == null ? "" : gameId;
        if ("snake".equals(id))
        {
            drawSnakeIcon(g2, w, h);
        }
        else if ("tictactoe-online".equals(id))
        {
            drawTicTacToeIcon(g2, w, h);
        }
        else if ("square-wars".equals(id))
        {
            drawSquareWarsIcon(g2, w, h);
        }
        else if ("racing".equals(id))
        {
            drawRacingIcon(g2, w, h);
        }
        else if ("puzzle-quest".equals(id))
        {
            drawPuzzleIcon(g2, w, h);
        }
        else if ("zombie-survival".equals(id))
        {
            drawZombieIcon(g2, w, h);
        }
        else if ("space-battle".equals(id))
        {
            drawSpaceIcon(g2, w, h);
        }
        else
        {
            drawGenericIcon(g2, w, h);
        }
    }

    /** A simple curled snake body with a dot for the head. */
    private static void drawSnakeIcon(Graphics2D g2, int w, int h)
    {
        int cx = w / 2;
        int cy = h / 2;
        int r = Math.min(w, h) / 5;

        GeneralPath path = new GeneralPath();
        path.moveTo(cx - r * 2.2, cy + r * 0.8);
        path.curveTo(cx - r * 1.2, cy - r * 1.2, cx - r * 0.2, cy - r * 1.2, cx, cy);
        path.curveTo(cx + r * 0.6, cy + r * 0.9, cx + r * 1.4, cy + r * 0.9, cx + r * 1.8, cy - r * 0.4);
        g2.draw(path);

        int headSize = (int) (r * 0.9);
        g2.fill(new Ellipse2D.Double(cx + r * 1.8 - headSize / 2.0, cy - r * 0.4 - headSize / 2.0, headSize, headSize));
    }

    /** A 3x3 grid with an X and an O placed in it. */
    private static void drawTicTacToeIcon(Graphics2D g2, int w, int h)
    {
        int size = Math.min(w, h) - 20;
        int x0 = (w - size) / 2;
        int y0 = (h - size) / 2;
        int third = size / 3;

        g2.drawLine(x0 + third, y0, x0 + third, y0 + size);
        g2.drawLine(x0 + third * 2, y0, x0 + third * 2, y0 + size);
        g2.drawLine(x0, y0 + third, x0 + size, y0 + third);
        g2.drawLine(x0, y0 + third * 2, x0 + size, y0 + third * 2);

        int pad = third / 4;
        int cx1 = x0 + pad;
        int cy1 = y0 + pad;
        g2.drawLine(cx1, cy1, cx1 + third - pad * 2, cy1 + third - pad * 2);
        g2.drawLine(cx1 + third - pad * 2, cy1, cx1, cy1 + third - pad * 2);

        int ocx = x0 + third * 2 + third / 2;
        int ocy = y0 + third + third / 2;
        int rad = third / 2 - pad;
        g2.draw(new Ellipse2D.Double(ocx - rad, ocy - rad, rad * 2, rad * 2));
    }

    /** A few overlapping angular squares, suggesting a battle/versus arrangement. */
    private static void drawSquareWarsIcon(Graphics2D g2, int w, int h)
    {
        int cx = w / 2;
        int cy = h / 2;
        int size = Math.min(w, h) / 3;

        g2.drawRect(cx - size - size / 3, cy - size / 2, size, size);
        g2.drawRect(cx - size / 3, cy - size / 2 - size / 4, size, size);
        g2.drawRect(cx + size / 3, cy - size / 2 + size / 4, size, size);
    }

    /** A simple checkered flag on a pole. */
    private static void drawRacingIcon(Graphics2D g2, int w, int h)
    {
        int poleX = w / 2 - 24;
        int topY = h / 2 - 24;
        int flagW = 44;
        int flagH = 30;

        g2.drawLine(poleX, topY, poleX, topY + 48);

        int checks = 4;
        int cw = flagW / checks;
        int ch = flagH / 2;
        for (int row = 0; row < 2; row++)
        {
            for (int col = 0; col < checks; col++)
            {
                boolean filled = (row + col) % 2 == 0;
                if (filled)
                {
                    g2.fillRect(poleX + col * cw, topY + row * ch, cw, ch);
                }
                else
                {
                    g2.drawRect(poleX + col * cw, topY + row * ch, cw, ch);
                }
            }
        }
    }

    /** An interlocking two-piece puzzle silhouette. */
    private static void drawPuzzleIcon(Graphics2D g2, int w, int h)
    {
        int cx = w / 2;
        int cy = h / 2;
        int size = Math.min(w, h) / 3;
        int bump = size / 4;

        GeneralPath piece = new GeneralPath();
        piece.moveTo(cx - size, cy - size);
        piece.lineTo(cx, cy - size);
        piece.curveTo(cx + bump, cy - size - bump, cx + size - bump, cy - size - bump, cx + size, cy - size);
        piece.lineTo(cx + size, cy);
        piece.curveTo(cx + size + bump, cy + bump, cx + size + bump, cy + size - bump, cx + size, cy + size);
        piece.lineTo(cx - size, cy + size);
        piece.closePath();
        g2.draw(piece);
    }

    /** A simple stitched-up warning-style face: two X eyes, a flat mouth line. */
    private static void drawZombieIcon(Graphics2D g2, int w, int h)
    {
        int cx = w / 2;
        int cy = h / 2;
        int r = Math.min(w, h) / 5;

        g2.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));

        int eyeSize = r / 3;
        int eyeOffsetX = r / 2;
        int eyeOffsetY = r / 4;
        drawX(g2, cx - eyeOffsetX, cy - eyeOffsetY, eyeSize);
        drawX(g2, cx + eyeOffsetX, cy - eyeOffsetY, eyeSize);

        g2.drawLine(cx - r / 2, cy + r / 2, cx + r / 2, cy + r / 2);
    }

    private static void drawX(Graphics2D g2, int cx, int cy, int size)
    {
        g2.drawLine(cx - size, cy - size, cx + size, cy + size);
        g2.drawLine(cx + size, cy - size, cx - size, cy + size);
    }

    /** A simple rocket - triangular nose, rectangular body, two fins. */
    private static void drawSpaceIcon(Graphics2D g2, int w, int h)
    {
        int cx = w / 2;
        int cy = h / 2;
        int bodyW = Math.min(w, h) / 6;
        int bodyH = (int) (bodyW * 2.6);

        GeneralPath rocket = new GeneralPath();
        rocket.moveTo(cx, cy - bodyH / 2 - bodyW);
        rocket.lineTo(cx - bodyW / 2, cy - bodyH / 2);
        rocket.lineTo(cx - bodyW / 2, cy + bodyH / 2);
        rocket.lineTo(cx - bodyW, cy + bodyH / 2 + bodyW / 2);
        rocket.moveTo(cx - bodyW / 2, cy + bodyH / 2);
        rocket.lineTo(cx + bodyW / 2, cy + bodyH / 2);
        rocket.lineTo(cx + bodyW, cy + bodyH / 2 + bodyW / 2);
        rocket.moveTo(cx + bodyW / 2, cy + bodyH / 2);
        rocket.lineTo(cx + bodyW / 2, cy - bodyH / 2);
        rocket.lineTo(cx, cy - bodyH / 2 - bodyW);
        g2.draw(rocket);

        g2.draw(new Ellipse2D.Double(cx - bodyW / 5.0, cy - bodyW / 5.0, bodyW / 2.5, bodyW / 2.5));
    }

    /** Fallback for any game without a dedicated icon yet - a simple d-pad-like cross, echoing the logo mark. */
    private static void drawGenericIcon(Graphics2D g2, int w, int h)
    {
        int cx = w / 2;
        int cy = h / 2;
        int arm = Math.min(w, h) / 5;
        int thickness = arm / 2;

        g2.drawRoundRect(cx - arm, cy - thickness / 2, arm * 2, thickness, 6, 6);
        g2.drawRoundRect(cx - thickness / 2, cy - arm, thickness, arm * 2, 6, 6);
    }
}
