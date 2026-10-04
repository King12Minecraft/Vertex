package games;
import net.Message;
import net.MessageType;
import net.NetworkManager;
import pages.MainMenu;
import pages.SectionCard;
import theme.ThemeColor;
import theme.ThemeManager;
import theme.UITheme;
import ui.RoundedPanel;
import ui.ThemedButton;
import ui.ThemedLabel;
import ui.ThemedTextField;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * CaptionChaosWindow
 * ------------------
 * Client for Caption Chaos (see CaptionChaosMatch for the rules and the server side). Screens, driven entirely by
 * what the server pushes: searching for players, write an answer to the prompt, wait, vote between the anonymous
 * answers (your own is marked and can't be picked), the round reveal with votes and totals, and the final
 * scoreboard. The client only ever submits text and a vote index; who wrote what, the counting and the winner are
 * the server's.
 */
public class CaptionChaosWindow extends JPanel implements NetworkManager.PushListener, EmbeddedGamePanel
{
    private static final String GAME_ID = "caption-chaos";
    private static final String SEARCHING = "SEARCHING";
    private static final String WRITE = "WRITE";
    private static final String WAIT = "WAIT";
    private static final String VOTE = "VOTE";
    private static final String RESULT = "RESULT";
    private static final String FINAL = "FINAL";
    private static final int CARD_WIDTH = 600;

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);

    private JLabel searchingLabel;
    private JLabel writeHeader;
    private JLabel writeTimer;
    private JLabel writePrompt;
    private ThemedTextField answerField;
    private ThemedButton submitButton;
    private JLabel waitLabel;
    private JLabel voteHeader;
    private JLabel voteTimer;
    private JLabel votePrompt;
    private JPanel voteOptions;
    private JLabel resultHeader;
    private JPanel resultRows;
    private JPanel resultScores;
    private JLabel finalWinner;
    private JPanel finalScores;

    private String matchId;
    private boolean matchOver = false;
    private boolean answered = false;
    private boolean voted = false;
    private Timer countdown;
    private long deadline;
    private JLabel countdownLabel;

    public CaptionChaosWindow()
    {
        setLayout(new BorderLayout());
        cards.add(buildSearching(), SEARCHING);
        cards.add(buildWrite(), WRITE);
        cards.add(buildWait(), WAIT);
        cards.add(buildVote(), VOTE);
        cards.add(buildResult(), RESULT);
        cards.add(buildFinal(), FINAL);
        add(cards, BorderLayout.CENTER);
        cardLayout.show(cards, SEARCHING);

        NetworkManager.addPushListener(this);
        Message request = new Message();
        request.setType(MessageType.CAPTIONCHAOS_FIND_MATCH_REQUEST);
        NetworkManager.sendAsync(request);
        String issue = NetworkManager.describeIfNotReady();
        if (issue != null)
        {
            searchingLabel.setText(issue);
        }
    }

    @Override
    public boolean requestLeave()
    {
        if (!matchOver)
        {
            Message request = new Message();
            request.setType(MessageType.CAPTIONCHAOS_LEAVE_QUEUE_REQUEST);
            request.setMatchId(matchId);
            NetworkManager.sendAsync(request);
        }
        stopCountdown();
        NetworkManager.removePushListener(this);
        return true;
    }

    // ==================== screens ====================

    /** A centred card of a fixed readable width on the app background. */
    private JPanel screen(String title, JPanel body)
    {
        RoundedPanel wrapper = new RoundedPanel(ThemeColor.BG_APP, 0);
        wrapper.setLayout(new GridBagLayout());
        // fixed width, but the height follows the content (the vote and reveal lists change length every round)
        SectionCard card = new SectionCard(title)
        {
            @Override
            public Dimension getPreferredSize()
            {
                return new Dimension(CARD_WIDTH, super.getPreferredSize().height);
            }
        };
        card.content(body);
        wrapper.add(card, new GridBagConstraints());
        return wrapper;
    }

    private static JPanel column()
    {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        return p;
    }

    private static JLabel label(String text, java.awt.Font font, ThemeColor color)
    {
        JLabel l = new ThemedLabel(text, color);
        l.setFont(font);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static String wrap(String text, int width)
    {
        return "<html><table width='" + width + "' cellpadding='0' cellspacing='0'><tr><td>"
            + text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</td></tr></table></html>";
    }

    private JPanel buildSearching()
    {
        JPanel body = column();
        searchingLabel = label("Waiting for players... (need " + CaptionChaosMatchManager.MIN_PLAYERS + " to start)",
            UITheme.FONT_BODY, ThemeColor.TEXT_SECONDARY);
        body.add(searchingLabel);
        body.add(Box.createVerticalStrut(16));
        ThemedButton cancel = new ThemedButton("Cancel", false);
        cancel.setAlignmentX(Component.LEFT_ALIGNMENT);
        cancel.setPreferredSize(new Dimension(120, 36));
        cancel.setMaximumSize(new Dimension(120, 36));
        cancel.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { MainMenu.getInstance().returnToGames(); }
        });
        body.add(cancel);
        return screen("CAPTION CHAOS", body);
    }

    private JPanel buildWrite()
    {
        JPanel body = column();
        writeHeader = label(" ", UITheme.FONT_SMALL, ThemeColor.TEXT_MUTED);
        body.add(writeHeader);
        writeTimer = label(" ", UITheme.FONT_SMALL, ThemeColor.ACCENT);
        body.add(writeTimer);
        body.add(Box.createVerticalStrut(12));
        writePrompt = label(" ", UITheme.FONT_HEADING.deriveFont(22f), ThemeColor.TEXT_PRIMARY);
        body.add(writePrompt);
        body.add(Box.createVerticalStrut(14));
        answerField = new ThemedTextField("Your funniest answer (max " + CaptionChaosMatch.MAX_ANSWER_LENGTH + " characters)");
        answerField.setAlignmentX(Component.LEFT_ALIGNMENT);
        answerField.setMaximumSize(new Dimension(4000, 40));
        answerField.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { submitAnswer(); }
        });
        body.add(answerField);
        body.add(Box.createVerticalStrut(12));
        submitButton = new ThemedButton("Lock it in", true);
        submitButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        submitButton.setPreferredSize(new Dimension(140, 38));
        submitButton.setMaximumSize(new Dimension(140, 38));
        submitButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { submitAnswer(); }
        });
        body.add(submitButton);
        return screen("WRITE YOUR ANSWER", body);
    }

    private JPanel buildWait()
    {
        JPanel body = column();
        waitLabel = label("Waiting for the others...", UITheme.FONT_BODY, ThemeColor.TEXT_SECONDARY);
        body.add(waitLabel);
        return screen("CAPTION CHAOS", body);
    }

    private JPanel buildVote()
    {
        JPanel body = column();
        voteHeader = label(" ", UITheme.FONT_SMALL, ThemeColor.TEXT_MUTED);
        body.add(voteHeader);
        voteTimer = label(" ", UITheme.FONT_SMALL, ThemeColor.ACCENT);
        body.add(voteTimer);
        body.add(Box.createVerticalStrut(10));
        votePrompt = label(" ", UITheme.FONT_NAV_BOLD, ThemeColor.TEXT_PRIMARY);
        body.add(votePrompt);
        body.add(Box.createVerticalStrut(12));
        voteOptions = column();
        voteOptions.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(voteOptions);
        return screen("VOTE FOR THE FUNNIEST", body);
    }

    private JPanel buildResult()
    {
        JPanel body = column();
        resultHeader = label(" ", UITheme.FONT_SMALL, ThemeColor.TEXT_MUTED);
        body.add(resultHeader);
        body.add(Box.createVerticalStrut(10));
        resultRows = column();
        resultRows.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(resultRows);
        body.add(Box.createVerticalStrut(14));
        body.add(label("SCORES", UITheme.FONT_SMALL, ThemeColor.TEXT_MUTED));
        body.add(Box.createVerticalStrut(6));
        resultScores = column();
        resultScores.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(resultScores);
        return screen("THE REVEAL", body);
    }

    private JPanel buildFinal()
    {
        JPanel body = column();
        finalWinner = label(" ", UITheme.FONT_HEADING.deriveFont(22f), ThemeColor.ACCENT);
        body.add(finalWinner);
        body.add(Box.createVerticalStrut(12));
        finalScores = column();
        finalScores.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(finalScores);
        body.add(Box.createVerticalStrut(14));
        body.add(label("Chat is open now - say gg!", UITheme.FONT_SMALL, ThemeColor.TEXT_MUTED));
        body.add(Box.createVerticalStrut(14));
        ThemedButton back = new ThemedButton("Back to Games", true);
        back.setAlignmentX(Component.LEFT_ALIGNMENT);
        back.setPreferredSize(new Dimension(170, 38));
        back.setMaximumSize(new Dimension(170, 38));
        back.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { MainMenu.getInstance().returnToGames(); }
        });
        body.add(back);
        return screen("FINAL SCORES", body);
    }

    // ==================== actions ====================

    private void submitAnswer()
    {
        if (answered)
        {
            return;
        }
        String text = answerField.getValue().trim();
        if (text.isEmpty())
        {
            return;
        }
        answered = true;
        Message request = new Message();
        request.setType(MessageType.CAPTIONCHAOS_SUBMIT_REQUEST);
        request.setMatchId(matchId);
        request.setCaptionText(text);
        NetworkManager.sendAsync(request);
        waitLabel.setText("Answer locked in. Waiting for the others...");
        stopCountdown();
        cardLayout.show(cards, WAIT);
    }

    private void vote(int index)
    {
        if (voted)
        {
            return;
        }
        voted = true;
        Message request = new Message();
        request.setType(MessageType.CAPTIONCHAOS_VOTE_REQUEST);
        request.setMatchId(matchId);
        request.setCaptionIndex(index);
        NetworkManager.sendAsync(request);
        waitLabel.setText("Vote cast. Waiting for the others...");
        stopCountdown();
        cardLayout.show(cards, WAIT);
    }

    // ==================== pushes ====================

    @Override
    public void onPush(final Message message)
    {
        MessageType type = message.getType();
        boolean mine = type == MessageType.CAPTIONCHAOS_WRITE_START || type == MessageType.CAPTIONCHAOS_VOTE_START
            || type == MessageType.CAPTIONCHAOS_ROUND_RESULT || type == MessageType.CAPTIONCHAOS_MATCH_OVER;
        if (type == MessageType.QUEUE_UPDATE)
        {
            if (!GAME_ID.equals(message.getQueueGameId()) || matchId != null)
            {
                return;
            }
        }
        else if (!mine || (matchId != null && message.getMatchId() != null && !message.getMatchId().equals(matchId)))
        {
            return;
        }
        SwingUtilities.invokeLater(new Runnable()
        {
            public void run() { handle(message); }
        });
    }

    private void handle(Message m)
    {
        switch (m.getType())
        {
            case QUEUE_UPDATE:
                searchingLabel.setText("Waiting for players... " + m.getQueueCount() + " in queue (need "
                    + CaptionChaosMatchManager.MIN_PLAYERS + " to start)");
                break;
            case CAPTIONCHAOS_WRITE_START:
                matchId = m.getMatchId();
                answered = false;
                answerField.clear();
                writeHeader.setText("Round " + m.getCaptionRound() + " of " + m.getCaptionTotalRounds());
                writePrompt.setText(wrap(m.getCaptionPrompt(), CARD_WIDTH - 60));
                startCountdown(writeTimer, m.getCaptionSeconds());
                cardLayout.show(cards, WRITE);
                answerField.requestFocusInWindow();
                break;
            case CAPTIONCHAOS_VOTE_START:
                matchId = m.getMatchId();
                voted = false;
                showVote(m);
                break;
            case CAPTIONCHAOS_ROUND_RESULT:
                showResult(m);
                break;
            case CAPTIONCHAOS_MATCH_OVER:
                showFinal(m);
                break;
            default:
                break;
        }
    }

    private void showVote(Message m)
    {
        voteHeader.setText("Round " + m.getCaptionRound() + " of " + m.getCaptionTotalRounds());
        votePrompt.setText(wrap(m.getCaptionPrompt(), CARD_WIDTH - 60));
        voteOptions.removeAll();
        List<String> options = m.getCaptionOptions() == null ? new ArrayList<String>() : m.getCaptionOptions();
        final int own = m.getCaptionIndex();
        for (int i = 0; i < options.size(); i++)
        {
            final int index = i;
            RoundedPanel row = new RoundedPanel(ThemeColor.BG_APP, UITheme.RADIUS_BUTTON);
            row.setLayout(new BorderLayout(12, 0));
            row.setBorder(new EmptyBorder(10, 14, 10, 14));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel text = new ThemedLabel(wrap(options.get(i), CARD_WIDTH - 220), ThemeColor.TEXT_PRIMARY);
            text.setFont(UITheme.FONT_BODY);
            row.add(text, BorderLayout.CENTER);
            if (i == own)
            {
                JLabel yours = new ThemedLabel("your answer", ThemeColor.TEXT_MUTED);
                yours.setFont(UITheme.FONT_SMALL);
                row.add(yours, BorderLayout.EAST);
            }
            else
            {
                ThemedButton pick = new ThemedButton("Vote", true);
                pick.setPreferredSize(new Dimension(90, 32));
                pick.addActionListener(new ActionListener()
                {
                    public void actionPerformed(ActionEvent e) { vote(index); }
                });
                row.add(pick, BorderLayout.EAST);
            }
            voteOptions.add(row);
            voteOptions.add(Box.createVerticalStrut(8));
        }
        voteOptions.revalidate();
        startCountdown(voteTimer, m.getCaptionSeconds());
        cardLayout.show(cards, VOTE);
    }

    private void showResult(Message m)
    {
        stopCountdown();
        resultHeader.setText("Round " + m.getCaptionRound() + " of " + m.getCaptionTotalRounds()
            + (m.getCaptionRound() < m.getCaptionTotalRounds() ? " - next round in a few seconds" : " - final scores next"));
        resultRows.removeAll();
        List<String> results = m.getCaptionResults() == null ? new ArrayList<String>() : m.getCaptionResults();
        if (results.isEmpty())
        {
            resultRows.add(label("Nobody answered this round.", UITheme.FONT_BODY, ThemeColor.TEXT_MUTED));
        }
        for (String line : results)
        {
            String[] p = line.split("\\|", 3);
            if (p.length < 3) continue;
            RoundedPanel row = new RoundedPanel(ThemeColor.BG_APP, UITheme.RADIUS_BUTTON);
            row.setLayout(new BorderLayout(12, 0));
            row.setBorder(new EmptyBorder(10, 14, 10, 14));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel text = new ThemedLabel("<html><table width='" + (CARD_WIDTH - 220) + "' cellpadding='0' cellspacing='0'><tr><td>"
                + esc(p[1]) + "<br><font size='-2'>by " + esc(p[0]) + "</font></td></tr></table></html>", ThemeColor.TEXT_PRIMARY);
            text.setFont(UITheme.FONT_BODY);
            row.add(text, BorderLayout.CENTER);
            JLabel votes = new ThemedLabel(p[2] + (p[2].equals("1") ? " vote" : " votes"), ThemeColor.ACCENT);
            votes.setFont(UITheme.FONT_SMALL);
            row.add(votes, BorderLayout.EAST);
            resultRows.add(row);
            resultRows.add(Box.createVerticalStrut(8));
        }
        fillScores(resultScores, m.getCaptionScores());
        resultRows.revalidate();
        cardLayout.show(cards, RESULT);
    }

    private void showFinal(Message m)
    {
        matchOver = true;
        stopCountdown();
        List<String> winners = m.getCaptionWinners() == null ? new ArrayList<String>() : m.getCaptionWinners();
        if (m.isCaptionAborted())
        {
            finalWinner.setText("Too many players left - match ended");
        }
        else if (winners.isEmpty())
        {
            finalWinner.setText("Nobody scored - a draw!");
        }
        else
        {
            finalWinner.setText(winners.size() == 1 ? winners.get(0) + " wins!" : "Tie: " + String.join(", ", winners));
        }
        fillScores(finalScores, m.getCaptionScores());
        cardLayout.show(cards, FINAL);
    }

    private void fillScores(JPanel target, List<String> lines)
    {
        target.removeAll();
        if (lines != null)
        {
            for (String line : lines)
            {
                int colon = line.lastIndexOf(':');
                if (colon < 0) continue;
                JPanel row = new JPanel(new BorderLayout());
                row.setOpaque(false);
                row.setAlignmentX(Component.LEFT_ALIGNMENT);
                row.setMaximumSize(new Dimension(4000, 26));
                JLabel name = new ThemedLabel(line.substring(0, colon), ThemeColor.TEXT_PRIMARY);
                name.setFont(UITheme.FONT_BODY);
                JLabel points = new ThemedLabel(line.substring(colon + 1), ThemeColor.ACCENT);
                points.setFont(UITheme.FONT_NAV_BOLD);
                row.add(name, BorderLayout.WEST);
                row.add(points, BorderLayout.EAST);
                target.add(row);
            }
        }
        target.revalidate();
    }

    private static String esc(String s)
    {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ==================== countdown ====================

    private void startCountdown(final JLabel label, int seconds)
    {
        stopCountdown();
        deadline = System.currentTimeMillis() + seconds * 1000L;
        countdownLabel = label;
        countdown = new Timer(500, new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                long remaining = Math.max(0, deadline - System.currentTimeMillis());
                countdownLabel.setText((remaining + 999) / 1000 + "s left");
            }
        });
        countdown.start();
        label.setText(seconds + "s left");
    }

    private void stopCountdown()
    {
        if (countdown != null)
        {
            countdown.stop();
            countdown = null;
        }
    }
}
