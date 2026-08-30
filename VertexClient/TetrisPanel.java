import javax.swing.AbstractAction;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * TetrisPanel
 * -----------
 * Renders TetrisGame's grid, the falling piece, and a next-piece
 * preview - entirely with Graphics2D shapes, same principle as every
 * other game in Vertex. Keyboard input uses Swing key bindings
 * (InputMap/ActionMap): Left/Right move, Up rotates, Down soft-drops,
 * Space hard-drops.
 */
public class TetrisPanel extends JPanel
{
    private static final int CELL = 24;
    private static final int SIDE_PANEL_WIDTH = 120;

    private static final Color[] PIECE_COLORS = {
        new Color(80, 200, 220),
        new Color(230, 200, 60),
        new Color(170, 100, 220),
        new Color(100, 200, 100),
        new Color(230, 90, 90),
        new Color(90, 120, 220),
        new Color(230, 150, 70)
    };

    private final TetrisGame game;
    private final Runnable onGameOver;
    private Timer timer;

    public TetrisPanel(TetrisGame game, Runnable onGameOver)
    {
        this.game = game;
        this.onGameOver = onGameOver;
        setPreferredSize(new Dimension(TetrisGame.COLS * CELL + SIDE_PANEL_WIDTH, TetrisGame.ROWS * CELL));
        setFocusable(true);
        bindKeys();

        ThemeManager.addListener(new Runnable()
        {
            public void run() { repaint(); }
        });
    }

    private void bindKeys()
    {
        bindKey("LEFT", "moveLeft");
        bindKey("RIGHT", "moveRight");
        bindKey("UP", "rotate");
        bindKey("DOWN", "softDrop");
        bindKey("SPACE", "hardDrop");
    }

    private void bindKey(String keyName, final String action)
    {
        getInputMap().put(KeyStroke.getKeyStroke(keyName), action);
        getActionMap().put(action, new AbstractAction()
        {
            public void actionPerformed(ActionEvent e)
            {
                if ("moveLeft".equals(action)) game.moveLeft();
                else if ("moveRight".equals(action)) game.moveRight();
                else if ("rotate".equals(action)) game.rotate();
                else if ("softDrop".equals(action)) game.softDrop();
                else if ("hardDrop".equals(action)) game.hardDrop();
                repaint();
            }
        });
    }

    public void startTimer()
    {
        timer = new Timer(16, new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                game.tick();
                repaint();
                if (game.isGameOver())
                {
                    stopTimer();
                    onGameOver.run();
                }
            }
        });
        timer.start();
    }

    public void stopTimer()
    {
        if (timer != null) timer.stop();
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        UITheme.applyAntialiasing(g2);

        int boardW = TetrisGame.COLS * CELL;
        int boardH = TetrisGame.ROWS * CELL;

        g2.setColor(ThemeManager.getColor(ThemeColor.BG_SIDEBAR));
        g2.fillRect(0, 0, boardW, boardH);

        g2.setColor(ThemeManager.getColor(ThemeColor.BORDER));
        for (int c = 0; c <= TetrisGame.COLS; c++)
        {
            g2.drawLine(c * CELL, 0, c * CELL, boardH);
        }
        for (int r = 0; r <= TetrisGame.ROWS; r++)
        {
            g2.drawLine(0, r * CELL, boardW, r * CELL);
        }

        int[][] grid = game.getGrid();
        for (int r = 0; r < TetrisGame.ROWS; r++)
        {
            for (int c = 0; c < TetrisGame.COLS; c++)
            {
                if (grid[r][c] != 0)
                {
                    drawCell(g2, c * CELL, r * CELL, PIECE_COLORS[grid[r][c] - 1]);
                }
            }
        }

        int[][] cells = game.getCurrentCells();
        Color currentColor = PIECE_COLORS[game.getCurrentType()];
        for (int i = 0; i < cells.length; i++)
        {
            int r = game.getCurrentY() + cells[i][0];
            int c = game.getCurrentX() + cells[i][1];
            if (r >= 0)
            {
                drawCell(g2, c * CELL, r * CELL, currentColor);
            }
        }

        g2.setColor(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        g2.setFont(UITheme.FONT_NAV_BOLD);
        g2.drawString("Score", boardW + 16, 30);
        g2.drawString(String.valueOf(game.getScore()), boardW + 16, 52);
        g2.drawString("Lines", boardW + 16, 90);
        g2.drawString(String.valueOf(game.getLinesCleared()), boardW + 16, 112);
        g2.drawString("Next", boardW + 16, 150);

        int[][] nextCells = TetrisGame.shapeFor(game.getNextType(), 0);
        Color nextColor = PIECE_COLORS[game.getNextType()];
        int previewX = boardW + 20;
        int previewY = 165;
        for (int i = 0; i < nextCells.length; i++)
        {
            int x = previewX + nextCells[i][1] * CELL;
            int y = previewY + nextCells[i][0] * CELL;
            drawCell(g2, x, y, nextColor);
        }

        g2.dispose();
    }

    private void drawCell(Graphics2D g2, int x, int y, Color color)
    {
        g2.setColor(color);
        g2.fillRoundRect(x + 1, y + 1, CELL - 2, CELL - 2, 4, 4);
    }
}
