import javax.swing.JFrame;
import java.awt.BorderLayout;

/**
 * PongWindow
 * ----------
 * Standalone window for Ping Pong vs AI. Single-player, fully offline,
 * same recordPlayed pattern as Racing (no coin reward - only Snake has
 * that).
 */
public class PongWindow extends JFrame
{
    private PongPanel pongPanel;

    public PongWindow()
    {
        super("Vertex - Ping Pong");
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
        final PongGame game = new PongGame();

        if (pongPanel != null)
        {
            pongPanel.stopTimer();
            getContentPane().remove(pongPanel);
        }

        Runnable onGameOver = new Runnable()
        {
            public void run()
            {
                recordPlayed(game.getPlayerScore());
                SnakeGameOverDialog.show(pongPanel, game.getPlayerScore(), new SnakeGameOverDialog.Choice()
                {
                    public void onPlayAgain() { startGame(); }
                    public void onClose() { PongWindow.this.dispose(); }
                });
            }
        };

        pongPanel = new PongPanel(game, onGameOver);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(pongPanel, BorderLayout.CENTER);
        pack();
        setLocationRelativeTo(null);
        pongPanel.requestFocusInWindow();
        pongPanel.startTimer();
    }

    private void recordPlayed(int score)
    {
        if (!Session.isLoggedIn())
        {
            GuestPlayTracker.recordGuestPlay("pingpong", score);
            return;
        }

        Message request = new Message();
        request.setType(MessageType.GAME_PLAYED_REQUEST);
        request.setGameId("pingpong");
        request.setScore(score);
        NetworkManager.sendAsync(request);
    }
}
