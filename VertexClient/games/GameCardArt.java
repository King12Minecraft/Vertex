package games;
import theme.ThemeColor;
import ui.ChamferShape;
import theme.UITheme;
import theme.ThemeManager;

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

        // each game gets its own slight hue shift of the theme gradient, so neighbouring cards are not one flat colour
        int shift = (Math.abs(gameId.hashCode() / 7) % 7 - 3) * 6;
        Color start = shiftHue(ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_START), shift);
        Color end = shiftHue(ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_END), shift);
        LinearGradientPaint gradient = new LinearGradientPaint(
            0, 0, Math.max(w, 1), Math.max(h, 1), new float[] {0f, 1f}, new Color[] {start, end});
        g2.setPaint(gradient);
        g2.fill(shape);

        g2.setColor(new Color(255, 255, 255, 210));
        g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        g2.clip(shape);   // patterns must not spill past the chamfered corners
        drawIcon(g2, w, h);

        g2.dispose();
    }

    private static Color shiftHue(Color c, int degrees)
    {
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        float hue = (hsb[0] + degrees / 360f + 1f) % 1f;
        return new Color(Color.HSBtoRGB(hue, hsb[1], hsb[2]));
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
        else if ("caption-chaos".equals(id))
        {
            drawCaptionIcon(g2, w, h);
        }
        else
        {
            drawMonogramIcon(g2, w, h, id);
        }
    }

    /** Two overlapping speech bubbles with a few "typed" lines - the prompt and the answers. */
    private static void drawCaptionIcon(Graphics2D g2, int w, int h)
    {
        int cx = w / 2;
        int cy = h / 2;
        int u = Math.min(w, h) / 6;
        g2.setStroke(new BasicStroke(Math.max(2f, u / 5f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // back bubble
        g2.drawRoundRect(cx - u * 3, cy - u * 2, u * 3 + u / 2, u * 2 + u / 2, u / 2, u / 2);
        // front bubble with a tail
        int fx = cx - u / 2;
        int fy = cy - u / 2;
        int fw = u * 3 + u / 2;
        int fh = u * 2 + u / 2;
        g2.drawRoundRect(fx, fy, fw, fh, u / 2, u / 2);
        GeneralPath tail = new GeneralPath();
        tail.moveTo(fx + u, fy + fh);
        tail.lineTo(fx + u / 2, fy + fh + u * 0.8);
        tail.lineTo(fx + u * 1.8, fy + fh);
        g2.draw(tail);
        // text lines in the front bubble
        g2.drawLine(fx + u / 2 + u / 4, fy + u * 3 / 4, fx + fw - u / 2, fy + u * 3 / 4);
        g2.drawLine(fx + u / 2 + u / 4, fy + u * 3 / 2, fx + fw - u * 3 / 2, fy + u * 3 / 2);
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
    /**
     * The art for every game without a hand-drawn icon: its monogram ("Ch", "TT", "2048") over a faint pattern picked by
     * the game id. Before this all ~45 of them showed the same "+", so the catalogue read as one repeated tile; now each
     * game is recognisable at a glance and neighbouring cards differ. Colours still come from the theme (the gradient
     * behind and white strokes on top), so it follows whichever palette is active.
     */
    private static void drawMonogramIcon(Graphics2D g2, int w, int h, String id)
    {
        int seed = Math.abs(id.hashCode());
        java.awt.Stroke oldStroke = g2.getStroke();
        g2.setColor(new Color(255, 255, 255, 38));
        g2.setStroke(new BasicStroke(Math.max(1.5f, h / 60f)));
        int step = Math.max(12, h / 7);
        switch (seed % 6)
        {
            case 0:   // diagonal stripes
                for (int x = -h; x < w + h; x += step) g2.drawLine(x, h, x + h, 0);
                break;
            case 1:   // rings from the bottom-right corner
                for (int r = step * 2; r < w + h; r += step * 2) g2.drawOval(w - r, h - r, r * 2, r * 2);
                break;
            case 2:   // dot grid
                for (int x = step / 2; x < w; x += step) for (int y = step / 2; y < h; y += step) g2.fillOval(x - 2, y - 2, 5, 5);
                break;
            case 3:   // zigzag rows
                for (int y = step; y < h; y += step * 2)
                {
                    int px = 0;
                    int py = y;
                    for (int x = step; x <= w + step; x += step)
                    {
                        int ny = (py == y) ? y - step / 2 : y;
                        g2.drawLine(px, py, x, ny);
                        px = x;
                        py = ny;
                    }
                }
                break;
            case 4:   // two big rings
                g2.drawOval(-h / 3, -h / 3, h, h);
                g2.drawOval(w - h * 2 / 3, h / 3, h, h);
                break;
            default:  // crosshatch squares
                for (int x = 0; x < w; x += step * 2) for (int y = 0; y < h; y += step * 2) g2.drawRect(x, y, step, step);
                break;
        }
        g2.setStroke(oldStroke);

        String text = monogram(id);
        float size = Math.min(h * 0.46f, w * 0.78f / Math.max(1, text.length()) * 1.3f);
        g2.setFont(UITheme.FONT_HEADING.deriveFont(java.awt.Font.BOLD, Math.max(10f, size)));
        java.awt.FontMetrics fm = g2.getFontMetrics();
        int tx = (w - fm.stringWidth(text)) / 2;
        int ty = (h + fm.getAscent() - fm.getDescent()) / 2;
        g2.setColor(new Color(0, 0, 0, 40));
        g2.drawString(text, tx + 2, ty + 2);
        g2.setColor(new Color(255, 255, 255, 235));
        g2.drawString(text, tx, ty);
    }

    /** "chess" -> "Ch", "tictactoe-online" -> "TO"... uses the game's display name when it is known. */
    private static String monogram(String id)
    {
        GameInfo info = GameManager.findCachedGame(id);
        String name = info != null && info.getName() != null ? info.getName() : id.replace('-', ' ');
        String[] words = name.trim().split("[^A-Za-z0-9]+");
        java.util.List<String> parts = new java.util.ArrayList<String>();
        for (String word : words) if (!word.isEmpty()) parts.add(word);
        if (parts.isEmpty()) return "?";
        String first = parts.get(0);
        if (Character.isDigit(first.charAt(0)))
        {
            return first.length() > 4 ? first.substring(0, 4) : first;
        }
        if (parts.size() >= 2)
        {
            return ("" + Character.toUpperCase(first.charAt(0)) + Character.toUpperCase(parts.get(1).charAt(0)));
        }
        return Character.toUpperCase(first.charAt(0)) + (first.length() > 1 ? "" + Character.toLowerCase(first.charAt(1)) : "");
    }
}
