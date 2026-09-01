import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Random;

/**
 * RockPaperScissorsWindow
 * ------------------------
 * Two ways to play: vs Player (1v1 online, best-of-5, simultaneous
 * blind moves - see RockPaperScissorsMatch) or vs AI (fully local,
 * random-pick opponent, unchanged from the original single-player
 * version). Purely event-driven either way - no game loop/timer
 * needed, a move resolves the instant both sides have picked.
 */
public class RockPaperScissorsWindow extends JFrame implements NetworkManager.PushListener
{
    private static final String[] MOVES = { "Rock", "Paper", "Scissors" };
    private static final String MODE_SELECT = "MODE_SELECT";
    private static final String SEARCHING = "SEARCHING";
    private static final String GAME = "GAME";

    private final java.awt.CardLayout cardLayout = new java.awt.CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private final Random random = new Random();

    private JLabel searchingLabel;
    private JLabel statusLabel;
    private JLabel scoreLabel;

    private boolean vsAi;
    private String matchId;
    private String opponentUsername;
    private boolean isSpectator;
    private String spectatorPlayerA;
    private String spectatorPlayerB;
    private JPanel moveButtonRow;

    // vs-AI local score tracking (unchanged from the original)
    private int wins = 0;
    private int losses = 0;
    private int draws = 0;

    // vs-Player online score tracking
    private int myScore = 0;
    private int opponentScore = 0;

    public RockPaperScissorsWindow()
    {
        this(null, null, null, null);
    }

    /** Watches an in-progress match without playing - playerAName/playerBName come from the SPECTATABLE_MATCHES_RESPONSE list (SpectateDialog already has them), since RPS's addSpectator doesn't send any initial catch-up state (see RockPaperScissorsMatch's own note on why). */
    public static RockPaperScissorsWindow forSpectating(String spectateMatchId, String playerAName, String playerBName)
    {
        return new RockPaperScissorsWindow(spectateMatchId, playerAName, playerBName, null);
    }

    /** Waits for the RPS_MATCH_FOUND the server sends once a rematch is accepted - no queue, no spectating. */
    public static RockPaperScissorsWindow forRematchWait(String opponentUsername)
    {
        return new RockPaperScissorsWindow(null, null, null, opponentUsername);
    }

    private RockPaperScissorsWindow(String spectateMatchId, String playerAName, String playerBName, String rematchWaitOpponent)
    {
        super(spectateMatchId != null ? "Vertex - Rock Paper Scissors (Spectating)" : "Vertex - Rock Paper Scissors");
        isSpectator = spectateMatchId != null;
        boolean isRematchWait = rematchWaitOpponent != null;
        spectatorPlayerA = playerAName;
        spectatorPlayerB = playerBName;
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setResizable(false);
        setIconImage(GameLogo.renderIcon(64));

        cards.add(createModeSelectScreen(), MODE_SELECT);
        cards.add(createSearchingScreen(), SEARCHING);
        cards.add(createGameScreen(), GAME);

        getContentPane().add(cards, BorderLayout.CENTER);

        SignatureOverlay.attach(this);
        GlitchEffectOverlay.attach(this);

        NetworkManager.addPushListener(this);

        if (isSpectator)
        {
            matchId = spectateMatchId;
            moveButtonRow.setVisible(false);
            statusLabel.setText("Watching " + playerAName + " vs " + playerBName + "...");
            scoreLabel.setText(playerAName + " 0 - " + playerBName + " 0");
            cardLayout.show(cards, GAME);

            Message request = new Message();
            request.setType(MessageType.SPECTATE_REQUEST);
            request.setGameId("rock-paper-scissors");
            request.setMatchId(spectateMatchId);
            NetworkManager.sendAsync(request);
        }
        else if (isRematchWait)
        {
            moveButtonRow.setVisible(false);
            statusLabel.setText("Waiting for " + rematchWaitOpponent + " to accept...");
            scoreLabel.setText("");
            cardLayout.show(cards, GAME);
        }
        else
        {
            cardLayout.show(cards, MODE_SELECT);
        }

        pack();
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter()
        {
            public void windowClosing(WindowEvent e)
            {
                if (matchId != null && !isSpectator)
                {
                    int choice = javax.swing.JOptionPane.showConfirmDialog(RockPaperScissorsWindow.this,
                        "Close this game? " + (opponentUsername != null ? opponentUsername : "Your opponent") + " will win by default.",
                        "Leave Match", javax.swing.JOptionPane.YES_NO_OPTION);
                    if (choice != javax.swing.JOptionPane.YES_OPTION)
                    {
                        return;
                    }
                }
                if (!isSpectator && !vsAi && matchId == null)
                {
                    leaveQueue();
                }
                // vsAI mode has no natural conclusion (rounds continue until closed), so this is
                // its only real "done playing" signal - recorded here. Online mode is different:
                // it has a genuine conclusion point (RPS_MATCH_OVER), which records the play
                // explicitly there instead, matching Chess/Battleship's "only count real
                // completions" design - not whatever state a mid-game close happens to leave it in.
                if (vsAi)
                {
                    recordPlayed();
                }
                NetworkManager.removePushListener(RockPaperScissorsWindow.this);
                dispose();
            }
        });
    }

    // ==================== Mode select ====================

    private JPanel createModeSelectScreen()
    {
        RoundedPanel panel = new RoundedPanel(ThemeColor.BG_APP, 0);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(50, 60, 50, 60));
        panel.setPreferredSize(new Dimension(460, 320));

        JLabel title = new JLabel("Rock Paper Scissors");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        JLabel subtitle = new JLabel("Choose how you want to play.");
        subtitle.setFont(UITheme.FONT_SUBHEAD);
        subtitle.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setBorder(new EmptyBorder(6, 0, 24, 0));
        panel.add(subtitle);

        JPanel tileRow = new JPanel(new GridLayout(1, 2, 16, 0));
        tileRow.setOpaque(false);
        tileRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        tileRow.setMaximumSize(new Dimension(2000, 150));

        String lastMode = LastGameModeStore.getLastMode("rock-paper-scissors");

        tileRow.add(new GameModeCard("vs Player", "Best of 5, simultaneous blind moves.",
            ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_START), "vsPlayer".equals(lastMode), new GameModeCard.ClickListener()
            {
                public void onClick() { chooseMode(false); }
            }));

        tileRow.add(new GameModeCard("vs AI", "Play against the computer - works fully offline.",
            ThemeManager.getColor(ThemeColor.TEXT_MUTED), "vsAi".equals(lastMode), new GameModeCard.ClickListener()
            {
                public void onClick() { chooseMode(true); }
            }));

        panel.add(tileRow);
        return panel;
    }

    private void chooseMode(boolean aiMode)
    {
        vsAi = aiMode;
        LastGameModeStore.setLastMode("rock-paper-scissors", aiMode ? "vsAi" : "vsPlayer");
        if (aiMode)
        {
            statusLabel.setText("Choose your move.");
            scoreLabel.setText(offlineScoreText());
            cardLayout.show(cards, GAME);
            pack();
            setLocationRelativeTo(null);
        }
        else
        {
            cardLayout.show(cards, SEARCHING);
            pack();
            setLocationRelativeTo(null);
            findMatch();
        }
    }

    // ==================== Searching (vs Player) ====================

    private JPanel createSearchingScreen()
    {
        RoundedPanel panel = new RoundedPanel(ThemeColor.BG_APP, 0);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(60, 60, 60, 60));
        panel.setPreferredSize(new Dimension(380, 240));

        JLabel title = new JLabel("Rock Paper Scissors");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        searchingLabel = new JLabel("Looking for an opponent...");
        searchingLabel.setFont(UITheme.FONT_SUBHEAD);
        searchingLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        searchingLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchingLabel.setBorder(new EmptyBorder(10, 0, 30, 0));
        panel.add(searchingLabel);

        ThemedButton cancel = new ThemedButton("Cancel", false);
        cancel.setAlignmentX(Component.LEFT_ALIGNMENT);
        cancel.setPreferredSize(new Dimension(120, 38));
        cancel.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                leaveQueue();
                dispose();
            }
        });
        panel.add(cancel);

        return panel;
    }

    private void findMatch()
    {
        Message request = new Message();
        request.setType(MessageType.RPS_FIND_MATCH_REQUEST);
        boolean sent = NetworkManager.sendAsync(request);
        if (!sent)
        {
            searchingLabel.setText("Can't reach the server - is it running?");
        }
    }

    private void leaveQueue()
    {
        Message request = new Message();
        request.setType(MessageType.RPS_LEAVE_QUEUE_REQUEST);
        NetworkManager.sendAsync(request);
    }

    // ==================== Game ====================

    private JPanel createGameScreen()
    {
        RoundedPanel panel = new RoundedPanel(ThemeColor.BG_APP, 0);
        panel.setLayout(new BorderLayout());
        panel.setBorder(new EmptyBorder(30, 40, 30, 40));
        panel.setPreferredSize(new Dimension(420, 300));

        JLabel title = new JLabel("Rock Paper Scissors");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        panel.add(title, BorderLayout.NORTH);

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        statusLabel = new JLabel("Choose your move.");
        statusLabel.setFont(UITheme.FONT_SUBHEAD);
        statusLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        statusLabel.setBorder(new EmptyBorder(20, 0, 6, 0));
        center.add(statusLabel);

        scoreLabel = new JLabel("");
        scoreLabel.setFont(UITheme.FONT_SMALL);
        scoreLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        scoreLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        scoreLabel.setBorder(new EmptyBorder(0, 0, 24, 0));
        center.add(scoreLabel);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 0));
        buttonRow.setOpaque(false);
        for (int i = 0; i < MOVES.length; i++)
        {
            final String move = MOVES[i];
            ThemedButton button = new ThemedButton(move, true);
            button.setPreferredSize(new Dimension(110, 44));
            button.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e) { play(move); }
            });
            buttonRow.add(button);
        }
        center.add(buttonRow);
        moveButtonRow = buttonRow;

        panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    private void play(String myMove)
    {
        if (isSpectator)
        {
            return;
        }
        if (vsAi)
        {
            playVsAi(myMove);
        }
        else
        {
            Message request = new Message();
            request.setType(MessageType.RPS_MOVE_REQUEST);
            request.setMatchId(matchId);
            request.setRpsMove(myMove);
            NetworkManager.sendAsync(request);
            statusLabel.setText("You picked " + myMove + " - waiting for " + opponentUsername + "...");
        }
    }

    private void playVsAi(String playerMove)
    {
        String aiMove = MOVES[random.nextInt(MOVES.length)];
        int outcome = resolve(playerMove, aiMove);

        if (outcome > 0)
        {
            wins++;
            statusLabel.setText("You picked " + playerMove + ", CPU picked " + aiMove + " - You win!");
        }
        else if (outcome < 0)
        {
            losses++;
            statusLabel.setText("You picked " + playerMove + ", CPU picked " + aiMove + " - You lose.");
        }
        else
        {
            draws++;
            statusLabel.setText("You both picked " + playerMove + " - Draw.");
        }
        scoreLabel.setText(offlineScoreText());
    }

    /** Returns 1 if playerMove beats aiMove, -1 if it loses, 0 for a draw. */
    private int resolve(String playerMove, String aiMove)
    {
        if (playerMove.equals(aiMove))
        {
            return 0;
        }
        if (("Rock".equals(playerMove) && "Scissors".equals(aiMove))
            || ("Paper".equals(playerMove) && "Rock".equals(aiMove))
            || ("Scissors".equals(playerMove) && "Paper".equals(aiMove)))
        {
            return 1;
        }
        return -1;
    }

    private String offlineScoreText()
    {
        return "Wins " + wins + " - Losses " + losses + " - Draws " + draws;
    }

    // ==================== Push handling (vs Player) ====================

    @Override
    public void onPush(final Message message)
    {
        MessageType type = message.getType();
        boolean isRpsType = type == MessageType.RPS_MATCH_FOUND || type == MessageType.RPS_ROUND_RESULT
            || type == MessageType.RPS_MATCH_OVER || type == MessageType.SPECTATE_ENDED;
        if (!isRpsType)
        {
            return;
        }
        if (matchId != null && message.getMatchId() != null && !message.getMatchId().equals(matchId))
        {
            return;
        }

        SwingUtilities.invokeLater(new Runnable()
        {
            public void run() { handleRpsMessage(message); }
        });
    }

    private void handleRpsMessage(Message message)
    {
        if (message.getType() == MessageType.RPS_MATCH_FOUND)
        {
            matchId = message.getMatchId();
            opponentUsername = message.getOpponentUsername();
            myScore = 0;
            opponentScore = 0;
            moveButtonRow.setVisible(true);
            statusLabel.setText("Choose your move.");
            scoreLabel.setText("You 0 - " + opponentUsername + " 0");

            cardLayout.show(cards, GAME);
            pack();
            setLocationRelativeTo(null);
        }
        else if (message.getType() == MessageType.RPS_ROUND_RESULT)
        {
            myScore = message.getRpsMyScore();
            opponentScore = message.getRpsOpponentScore();
            String myMove = message.getRpsMove();
            String opponentMove = message.getRpsOpponentMove();

            if (isSpectator)
            {
                statusLabel.setText(spectatorPlayerA + ": " + myMove + "  " + spectatorPlayerB + ": " + opponentMove);
                scoreLabel.setText(spectatorPlayerA + " " + myScore + " - " + spectatorPlayerB + " " + opponentScore);
                return;
            }

            String result = message.getMatchResult();
            String outcomeText = "WIN".equals(result) ? "You win this round!"
                : "LOSE".equals(result) ? opponentUsername + " wins this round."
                : "Draw.";

            statusLabel.setText("You: " + myMove + "  " + opponentUsername + ": " + opponentMove + " - " + outcomeText);
            scoreLabel.setText("You " + myScore + " - " + opponentUsername + " " + opponentScore);
        }
        else if (message.getType() == MessageType.SPECTATE_ENDED)
        {
            GameHubDialog.show(this, "Rock Paper Scissors", "The match you were watching has ended.");
            dispose();
        }
        else if (message.getType() == MessageType.RPS_MATCH_OVER)
        {
            String result = message.getMatchResult();
            String text;
            if ("OPPONENT_LEFT".equals(result))
            {
                text = opponentUsername + " left the game. You win by default!";
            }
            else if ("WIN".equals(result))
            {
                text = "You won the series " + message.getRpsMyScore() + " - " + message.getRpsOpponentScore() + "!";
            }
            else
            {
                text = opponentUsername + " won the series " + message.getRpsOpponentScore() + " - " + message.getRpsMyScore() + ".";
            }

            final String finalOpponent = opponentUsername;
            recordPlayed();
            GameHubDialog.showWithAction(this, "Rock Paper Scissors", text, "Rematch", new Runnable()
            {
                public void run() { requestRematch(finalOpponent); }
            });
            dispose();
        }
    }

    private void requestRematch(String opponent)
    {
        Message request = new Message();
        request.setType(MessageType.REMATCH_REQUEST);
        request.setToUsername(opponent);
        request.setGameId("rock-paper-scissors");
        NetworkManager.sendAsync(request);

        RockPaperScissorsWindow window = RockPaperScissorsWindow.forRematchWait(opponent);
        window.setVisible(true);
    }

    /** Fire-and-forget - records play history once the window closes. Score = wins (vs AI) or final series score (vs Player). */
    private void recordPlayed()
    {
        if (isSpectator)
        {
            return;
        }
        int score = vsAi ? wins : myScore;

        if (!Session.isLoggedIn())
        {
            GuestPlayTracker.recordGuestPlay("rock-paper-scissors", score);
            return;
        }

        Message request = new Message();
        request.setType(MessageType.GAME_PLAYED_REQUEST);
        request.setGameId("rock-paper-scissors");
        request.setScore(score);
        NetworkManager.sendAsync(request);
    }
}
