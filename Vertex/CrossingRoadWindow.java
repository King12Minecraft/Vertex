import javax.swing.JFrame;
import java.awt.BorderLayout;

/**
 * CrossingRoadWindow
 * ------------------
 * Single-player only (no online mode), matches PuzzleQuestWindow's
 * simplicity: construct game + panel, show a game-over dialog with
 * Play Again on crash.
 */
public class CrossingRoadWindow extends JFrame
{
    private CrossingRoadPanel panel;

    public CrossingRoadWindow()
    {
        super("Vertex - Crossing Road");
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

        final CrossingRoadGame game = new CrossingRoadGame();
        panel = new CrossingRoadPanel(game, new Runnable()
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

    private void handleGameOver(CrossingRoadGame game)
    {
        int score = game.getScore();
        recordPlayed(score);
        SnakeGameOverDialog.show(panel, score, new SnakeGameOverDialog.Choice()
        {
            public void onPlayAgain() { startGame(); }
            public void onClose() { CrossingRoadWindow.this.dispose(); }
        });
    }

    private void recordPlayed(int score)
    {
        if (!Session.isLoggedIn())
        {
            GuestPlayTracker.recordGuestPlay("crossing-road", score);
            return;
        }

        Message request = new Message();
        request.setType(MessageType.GAME_PLAYED_REQUEST);
        request.setGameId("crossing-road");
        request.setScore(score);
        NetworkManager.sendAsync(request);
    }
}
