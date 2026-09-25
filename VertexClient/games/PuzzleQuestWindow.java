package games;
import net.NetworkManager;
import net.MessageType;
import net.Message;
import economy.GuestPlayTracker;
import account.Session;
import pages.MainMenu;
import ui.GameHubDialog;
import ui.ThemedButton;
import theme.ThemeManager;
import theme.UITheme;
import theme.ThemeColor;
import ui.RoundedPanel;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * PuzzleQuestWindow
 * ------------------
 * Puzzle Quest - the classic 15-puzzle. Click any tile adjacent to
 * the blank space to slide it. Tracks moves, shows a completion
 * message when solved, and offers a fresh shuffle without leaving the
 * game. Embedded in MainMenu's game-host slot (see ChessWindow's
 * javadoc for the pattern); requestLeave() has nothing to confirm.
 */
public class PuzzleQuestWindow extends JPanel implements EmbeddedGamePanel
{
    private final PuzzleQuestGame game = new PuzzleQuestGame();
    private final PuzzleTileButton[] tileButtons = new PuzzleTileButton[PuzzleQuestGame.SIZE * PuzzleQuestGame.SIZE];
    private JLabel movesLabel;

    public PuzzleQuestWindow()
    {
        setLayout(new BorderLayout());

        RoundedPanel panel = new RoundedPanel(ThemeColor.BG_APP, 0);
        panel.setLayout(new BorderLayout());
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setOpaque(false);
        headerRow.setBorder(new EmptyBorder(0, 0, 6, 0));

        JLabel title = new JLabel("Puzzle Quest");
        title.setFont(UITheme.FONT_NAV_BOLD);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        headerRow.add(title, BorderLayout.WEST);

        movesLabel = new JLabel("Moves: 0");
        movesLabel.setFont(UITheme.FONT_SMALL);
        movesLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        headerRow.add(movesLabel, BorderLayout.EAST);

        JLabel instructions = new JLabel(
            "<html>Click a tile next to the blank space to slide it. "
            + "Arrange 1-15 in order, left to right, top to bottom, to win.</html>");
        instructions.setFont(UITheme.FONT_SMALL);
        instructions.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));

        JPanel topWrap = new JPanel();
        topWrap.setOpaque(false);
        topWrap.setLayout(new javax.swing.BoxLayout(topWrap, javax.swing.BoxLayout.Y_AXIS));
        topWrap.setBorder(new EmptyBorder(0, 0, 14, 0));
        topWrap.add(headerRow);
        topWrap.add(instructions);

        panel.add(topWrap, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(PuzzleQuestGame.SIZE, PuzzleQuestGame.SIZE, 6, 6));
        grid.setOpaque(false);
        grid.setPreferredSize(new Dimension(280, 280));

        for (int i = 0; i < tileButtons.length; i++)
        {
            final int index = i;
            final PuzzleTileButton tile = new PuzzleTileButton();
            tile.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e) { makeMove(index); }
            });
            tileButtons[i] = tile;
            grid.add(tile);
        }
        JPanel gridCenterer = new JPanel(new GridBagLayout());
        gridCenterer.setOpaque(false);
        gridCenterer.add(grid, new GridBagConstraints());
        panel.add(gridCenterer, BorderLayout.CENTER);

        JPanel bottomRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        bottomRow.setOpaque(false);
        bottomRow.setBorder(new EmptyBorder(14, 0, 0, 0));

        ThemedButton newPuzzle = new ThemedButton("New Puzzle", false);
        newPuzzle.setPreferredSize(new Dimension(130, 36));
        newPuzzle.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                game.shuffleAgain();
                refreshTiles();
            }
        });

        ThemedButton close = new ThemedButton("Leave", false);
        close.setPreferredSize(new Dimension(100, 36));
        close.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { MainMenu.getInstance().returnToGames(); }
        });

        bottomRow.add(newPuzzle);
        bottomRow.add(close);
        panel.add(bottomRow, BorderLayout.SOUTH);

        add(panel, BorderLayout.CENTER);
        refreshTiles();
    }

    @Override
    public boolean requestLeave()
    {
        return true;
    }

    private void makeMove(int index)
    {
        if (game.tryMove(index))
        {
            refreshTiles();
            if (game.isSolved())
            {
                recordPlayed();
                GameHubDialog.show(this, "Puzzle Quest",
                    "Solved in " + game.getMoves() + " moves! Click \"New Puzzle\" for another.");
            }
        }
    }

    private void refreshTiles()
    {
        for (int i = 0; i < tileButtons.length; i++)
        {
            tileButtons[i].setValue(game.getTile(i));
        }
        movesLabel.setText("Moves: " + game.getMoves());
    }

    /** Fire-and-forget - records play history only, no score/coin reward (this game has no score concept, just move count). */
    private void recordPlayed()
    {
        if (!Session.isLoggedIn())
        {
            GuestPlayTracker.recordGuestPlay("puzzle-quest", 0);
            return;
        }

        Message request = new Message();
        request.setType(MessageType.GAME_PLAYED_REQUEST);
        request.setGameId("puzzle-quest");
        NetworkManager.sendAsync(request);
    }
}
