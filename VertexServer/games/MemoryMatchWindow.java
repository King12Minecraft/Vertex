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
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * MemoryMatchWindow
 * -----------------
 * Online ranked Memory Match, 1v1. Click a face-down card to flip it
 * - MemoryMatchMatch is the sole authority on whose turn it is and
 * whether two flipped cards match; this window just forwards the
 * click and redraws whatever board state comes back (a '.' means
 * still face-down, any other character is that card's revealed
 * symbol - either permanently matched or the current turn's flip).
 */
public class MemoryMatchWindow extends JFrame implements NetworkManager.PushListener
{
    private static final String MODE_SELECT = "MODE_SELECT";
    private static final String SEARCHING = "SEARCHING";
    private static final String BOARD = "BOARD";

    private static final Color[] SYMBOL_COLORS = {
        new Color(230, 90, 90), new Color(90, 170, 230), new Color(230, 200, 60), new Color(120, 200, 120),
        new Color(200, 100, 220), new Color(230, 150, 60), new Color(90, 220, 200), new Color(220, 220, 220)
    };

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);

    private JLabel searchingLabel;
    private BoardPanel boardPanel;
    private JLabel statusLabel;
    private JLabel scoreLabel;

    private String matchId;
    private int mySymbol = -1;
    private String opponentUsername;
    private char[] board = new char[MemoryMatchMatch.CARD_COUNT];
    private boolean myTurn;
    private boolean gameOver;

    public MemoryMatchWindow()
    {
        super("Vertex - Memory Match");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);
        setIconImage(GameLogo.renderIcon(64));
        java.util.Arrays.fill(board, '.');

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
                NetworkManager.removePushListener(MemoryMatchWindow.this);
            }
        });
    }

    private JPanel createModeSelectScreen()
    {
        RoundedPanel panel = new RoundedPanel(ThemeColor.BG_APP, 0);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(50, 60, 50, 60));
        panel.setPreferredSize(new Dimension(400, 240));

        JLabel title = new JLabel("Memory Match");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        JLabel subtitle = new JLabel("Ranked 1v1 - find a pair and go again, miss and pass the turn.");
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

        JLabel title = new JLabel("Memory Match");
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

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        statusLabel = new JLabel("Waiting...");
        statusLabel.setFont(UITheme.FONT_NAV_BOLD);
        statusLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        top.add(statusLabel);

        scoreLabel = new JLabel("You: 0   Opponent: 0");
        scoreLabel.setFont(UITheme.FONT_SMALL);
        scoreLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        scoreLabel.setBorder(new EmptyBorder(4, 0, 12, 0));
        top.add(scoreLabel);

        wrap.add(top, BorderLayout.NORTH);

        boardPanel = new BoardPanel();
        wrap.add(boardPanel, BorderLayout.CENTER);

        return wrap;
    }

    private void findMatch()
    {
        Message request = new Message();
        request.setType(MessageType.MEMORY_FIND_MATCH_REQUEST);
        boolean sent = NetworkManager.sendAsync(request);
        if (!sent)
        {
            searchingLabel.setText("Can't reach the server - is it running?");
        }
    }

    private void leaveMatch()
    {
        Message request = new Message();
        request.setType(MessageType.MEMORY_LEAVE_QUEUE_REQUEST);
        request.setMatchId(matchId);
        NetworkManager.sendAsync(request);
    }

    private void flipCard(int index)
    {
        if (gameOver || !myTurn || board[index] != '.') return;
        Message request = new Message();
        request.setType(MessageType.MEMORY_FLIP_REQUEST);
        request.setMatchId(matchId);
        request.setCellIndex(index);
        NetworkManager.sendAsync(request);
    }

    @Override
    public void onPush(final Message message)
    {
        MessageType type = message.getType();
        boolean isType = type == MessageType.MEMORY_MATCH_FOUND || type == MessageType.MEMORY_UPDATE
            || type == MessageType.MEMORY_RESULT;
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

        if (type == MessageType.MEMORY_MATCH_FOUND)
        {
            matchId = message.getMatchId();
            mySymbol = Integer.parseInt(message.getSymbol());
            opponentUsername = message.getOpponentUsername();
            applyBoardState(message.getBoardState());
            myTurn = mySymbol == 0;
            updateStatus();
            cardLayout.show(cards, BOARD);
            pack();
            setLocationRelativeTo(null);
        }
        else if (type == MessageType.MEMORY_UPDATE)
        {
            applyBoardState(message.getBoardState());
            myTurn = Integer.parseInt(message.getSymbol()) == mySymbol;
            updateScores(message.getTriviaScores());
            updateStatus();
        }
        else if (type == MessageType.MEMORY_RESULT)
        {
            gameOver = true;
            String result = message.getMatchResult();
            String text = "WIN".equals(result) ? "You won with " + message.getScore() + " pairs!"
                : "LOSE".equals(result) ? "You lost - " + message.getScore() + " pairs."
                : "DRAW".equals(result) ? "It's a draw."
                : "OPPONENT_LEFT".equals(result) ? "Your opponent left - you win!"
                : "Game over.";
            statusLabel.setText(text);
            applyBoardState(message.getBoardState());
            boardPanel.repaint();

            String shareText = ("WIN".equals(result) || "OPPONENT_LEFT".equals(result))
                ? "I won a Memory Match on Vertex!" : null;
            SnakeGameOverDialog.show(this, message.getScore(), text, shareText, new SnakeGameOverDialog.Choice()
            {
                public void onPlayAgain()
                {
                    gameOver = false;
                    matchId = null;
                    java.util.Arrays.fill(board, '.');
                    cardLayout.show(cards, SEARCHING);
                    pack();
                    setLocationRelativeTo(null);
                    findMatch();
                }
                public void onClose() { MemoryMatchWindow.this.dispose(); }
            });
        }
    }

    private void updateScores(java.util.List<String> scoreLines)
    {
        if (scoreLines == null || scoreLines.isEmpty()) return;
        String[] parts = scoreLines.get(0).split(":", -1);
        if (parts.length < 2) return;
        int scoreA = Integer.parseInt(parts[0]);
        int scoreB = Integer.parseInt(parts[1]);
        int myScore = mySymbol == 0 ? scoreA : scoreB;
        int theirScore = mySymbol == 0 ? scoreB : scoreA;
        scoreLabel.setText("You: " + myScore + "   Opponent: " + theirScore);
    }

    private void updateStatus()
    {
        String opponentLabel = opponentUsername != null ? opponentUsername : "opponent";
        statusLabel.setText(myTurn ? "Your turn - flip two cards" : opponentLabel + "'s turn...");
    }

    private void applyBoardState(String boardState)
    {
        if (boardState != null && boardState.length() >= MemoryMatchMatch.CARD_COUNT)
        {
            board = boardState.substring(0, MemoryMatchMatch.CARD_COUNT).toCharArray();
        }
        boardPanel.repaint();
    }

    private class BoardPanel extends JPanel
    {
        private static final int CELL = 70;

        BoardPanel()
        {
            setLayout(new GridLayout(4, 4, 8, 8));
            setPreferredSize(new Dimension(CELL * 4 + 24, CELL * 4 + 24));
            setOpaque(false);
            for (int i = 0; i < MemoryMatchMatch.CARD_COUNT; i++)
            {
                add(new CardTile(i));
            }
        }
    }

    private class CardTile extends JPanel
    {
        private final int index;

        CardTile(int index)
        {
            this.index = index;
            setPreferredSize(new Dimension(70, 70));
            addMouseListener(new MouseAdapter()
            {
                public void mouseClicked(MouseEvent e) { flipCard(CardTile.this.index); }
            });
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.applyAntialiasing(g2);

            char value = index < board.length ? board[index] : '.';
            boolean faceUp = value != '.';

            g2.setColor(faceUp ? cardColorFor(value) : new Color(60, 64, 78));
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            g2.setColor(new Color(90, 95, 112));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);

            if (faceUp)
            {
                g2.setColor(Color.BLACK);
                g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
                String text = String.valueOf(value);
                int textWidth = g2.getFontMetrics().stringWidth(text);
                g2.drawString(text, (getWidth() - textWidth) / 2, getHeight() / 2 + 8);
            }
            g2.dispose();
        }
    }

    private Color cardColorFor(char value)
    {
        int index = (value - 'A') % SYMBOL_COLORS.length;
        return index >= 0 && index < SYMBOL_COLORS.length ? SYMBOL_COLORS[index] : Color.GRAY;
    }
}
