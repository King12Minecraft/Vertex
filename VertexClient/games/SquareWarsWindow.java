package games;
import net.MessageType;
import ui.ThemedButton;
import theme.ThemeManager;
import theme.UITheme;
import theme.ThemeColor;
import ui.RoundedPanel;
import net.NetworkManager;
import theme.GlitchEffectOverlay;
import theme.SignatureOverlay;
import net.Message;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * SquareWarsWindow
 * ----------------
 * Real-time territory control, 2-4 players sharing one grid - click a
 * cell to claim it (yours instantly, whether it was empty or someone
 * else's - that back-and-forth is the whole game). SquareWarsMatch is
 * the sole authority on the board; this window just sends claim clicks
 * and redraws whatever grid state comes back. The on-screen countdown
 * is a local approximation (counts down from match start) since the
 * server ends the match on its own timer rather than announcing time
 * remaining on every update - close enough for "how much longer," not
 * meant to be exact to the second.
 */
public class SquareWarsWindow extends JFrame implements NetworkManager.PushListener
{
    private static final String MODE_SELECT = "MODE_SELECT";
    private static final String SEARCHING = "SEARCHING";
    private static final String BOARD = "BOARD";

    private static final Color[] PLAYER_COLORS = {
        new Color(220, 70, 70), new Color(70, 140, 220), new Color(230, 200, 60), new Color(120, 200, 120)
    };

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);

    private JLabel searchingLabel;
    private BoardPanel boardPanel;
    private JLabel statusLabel;
    private Timer countdownTimer;

    private String matchId;
    private int mySymbol = -1;
    private char[] board = new char[SquareWarsMatch.TOTAL_CELLS];
    private boolean gameOver;
    private long matchStartedAt;

    public SquareWarsWindow()
    {
        super("Vertex - Square Wars");
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
                if (countdownTimer != null) countdownTimer.stop();
                NetworkManager.removePushListener(SquareWarsWindow.this);
            }
        });
    }

    private JPanel createModeSelectScreen()
    {
        RoundedPanel panel = new RoundedPanel(ThemeColor.BG_APP, 0);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(50, 60, 50, 60));
        panel.setPreferredSize(new Dimension(400, 240));

        JLabel title = new JLabel("Square Wars");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        JLabel subtitle = new JLabel("2-4 players, one shared grid - claim the most cells in 60 seconds.");
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

        JLabel title = new JLabel("Square Wars");
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

    private JPanel createBoardScreen()
    {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(ThemeManager.getColor(ThemeColor.BG_APP));
        wrap.setBorder(new EmptyBorder(20, 20, 20, 20));

        statusLabel = new JLabel("Waiting...");
        statusLabel.setFont(UITheme.FONT_NAV_BOLD);
        statusLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        statusLabel.setBorder(new EmptyBorder(0, 0, 12, 0));
        wrap.add(statusLabel, BorderLayout.NORTH);

        boardPanel = new BoardPanel();
        wrap.add(boardPanel, BorderLayout.CENTER);

        return wrap;
    }

    private void findMatch()
    {
        Message request = new Message();
        request.setType(MessageType.SQWARS_FIND_MATCH_REQUEST);
        boolean sent = NetworkManager.sendAsync(request);
        if (!sent)
        {
            searchingLabel.setText("Can't reach the server - is it running?");
        }
    }

    private void leaveMatch()
    {
        Message request = new Message();
        request.setType(MessageType.SQWARS_LEAVE_QUEUE_REQUEST);
        request.setMatchId(matchId);
        NetworkManager.sendAsync(request);
    }

    private void claimCell(int cellIndex)
    {
        if (gameOver) return;
        Message request = new Message();
        request.setType(MessageType.SQWARS_CLAIM_REQUEST);
        request.setMatchId(matchId);
        request.setCellIndex(cellIndex);
        NetworkManager.sendAsync(request);
    }

    @Override
    public void onPush(final Message message)
    {
        MessageType type = message.getType();
        boolean isType = type == MessageType.SQWARS_MATCH_FOUND || type == MessageType.SQWARS_UPDATE
            || type == MessageType.SQWARS_RESULT || type == MessageType.QUEUE_UPDATE;
        if (!isType)
        {
            return;
        }
        if (type == MessageType.QUEUE_UPDATE && !"square-wars".equals(message.getQueueGameId()))
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
        else if (type == MessageType.SQWARS_MATCH_FOUND)
        {
            matchId = message.getMatchId();
            mySymbol = Integer.parseInt(message.getSymbol());
            matchStartedAt = System.currentTimeMillis();
            applyBoardState(message.getBoardState());
            cardLayout.show(cards, BOARD);
            pack();
            setLocationRelativeTo(null);
            startCountdown();
        }
        else if (type == MessageType.SQWARS_UPDATE)
        {
            applyBoardState(message.getBoardState());
        }
        else if (type == MessageType.SQWARS_RESULT)
        {
            applyBoardState(message.getBoardState());
            gameOver = true;
            if (countdownTimer != null) countdownTimer.stop();

            boolean won = "WIN".equals(message.getMatchResult());
            int reward = message.getSqWarsReward();
            String text = won ? "You won with " + message.getScore() + " cells!"
                : "Final: " + message.getScore() + " cells.";
            if (reward > 0)
            {
                text += "\n+" + reward + " coins.";
            }
            statusLabel.setText(won ? "You won!" : "Time's up.");

            String shareText = won ? "I won a Square Wars match on Vertex with " + message.getScore() + " cells claimed!" : null;
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
                public void onClose() { SquareWarsWindow.this.dispose(); }
            });
        }
    }

    private void startCountdown()
    {
        if (countdownTimer != null) countdownTimer.stop();
        countdownTimer = new Timer(500, new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { updateCountdownLabel(); }
        });
        countdownTimer.start();
        updateCountdownLabel();
    }

    private void updateCountdownLabel()
    {
        long elapsed = System.currentTimeMillis() - matchStartedAt;
        long remainingSec = Math.max(0, (SquareWarsMatch.MATCH_DURATION_MS - elapsed) / 1000);
        statusLabel.setText("Claim cells! " + remainingSec + "s left");
        if (remainingSec <= 0 && countdownTimer != null)
        {
            countdownTimer.stop();
        }
    }

    private void applyBoardState(String boardState)
    {
        if (boardState != null)
        {
            board = boardState.toCharArray();
        }
        boardPanel.repaint();
    }

    private class BoardPanel extends JPanel
    {
        private static final int CELL = 42;

        BoardPanel()
        {
            setPreferredSize(new Dimension(CELL * SquareWarsMatch.COLS, CELL * SquareWarsMatch.ROWS));
            addMouseListener(new MouseAdapter()
            {
                public void mouseClicked(MouseEvent e)
                {
                    int col = e.getX() / CELL;
                    int row = e.getY() / CELL;
                    if (row >= 0 && row < SquareWarsMatch.ROWS && col >= 0 && col < SquareWarsMatch.COLS)
                    {
                        claimCell(row * SquareWarsMatch.COLS + col);
                    }
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.applyAntialiasing(g2);

            for (int row = 0; row < SquareWarsMatch.ROWS; row++)
            {
                for (int col = 0; col < SquareWarsMatch.COLS; col++)
                {
                    int index = row * SquareWarsMatch.COLS + col;
                    char cell = index < board.length ? board[index] : '.';

                    Color color;
                    if (cell == '.')
                    {
                        color = new Color(40, 42, 50);
                    }
                    else
                    {
                        int ownerIndex = cell - '0';
                        color = (ownerIndex >= 0 && ownerIndex < PLAYER_COLORS.length)
                            ? PLAYER_COLORS[ownerIndex] : Color.GRAY;
                    }

                    g2.setColor(color);
                    g2.fillRect(col * CELL, row * CELL, CELL - 2, CELL - 2);

                    if (mySymbol >= 0 && cell == ('0' + mySymbol))
                    {
                        g2.setColor(Color.WHITE);
                        g2.setStroke(new java.awt.BasicStroke(2));
                        g2.drawRect(col * CELL + 1, row * CELL + 1, CELL - 4, CELL - 4);
                    }
                }
            }
            g2.dispose();
        }
    }
}
