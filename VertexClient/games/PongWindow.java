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
 * PongWindow
 * ----------
 * Ping Pong vs AI, embedded in MainMenu's game-host slot (see
 * ChessWindow's javadoc for the pattern). Single-player, fully
 * offline, same recordPlayed pattern as Racing (no coin reward - only
 * Snake has that). requestLeave() just stops PongPanel's timer.
 */
public class PongWindow extends JPanel implements EmbeddedGamePanel
{
    private PongPanel pongPanel;
    private JPanel playWrap;

    public PongWindow()
    {
        setLayout(new BorderLayout());
        startGame();
    }

    @Override
    public boolean requestLeave()
    {
        if (pongPanel != null)
        {
            pongPanel.stopTimer();
        }
        return true;
    }

    private void startGame()
    {
        final PongGame game = new PongGame();

        if (pongPanel != null)
        {
            pongPanel.stopTimer();
            remove(playWrap);
        }

        Runnable onGameOver = new Runnable()
        {
            public void run()
            {
                recordPlayed(game.getPlayerScore());
                SnakeGameOverDialog.show(pongPanel, game.getPlayerScore(), new SnakeGameOverDialog.Choice()
                {
                    public void onPlayAgain() { startGame(); }
                    public void onClose() { MainMenu.getInstance().returnToGames(); }
                });
            }
        };

        pongPanel = new PongPanel(game, onGameOver);

        playWrap = new JPanel(new BorderLayout());
        playWrap.setOpaque(false);
        JPanel centerer = new JPanel(new GridBagLayout());
        centerer.setOpaque(false);
        centerer.add(pongPanel, new GridBagConstraints());
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
