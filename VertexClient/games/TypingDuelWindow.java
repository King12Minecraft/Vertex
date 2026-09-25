package games;

import net.Message;
import net.MessageType;
import net.NetworkManager;
import pages.MainMenu;
import theme.ThemeColor;
import theme.ThemeManager;
import theme.UITheme;
import ui.RoundedPanel;
import ui.ThemedButton;
import ui.ThemedTextField;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * TypingDuelWindow
 * ----------------
 * Online ranked Typing Duel, 1v1. Type the shown sentence into the
 * field - every keystroke reports your current text to the server
 * for validation (TypingDuelMatch checks it against the real
 * sentence; this window never decides a win locally). Live progress
 * bars for both players update as you both type.
 *
 * Embedded in MainMenu's game-host slot (see ChessWindow's javadoc for
 * the pattern). Never confirmed before leaving mid-match even as its
 * own window, so requestLeave() here stays unconditional.
 */
public class TypingDuelWindow extends JPanel implements NetworkManager.PushListener, EmbeddedGamePanel
{
    private static final String MODE_SELECT = "MODE_SELECT";
    private static final String SEARCHING = "SEARCHING";
    private static final String ROUND = "ROUND";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);

    private JLabel searchingLabel;
    private JLabel sentenceLabel;
    private JLabel roundScoreLabel;
    private ThemedTextField typingField;
    private ProgressBar myProgressBar;
    private ProgressBar opponentProgressBar;
    private JLabel statusLabel;

    private String matchId;
    private String mySymbol;
    private String opponentUsername;
    private String currentSentence = "";
    private boolean roundLocked;
    private boolean gameOver;

    public TypingDuelWindow()
    {
        setLayout(new BorderLayout());

        cards.add(createModeSelectScreen(), MODE_SELECT);
        cards.add(createSearchingScreen(), SEARCHING);
        cards.add(createRoundScreen(), ROUND);

        add(cards, BorderLayout.CENTER);
        cardLayout.show(cards, MODE_SELECT);

        NetworkManager.addPushListener(this);
    }

    @Override
    public boolean requestLeave()
    {
        leaveMatch();
        NetworkManager.removePushListener(this);
        return true;
    }

    private JPanel createModeSelectScreen()
    {
        RoundedPanel wrapper = new RoundedPanel(ThemeColor.BG_APP, 0);
        wrapper.setLayout(new GridBagLayout());

        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(50, 60, 50, 60));
        wrapper.add(panel, new GridBagConstraints());

        JLabel title = new JLabel("Typing Duel");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        JLabel subtitle = new JLabel("Ranked 1v1 - same sentence, same start, pure speed. Best of 5.");
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
                findMatch();
            }
        });
        panel.add(playOnline);

        return wrapper;
    }

    private JPanel createSearchingScreen()
    {
        RoundedPanel wrapper = new RoundedPanel(ThemeColor.BG_APP, 0);
        wrapper.setLayout(new GridBagLayout());

        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(60, 60, 60, 60));
        wrapper.add(panel, new GridBagConstraints());

        JLabel title = new JLabel("Typing Duel");
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
                MainMenu.getInstance().returnToGames();
            }
        });
        panel.add(cancel);

        return wrapper;
    }

    private JPanel createRoundScreen()
    {
        RoundedPanel wrapper = new RoundedPanel(ThemeColor.BG_APP, 0);
        wrapper.setLayout(new GridBagLayout());

        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(26, 30, 26, 30));
        wrapper.add(panel, new GridBagConstraints());

        statusLabel = new JLabel("Waiting...");
        statusLabel.setFont(UITheme.FONT_NAV_BOLD);
        statusLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(statusLabel);

        roundScoreLabel = new JLabel("Rounds: 0 - 0");
        roundScoreLabel.setFont(UITheme.FONT_SMALL);
        roundScoreLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        roundScoreLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        roundScoreLabel.setBorder(new EmptyBorder(2, 0, 16, 0));
        panel.add(roundScoreLabel);

        sentenceLabel = new JLabel("<html><body style='width:440px'>Get ready...</body></html>");
        sentenceLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 16));
        sentenceLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        sentenceLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        sentenceLabel.setBorder(new EmptyBorder(0, 0, 16, 0));
        panel.add(sentenceLabel);

        typingField = new ThemedTextField("Start typing here...");
        typingField.setAlignmentX(Component.LEFT_ALIGNMENT);
        typingField.setMaximumSize(new Dimension(2000, 44));
        typingField.addChangeListener(new Runnable()
        {
            public void run() { onTyped(); }
        });
        panel.add(typingField);
        panel.add(javax.swing.Box.createVerticalStrut(20));

        JLabel myLabel = new JLabel("You");
        myLabel.setFont(UITheme.FONT_SMALL);
        myLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        myLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(myLabel);

        myProgressBar = new ProgressBar(new Color(90, 200, 120));
        myProgressBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        myProgressBar.setMaximumSize(new Dimension(2000, 16));
        panel.add(myProgressBar);
        panel.add(javax.swing.Box.createVerticalStrut(10));

        JLabel oppLabel = new JLabel("Opponent");
        oppLabel.setFont(UITheme.FONT_SMALL);
        oppLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        oppLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(oppLabel);

        opponentProgressBar = new ProgressBar(new Color(90, 150, 230));
        opponentProgressBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        opponentProgressBar.setMaximumSize(new Dimension(2000, 16));
        panel.add(opponentProgressBar);
        panel.add(javax.swing.Box.createVerticalStrut(20));

        ThemedButton leave = new ThemedButton("Leave", false);
        leave.setAlignmentX(Component.LEFT_ALIGNMENT);
        leave.setMaximumSize(new Dimension(100, 36));
        leave.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                if (requestLeave()) { MainMenu.getInstance().returnToGames(); }
            }
        });
        panel.add(leave);

        return wrapper;
    }

    private void findMatch()
    {
        Message request = new Message();
        request.setType(MessageType.TYPINGDUEL_FIND_MATCH_REQUEST);
        NetworkManager.sendAsync(request);
        String connectionIssue = NetworkManager.describeIfNotReady();
        if (connectionIssue != null)
        {
            searchingLabel.setText(connectionIssue);
        }
    }

    private void leaveMatch()
    {
        Message request = new Message();
        request.setType(MessageType.TYPINGDUEL_LEAVE_QUEUE_REQUEST);
        request.setMatchId(matchId);
        NetworkManager.sendAsync(request);
    }

    private void onTyped()
    {
        if (gameOver || roundLocked) return;
        Message request = new Message();
        request.setType(MessageType.TYPINGDUEL_PROGRESS_REQUEST);
        request.setMatchId(matchId);
        request.setChatText(typingField.getValue());
        NetworkManager.sendAsync(request);
    }

    @Override
    public void onPush(final Message message)
    {
        MessageType type = message.getType();
        boolean isType = type == MessageType.TYPINGDUEL_MATCH_FOUND || type == MessageType.TYPINGDUEL_ROUND_START
            || type == MessageType.TYPINGDUEL_UPDATE || type == MessageType.TYPINGDUEL_ROUND_RESULT
            || type == MessageType.TYPINGDUEL_RESULT;
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

        if (type == MessageType.TYPINGDUEL_MATCH_FOUND)
        {
            matchId = message.getMatchId();
            mySymbol = message.getSymbol();
            opponentUsername = message.getOpponentUsername();
            statusLabel.setText("vs " + opponentUsername);
            cardLayout.show(cards, ROUND);
        }
        else if (type == MessageType.TYPINGDUEL_ROUND_START)
        {
            currentSentence = message.getTriviaQuestion();
            sentenceLabel.setText("<html><body style='width:440px'>" + escapeHtml(currentSentence) + "</body></html>");
            roundLocked = false;
            typingField.clear();
            typingField.requestFocusInWindow();
            myProgressBar.setProgress(0);
            opponentProgressBar.setProgress(0);
            updateRoundScore(message.getTriviaScores());
        }
        else if (type == MessageType.TYPINGDUEL_UPDATE)
        {
            java.util.List<String> scores = message.getTriviaScores();
            if (scores != null && !scores.isEmpty() && currentSentence != null && !currentSentence.isEmpty())
            {
                String[] parts = scores.get(0).split(":", -1);
                if (parts.length == 2)
                {
                    int progressA = Integer.parseInt(parts[0]);
                    int progressB = Integer.parseInt(parts[1]);
                    int myProgress = "A".equals(mySymbol) ? progressA : progressB;
                    int theirProgress = "A".equals(mySymbol) ? progressB : progressA;
                    myProgressBar.setProgress(myProgress / (double) currentSentence.length());
                    opponentProgressBar.setProgress(theirProgress / (double) currentSentence.length());
                }
            }
        }
        else if (type == MessageType.TYPINGDUEL_ROUND_RESULT)
        {
            roundLocked = true;
            updateRoundScore(message.getTriviaScores());
        }
        else if (type == MessageType.TYPINGDUEL_RESULT)
        {
            gameOver = true;
            String result = message.getMatchResult();
            String text = "WIN".equals(result) ? "You won the match, " + message.getScore() + " rounds to go!"
                : "LOSE".equals(result) ? "You lost the match - " + message.getScore() + " rounds won."
                : "OPPONENT_LEFT".equals(result) ? "Your opponent left - you win!"
                : "Match over.";
            statusLabel.setText(text);

            String shareText = ("WIN".equals(result) || "OPPONENT_LEFT".equals(result))
                ? "I won a Typing Duel on Vertex!" : null;
            SnakeGameOverDialog.show(this, message.getScore(), text, shareText, new SnakeGameOverDialog.Choice()
            {
                public void onPlayAgain()
                {
                    gameOver = false;
                    matchId = null;
                    cardLayout.show(cards, SEARCHING);
                    findMatch();
                }
                public void onClose() { MainMenu.getInstance().returnToGames(); }
            });
        }
    }

    private void updateRoundScore(java.util.List<String> scores)
    {
        if (scores == null || scores.isEmpty()) return;
        String[] parts = scores.get(0).split(":", -1);
        if (parts.length != 2) return;
        int winsA = Integer.parseInt(parts[0]), winsB = Integer.parseInt(parts[1]);
        int myWins = "A".equals(mySymbol) ? winsA : winsB;
        int theirWins = "A".equals(mySymbol) ? winsB : winsA;
        roundScoreLabel.setText("Rounds: " + myWins + " - " + theirWins);
    }

    private String escapeHtml(String text)
    {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static class ProgressBar extends JPanel
    {
        private final Color fillColor;
        private double progress = 0;

        ProgressBar(Color fillColor)
        {
            this.fillColor = fillColor;
            setOpaque(false);
        }

        void setProgress(double progress)
        {
            this.progress = Math.max(0, Math.min(1, progress));
            repaint();
        }

        @Override
        protected void paintComponent(java.awt.Graphics g)
        {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            UITheme.applyAntialiasing(g2);
            g2.setColor(new Color(50, 53, 65));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            g2.setColor(fillColor);
            g2.fillRoundRect(0, 0, Math.max(8, (int) (getWidth() * progress)), getHeight(), 8, 8);
            g2.dispose();
        }
    }
}
