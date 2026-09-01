import javax.swing.AbstractAction;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * DinoWindow
 * ----------
 * Standalone window for the Chrome Dino runner. Spacebar (or Up) to
 * jump. Single-player, fully offline, same recordPlayed pattern as
 * Racing (no coin reward).
 */
public class DinoWindow extends JFrame
{
    private RunPanel runPanel;

    public DinoWindow()
    {
        super("Vertex - Dino Dash");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);
        setIconImage(GameLogo.renderIcon(64));

        startGame();

        pack();
        setLocationRelativeTo(null);
        SignatureOverlay.attach(this);
        GlitchEffectOverlay.attach(this);
    }

    private void startGame()
    {
        final DinoGame game = new DinoGame();

        if (runPanel != null)
        {
            runPanel.stopTimer();
            getContentPane().remove(runPanel);
        }

        Runnable onGameOver = new Runnable()
        {
            public void run()
            {
                recordPlayed(game.getScore());
                SnakeGameOverDialog.show(runPanel, game.getScore(), new SnakeGameOverDialog.Choice()
                {
                    public void onPlayAgain() { startGame(); }
                    public void onClose() { DinoWindow.this.dispose(); }
                });
            }
        };

        runPanel = new RunPanel(game, onGameOver);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(runPanel, BorderLayout.CENTER);
        pack();
        setLocationRelativeTo(null);
        runPanel.requestFocusInWindow();
        runPanel.startTimer();
    }

    private void recordPlayed(int score)
    {
        if (!Session.isLoggedIn())
        {
            GuestPlayTracker.recordGuestPlay("dino-dash", score);
            return;
        }

        Message request = new Message();
        request.setType(MessageType.GAME_PLAYED_REQUEST);
        request.setGameId("dino-dash");
        request.setScore(score);
        NetworkManager.sendAsync(request);
    }

    private static class RunPanel extends JPanel
    {
        private final DinoGame game;
        private final Runnable onGameOver;
        private Timer timer;
        private boolean paused = false;

        RunPanel(DinoGame game, Runnable onGameOver)
        {
            this.game = game;
            this.onGameOver = onGameOver;
            setPreferredSize(new Dimension(DinoGame.WIDTH, DinoGame.HEIGHT));
            setFocusable(true);
            bindKeys();

            ThemeManager.addListener(new Runnable()
            {
                public void run() { repaint(); }
            });
        }

        private void bindKeys()
        {
            bind("SPACE");
            bind("UP");

            getInputMap().put(KeyStroke.getKeyStroke("P"), "pause_action");
            getActionMap().put("pause_action", new AbstractAction()
            {
                public void actionPerformed(ActionEvent e)
                {
                    if (!game.isGameOver())
                    {
                        paused = !paused;
                        repaint();
                    }
                }
            });
        }

        private void bind(String keyName)
        {
            getInputMap().put(KeyStroke.getKeyStroke(keyName), keyName + "_action");
            getActionMap().put(keyName + "_action", new AbstractAction()
            {
                public void actionPerformed(ActionEvent e) { if (!paused) game.jump(); }
            });
        }

        void startTimer()
        {
            timer = new Timer(16, new ActionListener()
            {
                public void actionPerformed(ActionEvent e)
                {
                    if (paused) return;
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

        void stopTimer()
        {
            if (timer != null) timer.stop();
        }

        /** The dino renders in the player's purchased username color if set, matching Racing/Snake/Pong's precedent. */
        private java.awt.Color playerColor()
        {
            if (Session.isLoggedIn())
            {
                java.awt.Color owned = PlayerColorRegistry.resolve(Session.getCurrentAccount().getPlayerColorName());
                if (owned != null)
                {
                    return owned;
                }
            }
            return ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_START);
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.applyAntialiasing(g2);

            int w = getWidth();

            g2.setColor(ThemeManager.getColor(ThemeColor.BG_SIDEBAR));
            g2.fillRect(0, 0, w, getHeight());

            g2.setColor(ThemeManager.getColor(ThemeColor.BORDER));
            g2.fillRect(0, DinoGame.GROUND_Y, w, 2);

            g2.setColor(playerColor());
            g2.fillRoundRect(DinoGame.DINO_X, game.getDinoY(), DinoGame.DINO_SIZE, DinoGame.DINO_SIZE, 6, 6);

            g2.setColor(new java.awt.Color(240, 100, 100));
            List<Integer> obstacles = game.getObstacleXs();
            for (int i = 0; i < obstacles.size(); i++)
            {
                g2.fillRect(obstacles.get(i), DinoGame.GROUND_Y - 26, 16, 26);
            }

            g2.setColor(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
            g2.setFont(UITheme.FONT_NAV_BOLD);
            g2.drawString("Score: " + game.getScore(), 12, 24);

            if (paused)
            {
                g2.setColor(new Color(0, 0, 0, 140));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(Color.WHITE);
                g2.setFont(UITheme.FONT_HEADING);
                String text = "PAUSED";
                int textWidth = g2.getFontMetrics().stringWidth(text);
                g2.drawString(text, (getWidth() - textWidth) / 2, getHeight() / 2);
                g2.setFont(UITheme.FONT_SMALL);
                String hint = "Press P to resume";
                int hintWidth = g2.getFontMetrics().stringWidth(hint);
                g2.drawString(hint, (getWidth() - hintWidth) / 2, getHeight() / 2 + 24);
            }

            g2.dispose();
        }
    }
}
