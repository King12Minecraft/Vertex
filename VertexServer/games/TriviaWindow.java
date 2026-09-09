package games;
import net.MessageType;
import theme.ThemeManager;
import theme.UITheme;
import theme.ThemeColor;
import ui.RoundedPanel;
import net.NetworkManager;
import theme.GlitchEffectOverlay;
import theme.SignatureOverlay;
import net.Message;
import ui.ThemedButton;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

/**
 * TriviaWindow
 * ------------
 * Online Trivia Blitz, 2-6 players. Shows the current question and 4
 * answer buttons; picking one locks in your answer for that round
 * (TriviaMatch is the sole authority on scoring/timing - this window
 * just forwards the pick and redraws whatever round/result state
 * comes back). A round result screen briefly shows the correct answer
 * and everyone's scores before the next question appears.
 */
public class TriviaWindow extends JFrame implements NetworkManager.PushListener
{
    private static final String MODE_SELECT = "MODE_SELECT";
    private static final String SEARCHING = "SEARCHING";
    private static final String ROUND = "ROUND";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);

    private JLabel searchingLabel;
    private JLabel questionLabel;
    private JLabel roundLabel;
    private JPanel optionsPanel;
    private JPanel scoresPanel;
    private ThemedButton[] optionButtons = new ThemedButton[4];

    private String matchId;
    private boolean answeredThisRound;
    private boolean gameOver;

    public TriviaWindow()
    {
        super("Vertex - Trivia Blitz");
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
                NetworkManager.removePushListener(TriviaWindow.this);
            }
        });
    }

    private JPanel createModeSelectScreen()
    {
        RoundedPanel panel = new RoundedPanel(ThemeColor.BG_APP, 0);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(50, 60, 50, 60));
        panel.setPreferredSize(new Dimension(400, 240));

        JLabel title = new JLabel("Trivia Blitz");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        JLabel subtitle = new JLabel("2-6 players, 8 questions - fastest correct answers win.");
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

        JLabel title = new JLabel("Trivia Blitz");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        searchingLabel = new JLabel("Waiting for more players... (need 2 to start)");
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
        panel.setLayout(new BorderLayout(0, 16));
        panel.setBorder(new EmptyBorder(24, 28, 24, 28));
        panel.setPreferredSize(new Dimension(480, 420));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        roundLabel = new JLabel("Round 1/8");
        roundLabel.setFont(UITheme.FONT_SMALL);
        roundLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        roundLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(roundLabel);

        questionLabel = new JLabel("Waiting for question...");
        questionLabel.setFont(UITheme.FONT_HEADING.deriveFont(19f));
        questionLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        questionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        questionLabel.setBorder(new EmptyBorder(6, 0, 0, 0));
        top.add(questionLabel);

        panel.add(top, BorderLayout.NORTH);

        optionsPanel = new JPanel(new java.awt.GridLayout(2, 2, 12, 12));
        optionsPanel.setOpaque(false);
        for (int i = 0; i < 4; i++)
        {
            final int optionIndex = i;
            ThemedButton button = new ThemedButton("...", false);
            button.setPreferredSize(new Dimension(200, 60));
            button.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e) { pickAnswer(optionIndex); }
            });
            optionButtons[i] = button;
            optionsPanel.add(button);
        }
        panel.add(optionsPanel, BorderLayout.CENTER);

        scoresPanel = new JPanel();
        scoresPanel.setOpaque(false);
        scoresPanel.setLayout(new BoxLayout(scoresPanel, BoxLayout.Y_AXIS));
        panel.add(scoresPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void findMatch()
    {
        Message request = new Message();
        request.setType(MessageType.TRIVIA_FIND_MATCH_REQUEST);
        boolean sent = NetworkManager.sendAsync(request);
        if (!sent)
        {
            searchingLabel.setText("Can't reach the server - is it running?");
        }
    }

    private void leaveMatch()
    {
        Message request = new Message();
        request.setType(MessageType.TRIVIA_LEAVE_QUEUE_REQUEST);
        request.setMatchId(matchId);
        NetworkManager.sendAsync(request);
    }

    private void pickAnswer(int optionIndex)
    {
        if (gameOver || answeredThisRound) return;
        answeredThisRound = true;
        for (ThemedButton b : optionButtons) b.setEnabled(false);

        Message request = new Message();
        request.setType(MessageType.TRIVIA_ANSWER_REQUEST);
        request.setMatchId(matchId);
        request.setCellIndex(optionIndex);
        NetworkManager.sendAsync(request);
    }

    @Override
    public void onPush(final Message message)
    {
        MessageType type = message.getType();
        boolean isType = type == MessageType.TRIVIA_MATCH_FOUND || type == MessageType.TRIVIA_ROUND_START
            || type == MessageType.TRIVIA_ROUND_RESULT || type == MessageType.TRIVIA_RESULT
            || type == MessageType.QUEUE_UPDATE;
        if (!isType)
        {
            return;
        }
        if (type == MessageType.QUEUE_UPDATE && !"trivia-blitz".equals(message.getQueueGameId()))
        {
            return;
        }
        if (matchId != null && message.getMatchId() != null && !message.getMatchId().equals(matchId)
            && type != MessageType.QUEUE_UPDATE)
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

        if (type == MessageType.QUEUE_UPDATE)
        {
            if (searchingLabel != null && matchId == null)
            {
                searchingLabel.setText("Waiting for more players... " + message.getQueueCount()
                    + " in queue (need 2 to start)");
            }
        }
        else if (type == MessageType.TRIVIA_MATCH_FOUND)
        {
            matchId = message.getMatchId();
            cardLayout.show(cards, ROUND);
            pack();
            setLocationRelativeTo(null);
        }
        else if (type == MessageType.TRIVIA_ROUND_START)
        {
            answeredThisRound = false;
            roundLabel.setText("Round " + message.getTriviaRound() + "/" + message.getTriviaTotalRounds());
            questionLabel.setText("<html><body style='width:420px'>" + escapeHtml(message.getTriviaQuestion()) + "</body></html>");

            List<String> options = message.getTriviaOptions();
            for (int i = 0; i < optionButtons.length; i++)
            {
                optionButtons[i].setText(i < options.size() ? options.get(i) : "");
                optionButtons[i].setEnabled(true);
            }
            scoresPanel.removeAll();
            scoresPanel.revalidate();
            scoresPanel.repaint();
        }
        else if (type == MessageType.TRIVIA_ROUND_RESULT)
        {
            for (int i = 0; i < optionButtons.length; i++)
            {
                optionButtons[i].setEnabled(false);
            }
            if (message.getTriviaCorrectIndex() >= 0 && message.getTriviaCorrectIndex() < optionButtons.length)
            {
                optionButtons[message.getTriviaCorrectIndex()].setPrimary(true);
            }
            renderScores(message.getTriviaScores(), "Correct answer highlighted - next question soon...");
        }
        else if (type == MessageType.TRIVIA_RESULT)
        {
            gameOver = true;
            boolean won = "WIN".equals(message.getMatchResult());
            int reward = message.getTriviaReward();
            String text = won ? "You won Trivia Blitz!" : "Trivia Blitz finished.";
            if (reward > 0) text += "\n+" + reward + " coins.";
            renderScores(message.getTriviaScores(), text);

            String shareText = won ? "I won a Trivia Blitz match on Vertex!" : null;
            SnakeGameOverDialog.show(this, 0, text, shareText, new SnakeGameOverDialog.Choice()
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
                public void onClose() { TriviaWindow.this.dispose(); }
            });
        }
    }

    private void renderScores(List<String> scoreLines, String note)
    {
        scoresPanel.removeAll();

        JLabel noteLabel = new JLabel(note);
        noteLabel.setFont(UITheme.FONT_SMALL);
        noteLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        noteLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        noteLabel.setBorder(new EmptyBorder(10, 0, 6, 0));
        scoresPanel.add(noteLabel);

        if (scoreLines != null)
        {
            for (int i = 0; i < scoreLines.size(); i++)
            {
                String[] parts = scoreLines.get(i).split(":", -1);
                if (parts.length < 2) continue;
                JLabel line = new JLabel(parts[0] + ": " + parts[1] + " pts");
                line.setFont(UITheme.FONT_BODY);
                line.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
                line.setAlignmentX(Component.LEFT_ALIGNMENT);
                scoresPanel.add(line);
            }
        }

        scoresPanel.revalidate();
        scoresPanel.repaint();
    }

    private String escapeHtml(String text)
    {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
