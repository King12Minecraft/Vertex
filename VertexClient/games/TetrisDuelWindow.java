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
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * TetrisDuelWindow
 * ----------------
 * Online ranked Competitive Tetris, 1v1. Arrow keys move/rotate/soft-
 * drop, Space hard-drops. TetrisDuelMatch is the sole authority on
 * both boards, garbage, and game-over; this window just sends actions
 * and redraws whatever board states come back for both sides.
 */
public class TetrisDuelWindow extends JPanel implements NetworkManager.PushListener, EmbeddedGamePanel
{
    private static final String MODE_SELECT = "MODE_SELECT";
    private static final String SEARCHING = "SEARCHING";
    private static final String BOARD = "BOARD";

    private static final Color[] CELL_COLORS = {
        null,                               // 0 - empty
        new Color(80, 200, 220),            // 1 - I
        new Color(230, 200, 60),            // 2 - O
        new Color(170, 100, 220),           // 3 - T
        new Color(100, 200, 100),           // 4 - S
        new Color(220, 90, 90),             // 5 - Z
        new Color(90, 120, 220),            // 6 - J
        new Color(230, 150, 70),            // 7 - L
        new Color(110, 110, 110)            // 8 - garbage
    };

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);

    private JLabel searchingLabel;
    private BoardPanel myBoardPanel;
    private BoardPanel opponentBoardPanel;
    private JLabel statusLabel;
    private JLabel scoreLabel;

    private String matchId;
    private String opponentUsername;
    private int[] myGrid = new int[TetrisGame.ROWS * TetrisGame.COLS];
    private int[] opponentGrid = new int[TetrisGame.ROWS * TetrisGame.COLS];
    private boolean gameOver;

    public TetrisDuelWindow()
    {
        setLayout(new BorderLayout());

        cards.add(createModeSelectScreen(), MODE_SELECT);
        cards.add(createSearchingScreen(), SEARCHING);
        cards.add(createBoardScreen(), BOARD);

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
        panel.setPreferredSize(new Dimension(420, 240));
        wrapper.add(panel, new GridBagConstraints());

        JLabel title = new JLabel("Competitive Tetris");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        JLabel subtitle = new JLabel("Ranked 1v1 - clear 2+ lines at once to send garbage.");
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
        panel.setPreferredSize(new Dimension(360, 220));
        wrapper.add(panel, new GridBagConstraints());

        JLabel title = new JLabel("Competitive Tetris");
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

        scoreLabel = new JLabel("Score: 0");
        scoreLabel.setFont(UITheme.FONT_SMALL);
        scoreLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        scoreLabel.setBorder(new EmptyBorder(4, 0, 12, 0));
        top.add(scoreLabel);

        wrap.add(top, BorderLayout.NORTH);

        JPanel boardsRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 24, 0));
        boardsRow.setOpaque(false);

        JPanel myCol = new JPanel();
        myCol.setOpaque(false);
        myCol.setLayout(new BoxLayout(myCol, BoxLayout.Y_AXIS));
        JLabel myLabel = boardLabel("You");
        myCol.add(myLabel);
        myBoardPanel = new BoardPanel(20);
        myCol.add(myBoardPanel);
        boardsRow.add(myCol);

        JPanel oppCol = new JPanel();
        oppCol.setOpaque(false);
        oppCol.setLayout(new BoxLayout(oppCol, BoxLayout.Y_AXIS));
        JLabel oppLabel = boardLabel("Opponent");
        oppCol.add(oppLabel);
        opponentBoardPanel = new BoardPanel(12);
        oppCol.add(opponentBoardPanel);
        boardsRow.add(oppCol);

        JPanel boardsCenterer = new JPanel(new GridBagLayout());
        boardsCenterer.setOpaque(false);
        boardsCenterer.add(boardsRow, new GridBagConstraints());
        wrap.add(boardsCenterer, BorderLayout.CENTER);
        return wrap;
    }

    private JLabel boardLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(UITheme.FONT_SMALL);
        label.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 0, 6, 0));
        return label;
    }

    private void findMatch()
    {
        Message request = new Message();
        request.setType(MessageType.TETRISDUEL_FIND_MATCH_REQUEST);
        boolean sent = NetworkManager.sendAsync(request);
        if (!sent)
        {
            searchingLabel.setText("Can't reach the server - is it running?");
        }
    }

    private void leaveMatch()
    {
        Message request = new Message();
        request.setType(MessageType.TETRISDUEL_LEAVE_QUEUE_REQUEST);
        request.setMatchId(matchId);
        NetworkManager.sendAsync(request);
    }

    private void sendAction(String action)
    {
        if (gameOver || matchId == null) return;
        Message request = new Message();
        request.setType(MessageType.TETRISDUEL_ACTION_REQUEST);
        request.setMatchId(matchId);
        request.setChatText(action);
        NetworkManager.sendAsync(request);
    }

    @Override
    public void onPush(final Message message)
    {
        MessageType type = message.getType();
        boolean isType = type == MessageType.TETRISDUEL_MATCH_FOUND || type == MessageType.TETRISDUEL_UPDATE
            || type == MessageType.TETRISDUEL_RESULT;
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

        if (type == MessageType.TETRISDUEL_MATCH_FOUND)
        {
            matchId = message.getMatchId();
            opponentUsername = message.getOpponentUsername();
            statusLabel.setText("vs " + opponentUsername);
            cardLayout.show(cards, BOARD);
            myBoardPanel.requestFocusInWindow();
        }
        else if (type == MessageType.TETRISDUEL_UPDATE)
        {
            applyGrid(myGrid = parseGrid(message.getBoardState()), myBoardPanel);
            applyGrid(opponentGrid = parseGrid(message.getChatText()), opponentBoardPanel);
            scoreLabel.setText("Score: " + message.getScore());
        }
        else if (type == MessageType.TETRISDUEL_RESULT)
        {
            gameOver = true;
            String result = message.getMatchResult();
            String text = "WIN".equals(result) ? "You won! Score: " + message.getScore()
                : "LOSE".equals(result) ? "You topped out. Score: " + message.getScore()
                : "DRAW".equals(result) ? "Both topped out - a draw."
                : "OPPONENT_LEFT".equals(result) ? "Your opponent left - you win!"
                : "Game over.";
            statusLabel.setText(text);

            String shareText = ("WIN".equals(result) || "OPPONENT_LEFT".equals(result))
                ? "I won a Competitive Tetris match on Vertex!" : null;
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

    private int[] parseGrid(String text)
    {
        int[] grid = new int[TetrisGame.ROWS * TetrisGame.COLS];
        if (text == null) return grid;
        String[] parts = text.split(",");
        for (int i = 0; i < grid.length && i < parts.length; i++)
        {
            try { grid[i] = Integer.parseInt(parts[i]); }
            catch (NumberFormatException ignored) { }
        }
        return grid;
    }

    private void applyGrid(int[] grid, BoardPanel panel)
    {
        panel.repaint();
    }

    private class BoardPanel extends JPanel
    {
        private final int cellSize;
        private final boolean isMine;

        BoardPanel(int cellSize)
        {
            this.cellSize = cellSize;
            this.isMine = cellSize > 15;
            setPreferredSize(new Dimension(cellSize * TetrisGame.COLS, cellSize * TetrisGame.ROWS));
            if (isMine)
            {
                setFocusable(true);
                addKeyListener(new KeyAdapter()
                {
                    public void keyPressed(KeyEvent e)
                    {
                        if (e.getKeyCode() == KeyEvent.VK_LEFT) sendAction("LEFT");
                        else if (e.getKeyCode() == KeyEvent.VK_RIGHT) sendAction("RIGHT");
                        else if (e.getKeyCode() == KeyEvent.VK_UP) sendAction("ROTATE");
                        else if (e.getKeyCode() == KeyEvent.VK_DOWN) sendAction("SOFT_DROP");
                        else if (e.getKeyCode() == KeyEvent.VK_SPACE) sendAction("HARD_DROP");
                    }
                });
            }
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.applyAntialiasing(g2);

            g2.setColor(new Color(20, 22, 28));
            g2.fillRect(0, 0, getWidth(), getHeight());

            int[] grid = isMine ? myGrid : opponentGrid;
            for (int r = 0; r < TetrisGame.ROWS; r++)
            {
                for (int c = 0; c < TetrisGame.COLS; c++)
                {
                    int value = grid[r * TetrisGame.COLS + c];
                    if (value != 0 && value < CELL_COLORS.length)
                    {
                        g2.setColor(CELL_COLORS[value]);
                        g2.fillRect(c * cellSize + 1, r * cellSize + 1, cellSize - 2, cellSize - 2);
                    }
                }
            }
            g2.dispose();
        }
    }
}
