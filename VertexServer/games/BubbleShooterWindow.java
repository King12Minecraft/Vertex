package games;

import net.Message;
import net.MessageType;
import net.NetworkManager;
import theme.GlitchEffectOverlay;
import theme.SignatureOverlay;
import theme.ThemeColor;
import theme.ThemeManager;
import theme.UITheme;
import ui.ThemedButton;

import javax.swing.AbstractAction;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

/**
 * BubbleShooterWindow
 * ---------------------
 * Offline - BubbleShooterGame runs entirely client-side. Move the mouse
 * to aim, click (or Space) to fire; Left/Right arrows also nudge the aim
 * for keyboard-only play. A fixed-rate Swing Timer drives the flying
 * bubble's tick and the repaint, ~60fps. Reports the final score once on
 * win or game-over, same GAME_PLAYED_REQUEST pattern as the other
 * single-player games.
 */
public class BubbleShooterWindow extends JFrame
{
    private static final int TICK_MS = 16;

    private static final Color[] BUBBLE_COLORS = new Color[]
    {
        new Color(230, 90, 90),
        new Color(90, 170, 230),
        new Color(120, 210, 110),
        new Color(235, 210, 80),
        new Color(190, 120, 230)
    };

    private BubbleShooterGame game;
    private BoardPanel boardPanel;
    private JLabel statusLabel;
    private Timer timer;
    private boolean reported;

    public BubbleShooterWindow()
    {
        super("Vertex - Bubble Shooter");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);
        setIconImage(GameLogo.renderIcon(64));

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(ThemeManager.getColor(ThemeColor.BG_APP));
        root.setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.setBorder(new EmptyBorder(0, 0, 12, 0));

        statusLabel = new JLabel("Aim with the mouse, click or Space to fire. Clear every bubble.");
        statusLabel.setFont(UITheme.FONT_NAV_BOLD);
        statusLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        topRow.add(statusLabel, BorderLayout.WEST);

        ThemedButton restart = new ThemedButton("New Game", false);
        restart.setPreferredSize(new Dimension(100, 34));
        restart.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { startNewGame(); }
        });
        JPanel restartWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        restartWrap.setOpaque(false);
        restartWrap.add(restart);
        topRow.add(restartWrap, BorderLayout.EAST);

        root.add(topRow, BorderLayout.NORTH);

        boardPanel = new BoardPanel();
        root.add(boardPanel, BorderLayout.CENTER);

        getContentPane().add(root);
        startNewGame();
        pack();
        setLocationRelativeTo(null);
        SignatureOverlay.attach(this);
        GlitchEffectOverlay.attach(this);
    }

    private void startNewGame()
    {
        if (timer != null)
        {
            timer.stop();
        }
        game = new BubbleShooterGame();
        reported = false;
        updateStatus();

        timer = new Timer(TICK_MS, new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                game.tick();
                updateStatus();
                boardPanel.repaint();
                if (game.isOver())
                {
                    timer.stop();
                    finishGame();
                }
            }
        });
        timer.start();
        boardPanel.requestFocusInWindow();
        boardPanel.repaint();
    }

    private void updateStatus()
    {
        statusLabel.setText("Score: " + game.getScore());
    }

    private void finishGame()
    {
        int finalScore = game.getScore();
        boolean won = game.isWon();
        statusLabel.setText((won ? "Board cleared! " : "Overrun! ") + "Final score: " + finalScore);
        if (!reported)
        {
            reported = true;
            reportScore(finalScore);
        }

        SnakeGameOverDialog.show(this, finalScore,
            won ? "You cleared every bubble on the board." : "The bubbles got too close to the shooter.",
            finalScore >= 300 ? "I scored " + finalScore + " points in Bubble Shooter on Vertex!" : null,
            new SnakeGameOverDialog.Choice()
            {
                public void onPlayAgain() { startNewGame(); }
                public void onClose() { BubbleShooterWindow.this.dispose(); }
            });
    }

    private void reportScore(final int finalScore)
    {
        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.GAME_PLAYED_REQUEST);
                request.setGameId("bubble-shooter");
                request.setScore(finalScore);
                NetworkManager.sendAsync(request);
            }
        });
        worker.start();
    }

    private class BoardPanel extends JPanel
    {
        BoardPanel()
        {
            setPreferredSize(new Dimension(BubbleShooterGame.BOARD_WIDTH, BubbleShooterGame.BOARD_HEIGHT));
            setFocusable(true);
            setBackground(new Color(16, 16, 24));
            bindKeys();

            addMouseMotionListener(new MouseMotionAdapter()
            {
                public void mouseMoved(MouseEvent e) { aimAt(e.getX(), e.getY()); }
            });
            addMouseListener(new MouseAdapter()
            {
                public void mousePressed(MouseEvent e)
                {
                    aimAt(e.getX(), e.getY());
                    game.shoot();
                }
            });
        }

        private void aimAt(int mouseX, int mouseY)
        {
            double dx = mouseX - game.getShooterX();
            double dy = game.getShooterY() - mouseY;
            if (dy <= 0) dy = 0.001;
            double deg = Math.toDegrees(Math.atan2(dx, dy));
            game.setAimAngle(deg);
            repaint();
        }

        private void bindKeys()
        {
            getInputMap().put(KeyStroke.getKeyStroke("LEFT"), "aimLeft");
            getActionMap().put("aimLeft", new AbstractAction()
            {
                public void actionPerformed(ActionEvent e) { game.adjustAim(-4); repaint(); }
            });
            getInputMap().put(KeyStroke.getKeyStroke("RIGHT"), "aimRight");
            getActionMap().put("aimRight", new AbstractAction()
            {
                public void actionPerformed(ActionEvent e) { game.adjustAim(4); repaint(); }
            });
            getInputMap().put(KeyStroke.getKeyStroke("SPACE"), "shoot");
            getActionMap().put("shoot", new AbstractAction()
            {
                public void actionPerformed(ActionEvent e) { game.shoot(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.applyAntialiasing(g2);

            g2.setColor(new Color(16, 16, 24));
            g2.fillRect(0, 0, getWidth(), getHeight());

            paintGrid(g2);
            paintAimGuide(g2);
            paintFlyingBubble(g2);
            paintShooter(g2);

            g2.dispose();
        }

        private void paintGrid(Graphics2D g2)
        {
            for (int r = 0; r < BubbleShooterGame.MAX_ROWS; r++)
            {
                for (int c = 0; c < BubbleShooterGame.COLS; c++)
                {
                    int color = game.colorAt(r, c);
                    if (color < 0) continue;
                    int x = (int) Math.round(game.xCenter(r, c));
                    int y = (int) Math.round(game.yCenter(r));
                    drawBubble(g2, x, y, BUBBLE_COLORS[color]);
                }
            }
        }

        private void paintAimGuide(Graphics2D g2)
        {
            double rad = Math.toRadians(game.getAimAngleDeg());
            double dx = Math.sin(rad), dy = -Math.cos(rad);
            int x1 = (int) Math.round(game.getShooterX());
            int y1 = (int) Math.round(game.getShooterY());
            int x2 = (int) Math.round(game.getShooterX() + dx * 90);
            int y2 = (int) Math.round(game.getShooterY() + dy * 90);
            g2.setColor(new Color(255, 255, 255, 90));
            g2.drawLine(x1, y1, x2, y2);
        }

        private void paintFlyingBubble(Graphics2D g2)
        {
            if (!game.isInFlight()) return;
            int x = (int) Math.round(game.getFlyingX());
            int y = (int) Math.round(game.getFlyingY());
            drawBubble(g2, x, y, BUBBLE_COLORS[game.getFlyingColor()]);
        }

        private void paintShooter(Graphics2D g2)
        {
            int x = (int) Math.round(game.getShooterX());
            int y = (int) Math.round(game.getShooterY());
            g2.setColor(new Color(80, 80, 95));
            g2.fillRoundRect(x - 26, y - 10, 52, 20, 8, 8);
            drawBubble(g2, x, y, BUBBLE_COLORS[game.getCurrentColor()]);

            g2.setColor(new Color(200, 200, 210));
            g2.drawString("Next:", x + 40, y - 20);
            drawBubble(g2, x + 78, y - 12, BUBBLE_COLORS[game.getNextColor()]);
        }

        private void drawBubble(Graphics2D g2, int cx, int cy, Color color)
        {
            int r = BubbleShooterGame.BUBBLE_RADIUS;
            g2.setColor(color);
            g2.fillOval(cx - r, cy - r, r * 2, r * 2);
            g2.setColor(color.darker());
            g2.drawOval(cx - r, cy - r, r * 2, r * 2);
        }
    }
}
