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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * SnakeArenaWindow
 * -----------------
 * Online ranked Snake Arena, 1v1. Arrow keys steer your snake -
 * SnakeArenaMatch is the sole authority on movement, growth, and
 * collisions (server-authoritative, same as AirHockeyWindow); this
 * window just sends direction changes and redraws whatever arena
 * state comes back.
 */
public class SnakeArenaWindow extends JFrame implements NetworkManager.PushListener
{
    private static final String MODE_SELECT = "MODE_SELECT";
    private static final String SEARCHING = "SEARCHING";
    private static final String BOARD = "BOARD";
    private static final int UP = 0, DOWN = 1, LEFT = 2, RIGHT = 3;

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);

    private JLabel searchingLabel;
    private ArenaPanel arenaPanel;
    private JLabel statusLabel;

    private String matchId;
    private String mySymbol;
    private String opponentUsername;
    private boolean gameOver;

    private int[] food = { 0, 0 };
    private java.util.List<int[]> snakeA = new java.util.ArrayList<int[]>();
    private java.util.List<int[]> snakeB = new java.util.ArrayList<int[]>();

    public SnakeArenaWindow()
    {
        super("Vertex - Snake Arena");
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
                NetworkManager.removePushListener(SnakeArenaWindow.this);
            }
        });
    }

    private JPanel createModeSelectScreen()
    {
        RoundedPanel panel = new RoundedPanel(ThemeColor.BG_APP, 0);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(50, 60, 50, 60));
        panel.setPreferredSize(new Dimension(400, 240));

        JLabel title = new JLabel("Snake Arena");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        JLabel subtitle = new JLabel("Ranked 1v1 - two snakes, one arena, live. Arrow keys to steer.");
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

        JLabel title = new JLabel("Snake Arena");
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

        statusLabel = new JLabel("Waiting...");
        statusLabel.setFont(UITheme.FONT_NAV_BOLD);
        statusLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        statusLabel.setBorder(new EmptyBorder(0, 0, 12, 0));
        wrap.add(statusLabel, BorderLayout.NORTH);

        arenaPanel = new ArenaPanel();
        wrap.add(arenaPanel, BorderLayout.CENTER);

        return wrap;
    }

    private void findMatch()
    {
        Message request = new Message();
        request.setType(MessageType.SNAKEARENA_FIND_MATCH_REQUEST);
        boolean sent = NetworkManager.sendAsync(request);
        if (!sent)
        {
            searchingLabel.setText("Can't reach the server - is it running?");
        }
    }

    private void leaveMatch()
    {
        Message request = new Message();
        request.setType(MessageType.SNAKEARENA_LEAVE_QUEUE_REQUEST);
        request.setMatchId(matchId);
        NetworkManager.sendAsync(request);
    }

    private void sendTurn(int direction)
    {
        if (gameOver || matchId == null) return;
        Message request = new Message();
        request.setType(MessageType.SNAKEARENA_TURN_REQUEST);
        request.setMatchId(matchId);
        request.setCellIndex(direction);
        NetworkManager.sendAsync(request);
    }

    @Override
    public void onPush(final Message message)
    {
        MessageType type = message.getType();
        boolean isType = type == MessageType.SNAKEARENA_MATCH_FOUND || type == MessageType.SNAKEARENA_UPDATE
            || type == MessageType.SNAKEARENA_RESULT;
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

        if (type == MessageType.SNAKEARENA_MATCH_FOUND)
        {
            matchId = message.getMatchId();
            mySymbol = message.getSymbol();
            opponentUsername = message.getOpponentUsername();
            applyState(message.getBoardState());
            statusLabel.setText("You are the " + ("A".equals(mySymbol) ? "green" : "blue") + " snake - vs " + opponentUsername);
            cardLayout.show(cards, BOARD);
            pack();
            setLocationRelativeTo(null);
            arenaPanel.requestFocusInWindow();
        }
        else if (type == MessageType.SNAKEARENA_UPDATE)
        {
            applyState(message.getBoardState());
        }
        else if (type == MessageType.SNAKEARENA_RESULT)
        {
            gameOver = true;
            String result = message.getMatchResult();
            String text = "WIN".equals(result) ? "You won! Length: " + message.getScore()
                : "LOSE".equals(result) ? "You lost. Length: " + message.getScore()
                : "DRAW".equals(result) ? "Head-on collision - it's a draw."
                : "OPPONENT_LEFT".equals(result) ? "Your opponent left - you win!"
                : "Game over.";
            statusLabel.setText(text);

            String shareText = ("WIN".equals(result) || "OPPONENT_LEFT".equals(result))
                ? "I won a Snake Arena match on Vertex!" : null;
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
                public void onClose() { SnakeArenaWindow.this.dispose(); }
            });
        }
    }

    /** "food_x,food_y|Ax1,Ay1;Ax2,Ay2;...|Bx1,By1;Bx2,By2;..." - see SnakeArenaMatch.stateString(). */
    private void applyState(String state)
    {
        if (state == null) return;
        String[] parts = state.split("\\|", -1);
        if (parts.length < 3) return;

        String[] foodParts = parts[0].split(",");
        food = new int[] { Integer.parseInt(foodParts[0]), Integer.parseInt(foodParts[1]) };
        snakeA = parseSnake(parts[1]);
        snakeB = parseSnake(parts[2]);
        arenaPanel.repaint();
    }

    private java.util.List<int[]> parseSnake(String text)
    {
        java.util.List<int[]> segments = new java.util.ArrayList<int[]>();
        if (text == null || text.isEmpty()) return segments;
        for (String part : text.split(";"))
        {
            String[] xy = part.split(",");
            if (xy.length == 2)
            {
                segments.add(new int[] { Integer.parseInt(xy[0]), Integer.parseInt(xy[1]) });
            }
        }
        return segments;
    }

    private class ArenaPanel extends JPanel
    {
        private static final int CELL = 20;

        ArenaPanel()
        {
            setPreferredSize(new Dimension(CELL * SnakeArenaMatch.GRID_SIZE, CELL * SnakeArenaMatch.GRID_SIZE));
            setFocusable(true);
            addKeyListener(new KeyAdapter()
            {
                public void keyPressed(KeyEvent e)
                {
                    if (e.getKeyCode() == KeyEvent.VK_UP) sendTurn(UP);
                    else if (e.getKeyCode() == KeyEvent.VK_DOWN) sendTurn(DOWN);
                    else if (e.getKeyCode() == KeyEvent.VK_LEFT) sendTurn(LEFT);
                    else if (e.getKeyCode() == KeyEvent.VK_RIGHT) sendTurn(RIGHT);
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.applyAntialiasing(g2);

            g2.setColor(new Color(25, 27, 35));
            g2.fillRect(0, 0, getWidth(), getHeight());

            g2.setColor(new Color(230, 90, 90));
            g2.fillRect(food[0] * CELL + 2, food[1] * CELL + 2, CELL - 4, CELL - 4);

            drawSnake(g2, snakeA, new Color(90, 200, 120));
            drawSnake(g2, snakeB, new Color(90, 150, 230));

            g2.dispose();
        }

        private void drawSnake(Graphics2D g2, java.util.List<int[]> segments, Color color)
        {
            for (int[] segment : segments)
            {
                g2.setColor(color);
                g2.fillRect(segment[0] * CELL + 1, segment[1] * CELL + 1, CELL - 2, CELL - 2);
            }
        }
    }
}
