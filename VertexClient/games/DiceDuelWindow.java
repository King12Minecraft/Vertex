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
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * DiceDuelWindow
 * --------------
 * Online ranked Dice Duel, 1v1. Click a die to hold/release it (held
 * dice are excluded from the next re-roll), Re-roll to re-roll
 * everything not held, and click a category to lock your current
 * roll into it. DiceDuelMatch is the sole authority on whose turn it
 * is, what a category scores, and which categories are still
 * available - this window mirrors the same simple scoring formula
 * just to show a live preview of what each category is currently
 * worth, but the server's own calculation is what actually counts.
 */
public class DiceDuelWindow extends JFrame implements NetworkManager.PushListener
{
    private static final String MODE_SELECT = "MODE_SELECT";
    private static final String SEARCHING = "SEARCHING";
    private static final String BOARD = "BOARD";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);

    private JLabel searchingLabel;
    private JLabel statusLabel;
    private DieButton[] diceButtons = new DieButton[DiceDuelMatch.DICE_COUNT];
    private ThemedButton rerollButton;
    private JPanel categoryPanel;
    private JLabel myScoreLabel;
    private JLabel opponentScoreLabel;

    private String matchId;
    private int mySymbol = -1;
    private String opponentUsername;
    private int[] dice = new int[DiceDuelMatch.DICE_COUNT];
    private boolean[] held = new boolean[DiceDuelMatch.DICE_COUNT];
    private int rerollsUsed = 0;
    private Map<String, Integer> myFilledCategories = new HashMap<String, Integer>();
    private Map<String, Integer> opponentFilledCategories = new HashMap<String, Integer>();
    private boolean myTurn;
    private boolean gameOver;

    public DiceDuelWindow()
    {
        super("Vertex - Dice Duel");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);
        setIconImage(GameLogo.renderIcon(64));

        cards.add(createModeSelectScreen(), MODE_SELECT);
        cards.add(createSearchingScreen(), SEARCHING);
        cards.add(createBoardScreen(), BOARD);

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
                NetworkManager.removePushListener(DiceDuelWindow.this);
            }
        });
    }

    private JPanel createModeSelectScreen()
    {
        RoundedPanel panel = new RoundedPanel(ThemeColor.BG_APP, 0);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(50, 60, 50, 60));
        panel.setPreferredSize(new Dimension(420, 240));

        JLabel title = new JLabel("Dice Duel");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        JLabel subtitle = new JLabel("Ranked 1v1 - roll, hold, re-roll, and lock in your score.");
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

        JLabel title = new JLabel("Dice Duel");
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

    private JPanel createBoardScreen()
    {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(ThemeManager.getColor(ThemeColor.BG_APP));
        wrap.setBorder(new EmptyBorder(20, 20, 20, 20));
        wrap.setPreferredSize(new Dimension(420, 420));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        statusLabel = new JLabel("Waiting...");
        statusLabel.setFont(UITheme.FONT_NAV_BOLD);
        statusLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        top.add(statusLabel);

        JPanel scoreRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
        scoreRow.setOpaque(false);
        myScoreLabel = new JLabel("You: 0");
        myScoreLabel.setFont(UITheme.FONT_SMALL);
        myScoreLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        opponentScoreLabel = new JLabel("Opponent: 0");
        opponentScoreLabel.setFont(UITheme.FONT_SMALL);
        opponentScoreLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        scoreRow.add(myScoreLabel);
        scoreRow.add(opponentScoreLabel);
        top.add(scoreRow);

        wrap.add(top, BorderLayout.NORTH);

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBorder(new EmptyBorder(16, 0, 0, 0));

        JPanel diceRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        diceRow.setOpaque(false);
        for (int i = 0; i < DiceDuelMatch.DICE_COUNT; i++)
        {
            final int index = i;
            DieButton button = new DieButton();
            button.addMouseListener(new MouseAdapter()
            {
                public void mouseClicked(MouseEvent e) { toggleHold(index); }
            });
            diceButtons[i] = button;
            diceRow.add(button);
        }
        center.add(diceRow);
        center.add(javax.swing.Box.createVerticalStrut(10));

        rerollButton = new ThemedButton("Re-roll unheld dice", true);
        rerollButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        rerollButton.setPreferredSize(new Dimension(220, 36));
        rerollButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { requestReroll(); }
        });
        JPanel rerollWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        rerollWrap.setOpaque(false);
        rerollWrap.add(rerollButton);
        center.add(rerollWrap);
        center.add(javax.swing.Box.createVerticalStrut(16));

        categoryPanel = new JPanel(new GridLayout(0, 2, 6, 6));
        categoryPanel.setOpaque(false);
        center.add(categoryPanel);

        wrap.add(center, BorderLayout.CENTER);

        return wrap;
    }

    private void findMatch()
    {
        Message request = new Message();
        request.setType(MessageType.DICEDUEL_FIND_MATCH_REQUEST);
        boolean sent = NetworkManager.sendAsync(request);
        if (!sent)
        {
            searchingLabel.setText("Can't reach the server - is it running?");
        }
    }

    private void leaveMatch()
    {
        Message request = new Message();
        request.setType(MessageType.DICEDUEL_LEAVE_QUEUE_REQUEST);
        request.setMatchId(matchId);
        NetworkManager.sendAsync(request);
    }

    private void toggleHold(int index)
    {
        if (!myTurn || gameOver) return;
        held[index] = !held[index];
        diceButtons[index].setHeld(held[index]);
    }

    private void requestReroll()
    {
        if (!myTurn || gameOver || rerollsUsed >= DiceDuelMatch.MAX_REROLLS_PER_TURN) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < held.length; i++)
        {
            if (!held[i])
            {
                if (sb.length() > 0) sb.append(",");
                sb.append(i);
            }
        }
        Message request = new Message();
        request.setType(MessageType.DICEDUEL_REROLL_REQUEST);
        request.setMatchId(matchId);
        request.setChatText(sb.toString());
        NetworkManager.sendAsync(request);
    }

    private void lockCategory(String category)
    {
        if (!myTurn || gameOver) return;
        Message request = new Message();
        request.setType(MessageType.DICEDUEL_LOCK_REQUEST);
        request.setMatchId(matchId);
        request.setChatText(category);
        NetworkManager.sendAsync(request);
    }

    @Override
    public void onPush(final Message message)
    {
        MessageType type = message.getType();
        boolean isType = type == MessageType.DICEDUEL_MATCH_FOUND || type == MessageType.DICEDUEL_UPDATE
            || type == MessageType.DICEDUEL_RESULT;
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

        if (type == MessageType.DICEDUEL_MATCH_FOUND)
        {
            matchId = message.getMatchId();
            mySymbol = Integer.parseInt(message.getSymbol());
            opponentUsername = message.getOpponentUsername();
            applyState(message.getBoardState());
            myTurn = mySymbol == 0;
            updateStatus();
            cardLayout.show(cards, BOARD);
            pack();
            setLocationRelativeTo(null);
        }
        else if (type == MessageType.DICEDUEL_UPDATE)
        {
            applyState(message.getBoardState());
            myTurn = Integer.parseInt(message.getSymbol()) == mySymbol;
            updateStatus();
        }
        else if (type == MessageType.DICEDUEL_RESULT)
        {
            applyState(message.getBoardState());
            gameOver = true;
            String result = message.getMatchResult();
            String text = "WIN".equals(result) ? "You won with " + message.getScore() + " points!"
                : "LOSE".equals(result) ? "You lost - " + message.getScore() + " points."
                : "DRAW".equals(result) ? "It's a draw."
                : "OPPONENT_LEFT".equals(result) ? "Your opponent left - you win!"
                : "Game over.";
            statusLabel.setText(text);

            String shareText = ("WIN".equals(result) || "OPPONENT_LEFT".equals(result))
                ? "I won a Dice Duel match on Vertex!" : null;
            SnakeGameOverDialog.show(this, message.getScore(), text, shareText, new SnakeGameOverDialog.Choice()
            {
                public void onPlayAgain()
                {
                    gameOver = false;
                    matchId = null;
                    myFilledCategories.clear();
                    opponentFilledCategories.clear();
                    cardLayout.show(cards, SEARCHING);
                    pack();
                    setLocationRelativeTo(null);
                    findMatch();
                }
                public void onClose() { DiceDuelWindow.this.dispose(); }
            });
        }
    }

    /** "d1,d2,d3,d4,d5|rerollsUsed|scoresA|scoresB" - see DiceDuelMatch.stateString(). */
    private void applyState(String state)
    {
        if (state == null) return;
        String[] parts = state.split("\\|", -1);
        if (parts.length < 4) return;

        String[] diceParts = parts[0].split(",");
        for (int i = 0; i < dice.length && i < diceParts.length; i++)
        {
            dice[i] = Integer.parseInt(diceParts[i]);
        }
        rerollsUsed = Integer.parseInt(parts[1]);

        Map<String, Integer> scoresA = parseScores(parts[2]);
        Map<String, Integer> scoresB = parseScores(parts[3]);
        myFilledCategories = mySymbol == 0 ? scoresA : scoresB;
        opponentFilledCategories = mySymbol == 0 ? scoresB : scoresA;

        java.util.Arrays.fill(held, false);

        for (int i = 0; i < diceButtons.length; i++)
        {
            diceButtons[i].setValue(dice[i]);
            diceButtons[i].setHeld(false);
        }
        rerollButton.setText("Re-roll unheld dice (" + (DiceDuelMatch.MAX_REROLLS_PER_TURN - rerollsUsed) + " left)");

        rebuildCategoryButtons();
        updateScoreLabels();
    }

    private Map<String, Integer> parseScores(String text)
    {
        Map<String, Integer> map = new HashMap<String, Integer>();
        if (text == null || text.isEmpty()) return map;
        for (String entry : text.split(","))
        {
            String[] kv = entry.split(":");
            if (kv.length == 2)
            {
                try { map.put(kv[0], Integer.parseInt(kv[1])); }
                catch (NumberFormatException ignored) { }
            }
        }
        return map;
    }

    private void rebuildCategoryButtons()
    {
        categoryPanel.removeAll();
        for (final String category : DiceDuelMatch.CATEGORIES)
        {
            boolean filled = myFilledCategories.containsKey(category);
            int previewScore = filled ? myFilledCategories.get(category) : previewScoreFor(category);

            ThemedButton button = new ThemedButton(
                categoryLabel(category) + ": " + previewScore, !filled);
            button.setPreferredSize(new Dimension(180, 34));
            button.setEnabled(!filled);
            button.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e) { lockCategory(category); }
            });
            categoryPanel.add(button);
        }
        categoryPanel.revalidate();
        categoryPanel.repaint();
    }

    private String categoryLabel(String category)
    {
        if ("THREE_OF_A_KIND".equals(category)) return "3-of-a-kind";
        String name = category.substring(0, 1) + category.substring(1).toLowerCase();
        return name;
    }

    /** Mirrors DiceDuelMatch's own scoring formula purely for a live preview - the server's calculation when the lock actually happens is authoritative, this is never sent anywhere. */
    private int previewScoreFor(String category)
    {
        int[] counts = new int[7];
        for (int value : dice) counts[value]++;

        String[] faceNames = { "ONES", "TWOS", "THREES", "FOURS", "FIVES", "SIXES" };
        for (int face = 1; face <= 6; face++)
        {
            if (faceNames[face - 1].equals(category))
            {
                return counts[face] * face;
            }
        }
        if ("THREE_OF_A_KIND".equals(category))
        {
            for (int face = 1; face <= 6; face++)
            {
                if (counts[face] >= 3)
                {
                    int sum = 0;
                    for (int value : dice) sum += value;
                    return sum;
                }
            }
        }
        return 0;
    }

    private void updateScoreLabels()
    {
        myScoreLabel.setText("You: " + sum(myFilledCategories));
        opponentScoreLabel.setText("Opponent: " + sum(opponentFilledCategories));
    }

    private int sum(Map<String, Integer> scores)
    {
        int total = 0;
        for (int value : scores.values()) total += value;
        return total;
    }

    private void updateStatus()
    {
        String opponentLabel = opponentUsername != null ? opponentUsername : "opponent";
        statusLabel.setText(myTurn ? "Your turn" : opponentLabel + "'s turn...");
    }

    private static class DieButton extends RoundedPanel
    {
        private int value = 1;
        private boolean held = false;

        DieButton()
        {
            super(ThemeColor.BG_PANEL, 8);
            setPreferredSize(new Dimension(56, 56));
        }

        void setValue(int value) { this.value = value; repaint(); }
        void setHeld(boolean held) { this.held = held; repaint(); }

        @Override
        protected void paintComponent(java.awt.Graphics g)
        {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            UITheme.applyAntialiasing(g2);
            g2.setColor(held ? new Color(90, 170, 230) : new Color(240, 240, 240));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);

            g2.setColor(Color.BLACK);
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
            String text = String.valueOf(value);
            int textWidth = g2.getFontMetrics().stringWidth(text);
            g2.drawString(text, (getWidth() - textWidth) / 2, getHeight() / 2 + 9);
            g2.dispose();
        }
    }
}
