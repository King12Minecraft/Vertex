package games;
import net.NetworkManager;
import net.MessageType;
import net.Message;
import economy.GuestPlayTracker;
import account.Session;
import pages.MainMenu;
import ui.ThemedButton;

import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * CrossingRoadWindow
 * ------------------
 * Single-player only (no online mode), matches PuzzleQuestWindow's
 * simplicity: construct game + panel, show a game-over dialog with
 * Play Again on crash. Embedded in MainMenu's game-host slot (see
 * ChessWindow's javadoc for the pattern); requestLeave() just stops
 * the panel's timer.
 */
public class CrossingRoadWindow extends JPanel implements EmbeddedGamePanel
{
    private CrossingRoadPanel panel;
    private JPanel playWrap;

    public CrossingRoadWindow()
    {
        setLayout(new BorderLayout());
        startGame();
    }

    @Override
    public boolean requestLeave()
    {
        if (panel != null)
        {
            panel.stopTimer();
        }
        return true;
    }

    private void startGame()
    {
        if (panel != null)
        {
            panel.stopTimer();
            remove(playWrap);
        }

        final CrossingRoadGame game = new CrossingRoadGame();
        panel = new CrossingRoadPanel(game, new Runnable()
        {
            public void run() { handleGameOver(game); }
        });

        playWrap = new JPanel(new BorderLayout());
        playWrap.setOpaque(false);
        JPanel centerer = new JPanel(new GridBagLayout());
        centerer.setOpaque(false);
        centerer.add(panel, new GridBagConstraints());
        playWrap.add(centerer, BorderLayout.CENTER);

        ThemedButton leave = new ThemedButton("Leave", false);
        leave.setPreferredSize(new Dimension(90, 34));
        leave.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                if (requestLeave()) { MainMenu.getInstance().returnToGames(); }
            }
        });
        JPanel bottomRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        bottomRow.setOpaque(false);
        bottomRow.setBorder(new EmptyBorder(8, 0, 0, 0));
        bottomRow.add(leave);
        playWrap.add(bottomRow, BorderLayout.SOUTH);

        add(playWrap, BorderLayout.CENTER);
        revalidate();
        repaint();
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
            public void onClose() { MainMenu.getInstance().returnToGames(); }
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
