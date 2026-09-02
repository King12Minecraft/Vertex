import javax.swing.JFrame;
import java.awt.BorderLayout;

/**
 * TetrisWindow
 * ------------
 * Single-player only, matches PongWindow/DinoWindow's simple wrapper
 * pattern: construct game + panel, show a game-over dialog with Play
 * Again when a piece can't spawn.
 */
public class TetrisWindow extends JFrame
{
    private TetrisPanel panel;

    public TetrisWindow()
    {
        super("Vertex - Tetris");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);
        setIconImage(GameLogo.renderIcon(64));

        startGame();
    }

    private void startGame()
    {
        if (panel != null)
        {
            panel.stopTimer();
            getContentPane().remove(panel);
        }

        final TetrisGame game = new TetrisGame();
        panel = new TetrisPanel(game, new Runnable()
        {
            public void run() { handleGameOver(game); }
        });

        getContentPane().add(panel, BorderLayout.CENTER);
        pack();
        setLocationRelativeTo(null);
        SignatureOverlay.attach(this);
        GlitchEffectOverlay.attach(this);
        panel.requestFocusInWindow();
        panel.startTimer();
    }

    private void handleGameOver(TetrisGame game)
    {
        int score = game.getScore();
        recordPlayed(score);
        SnakeGameOverDialog.show(panel, score, new SnakeGameOverDialog.Choice()
        {
            public void onPlayAgain() { startGame(); }
            public void onClose() { TetrisWindow.this.dispose(); }
        });
    }

    private void recordPlayed(int score)
    {
        if (!Session.isLoggedIn())
        {
            GuestPlayTracker.recordGuestPlay("tetris", score);
            return;
        }

        Message request = new Message();
        request.setType(MessageType.GAME_PLAYED_REQUEST);
        request.setGameId("tetris");
        request.setScore(score);
        NetworkManager.sendAsync(request);
    }
}
