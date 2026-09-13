package games;

import net.Message;
import net.MessageType;
import net.NetworkManager;
import theme.GlitchEffectOverlay;
import theme.SignatureOverlay;
import theme.ThemeColor;
import theme.ThemeManager;
import theme.UITheme;
import ui.RoundedPanel;
import ui.ThemedButton;
import ui.ThemedTextField;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * WordDuelWindow
 * --------------
 * Online ranked Word Duel, 1v1. Shows the shared letter draw, a text
 * field to submit candidate words, your own current best, and your
 * opponent's best word LENGTH only (not the word itself - see
 * WordDuelMatch's own javadoc on why). WordDuelMatch is the sole
 * authority on whether a submission is actually valid; this window
 * just sends whatever's typed and shows whatever comes back.
 */
public class WordDuelWindow extends JFrame implements NetworkManager.PushListener
{
    private static final String MODE_SELECT = "MODE_SELECT";
    private static final String SEARCHING = "SEARCHING";
    private static final String ROUND = "ROUND";
    private static final long ROUND_DURATION_MS = 60_000;

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);

    private JLabel searchingLabel;
    private JLabel lettersLabel;
    private JLabel timeLabel;
    private JLabel myBestLabel;
    private JLabel opponentBestLabel;
    private ThemedTextField wordField;

    private String matchId;
    private String opponentUsername;
    private String myBestWord = "";
    private boolean gameOver;
    private long roundStartedAt;
    private Timer countdownTimer;

    public WordDuelWindow()
    {
        super("Vertex - Word Duel");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);
        setIconImage(GameLogo.renderIcon(64));

        cards.add(createModeSelectScreen(), MODE_SELECT);
        cards.add(createSearchingScreen(), SEARCHING);
        cards.add(createRoundScreen(), ROUND);

        getContentPane().add(cards, BorderLayout.CENTER);
        cardLayout.show(cards, MODE_SELECT);
        pack();
        setLocationRelativeTo(null);
        SignatureOverlay.attach(this);
        GlitchEffectOverlay.attach(this);

        NetworkManager.addPushListener(this);

        addWindowListener(new WindowAdapter()
        {
            public void windowClosing(WindowEvent e)
            {
                leaveMatch();
                if (countdownTimer != null) countdownTimer.stop();
                NetworkManager.removePushListener(WordDuelWindow.this);
            }
        });
    }

    private JPanel createModeSelectScreen()
    {
        RoundedPanel panel = new RoundedPanel(ThemeColor.BG_APP, 0);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(50, 60, 50, 60));
        panel.setPreferredSize(new Dimension(420, 240));

        JLabel title = new JLabel("Word Duel");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        JLabel subtitle = new JLabel("Same letters, 60 seconds - build the longest real word you can.");
        subtitle.setFont(UITheme.FONT_SUBHEAD);
        subtitle.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setBorder(new EmptyBorder(6, 0, 24, 0));
        panel.add(subtitle);

        ThemedButton playOnline = new ThemedButton("Find Match", true);
        playOnline.setAlignmentX(Component.LEFT_ALIGNMENT);
        playOnline.setMaximumSize(new Dimension(2000, 42));
        playOnline.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                cardLayout.show(cards, SEARCHING);
                pack();
                setLocationRelativeTo(null);
                findMatch();
            }
        });
        panel.add(playOnline);

        return panel;
    }

    private JPanel createSearchingScreen()
    {
        RoundedPanel panel = new RoundedPanel(ThemeColor.BG_APP, 0);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(60, 60, 60, 60));
        panel.setPreferredSize(new Dimension(360, 220));

        JLabel title = new JLabel("Word Duel");
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
                leaveMatch();
                dispose();
            }
        });
        panel.add(cancel);

        return panel;
    }

    private JPanel createRoundScreen()
    {
        RoundedPanel panel = new RoundedPanel(ThemeColor.BG_APP, 0);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(28, 32, 28, 32));
        panel.setPreferredSize(new Dimension(440, 320));

        timeLabel = new JLabel("60s left");
        timeLabel.setFont(UITheme.FONT_SMALL);
        timeLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        timeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(timeLabel);

        lettersLabel = new JLabel("- - - - - - - - -");
        lettersLabel.setFont(UITheme.FONT_HEADING.deriveFont(30f));
        lettersLabel.setForeground(ThemeManager.getColor(ThemeColor.ACCENT));
        lettersLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        lettersLabel.setBorder(new EmptyBorder(6, 0, 20, 0));
        panel.add(lettersLabel);

        wordField = new ThemedTextField("Type a word and press Enter...");
        wordField.setAlignmentX(Component.LEFT_ALIGNMENT);
        wordField.setMaximumSize(new Dimension(2000, 42));
        wordField.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { submitWord(); }
        });
        panel.add(wordField);
        panel.add(javax.swing.Box.createVerticalStrut(10));

        ThemedButton submit = new ThemedButton("Submit", true);
        submit.setAlignmentX(Component.LEFT_ALIGNMENT);
        submit.setMaximumSize(new Dimension(120, 36));
        submit.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { submitWord(); }
        });
        panel.add(submit);
        panel.add(javax.swing.Box.createVerticalStrut(20));

        myBestLabel = new JLabel("Your best: (none yet)");
        myBestLabel.setFont(UITheme.FONT_NAV_BOLD);
        myBestLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        myBestLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(myBestLabel);

        opponentBestLabel = new JLabel("Opponent's best: 0 letters");
        opponentBestLabel.setFont(UITheme.FONT_SMALL);
        opponentBestLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        opponentBestLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        opponentBestLabel.setBorder(new EmptyBorder(4, 0, 0, 0));
        panel.add(opponentBestLabel);

        return panel;
    }

    private void findMatch()
    {
        Message request = new Message();
        request.setType(MessageType.WORDDUEL_FIND_MATCH_REQUEST);
        boolean sent = NetworkManager.sendAsync(request);
        if (!sent)
        {
            searchingLabel.setText("Can't reach the server - is it running?");
        }
    }

    private void leaveMatch()
    {
        Message request = new Message();
        request.setType(MessageType.WORDDUEL_LEAVE_QUEUE_REQUEST);
        request.setMatchId(matchId);
        NetworkManager.sendAsync(request);
    }

    private void submitWord()
    {
        if (gameOver) return;
        String word = wordField.getValue().trim();
        if (word.isEmpty()) return;

        Message request = new Message();
        request.setType(MessageType.WORDDUEL_SUBMIT_REQUEST);
        request.setMatchId(matchId);
        request.setChatText(word);
        NetworkManager.sendAsync(request);

        if (word.length() > myBestWord.length())
        {
            myBestWord = word;
            myBestLabel.setText("Your best so far: " + myBestWord + " (" + myBestWord.length() + ")");
        }
        wordField.clear();
    }

    @Override
    public void onPush(final Message message)
    {
        MessageType type = message.getType();
        boolean isType = type == MessageType.WORDDUEL_MATCH_FOUND || type == MessageType.WORDDUEL_UPDATE
            || type == MessageType.WORDDUEL_RESULT;
        if (!isType)
        {
            return;
        }
        if (matchId != null && message.getMatchId() != null && !message.getMatchId().equals(matchId))
        {
            return;
        }

        SwingUtilities.invokeLater(new Runnable()
        {
            public void run() { handleServerMessage(message); }
        });
    }

    private void handleServerMessage(Message message)
    {
        MessageType type = message.getType();

        if (type == MessageType.WORDDUEL_MATCH_FOUND)
        {
            matchId = message.getMatchId();
            opponentUsername = message.getOpponentUsername();
            myBestWord = "";
            lettersLabel.setText(formatLetters(message.getTriviaQuestion()));
            myBestLabel.setText("Your best so far: (none yet)");
            opponentBestLabel.setText("Opponent's best: 0 letters");
            roundStartedAt = System.currentTimeMillis();
            startCountdown();
            cardLayout.show(cards, ROUND);
            pack();
            setLocationRelativeTo(null);
        }
        else if (type == MessageType.WORDDUEL_UPDATE)
        {
            java.util.List<String> scores = message.getTriviaScores();
            if (scores != null && !scores.isEmpty())
            {
                String[] parts = scores.get(0).split(":", -1);
                if (parts.length == 2)
                {
                    opponentBestLabel.setText("Opponent's best: " + parts[1] + " letters");
                }
            }
        }
        else if (type == MessageType.WORDDUEL_RESULT)
        {
            gameOver = true;
            if (countdownTimer != null) countdownTimer.stop();
            String result = message.getMatchResult();
            String myWord = message.getTriviaQuestion();
            String opponentWord = message.getChatText();
            String text = "WIN".equals(result) ? "You won! Your word: " + myWord
                : "LOSE".equals(result) ? "You lost. Your word: " + myWord + " - opponent: " + opponentWord
                : "DRAW".equals(result) ? "It's a draw - both: " + myWord
                : "OPPONENT_LEFT".equals(result) ? "Your opponent left - you win!"
                : "Time's up.";

            String shareText = ("WIN".equals(result) || "OPPONENT_LEFT".equals(result))
                ? "I won a Word Duel on Vertex with \"" + myWord + "\"!" : null;
            SnakeGameOverDialog.show(this, message.getScore(), text, shareText, new SnakeGameOverDialog.Choice()
            {
                public void onPlayAgain()
                {
                    gameOver = false;
                    matchId = null;
                    cardLayout.show(cards, SEARCHING);
                    pack();
                    setLocationRelativeTo(null);
                    findMatch();
                }
                public void onClose() { WordDuelWindow.this.dispose(); }
            });
        }
    }

    private String formatLetters(String letters)
    {
        if (letters == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : letters.toUpperCase().toCharArray())
        {
            if (sb.length() > 0) sb.append("  ");
            sb.append(c);
        }
        return sb.toString();
    }

    private void startCountdown()
    {
        if (countdownTimer != null) countdownTimer.stop();
        countdownTimer = new Timer(500, new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { updateCountdown(); }
        });
        countdownTimer.start();
        updateCountdown();
    }

    private void updateCountdown()
    {
        long elapsed = System.currentTimeMillis() - roundStartedAt;
        long remainingSec = Math.max(0, (ROUND_DURATION_MS - elapsed) / 1000);
        timeLabel.setText(remainingSec + "s left");
        if (remainingSec <= 0 && countdownTimer != null)
        {
            countdownTimer.stop();
        }
    }
}
