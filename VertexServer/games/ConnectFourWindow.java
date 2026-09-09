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
import java.awt.geom.Ellipse2D;

/**
 * ConnectFourWindow
 * -----------------
 * Online ranked Connect Four, 1v1 - same mode-select/searching/board
 * shell shape as the other short 1v1 games (Tic-Tac-Toe, Chess). Click
 * a column to drop a disc into the lowest open row.
 */
public class ConnectFourWindow extends JFrame implements NetworkManager.PushListener
{
    private static final String MODE_SELECT = "MODE_SELECT";
    private static final String SEARCHING = "SEARCHING";
    private static final String BOARD = "BOARD";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);

    private JLabel searchingLabel;
    private BoardPanel boardPanel;
    private JLabel statusLabel;

    private String matchId;
    private String mySymbol;
    private String opponentUsername;
    private char[] board = new char[ConnectFourMatch.COLS * ConnectFourMatch.ROWS];
    private boolean myTurn;
    private boolean gameOver;

    public ConnectFourWindow()
    {
        super("Vertex - Connect Four");
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
                NetworkManager.removePushListener(ConnectFourWindow.this);
            }
        });
    }

    private JPanel createModeSelectScreen()
    {
        RoundedPanel panel = new RoundedPanel(ThemeColor.BG_APP, 0);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(50, 60, 50, 60));
        panel.setPreferredSize(new Dimension(380, 240));

        JLabel title = new JLabel("Connect Four");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        JLabel subtitle = new JLabel("Ranked 1v1 - get four in a row.");
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

        JLabel title = new JLabel("Connect Four");
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

        boardPanel = new BoardPanel();
        wrap.add(boardPanel, BorderLayout.CENTER);

        return wrap;
    }

    private void findMatch()
    {
        Message request = new Message();
        request.setType(MessageType.CONNECT4_FIND_MATCH_REQUEST);
        boolean sent = NetworkManager.sendAsync(request);
        if (!sent)
        {
            searchingLabel.setText("Can't reach the server - is it running?");
        }
    }

    private void leaveMatch()
    {
        Message request = new Message();
        request.setType(MessageType.CONNECT4_LEAVE_QUEUE_REQUEST);
        request.setMatchId(matchId);
        NetworkManager.sendAsync(request);
    }

    private void makeMove(int column)
    {
        if (gameOver || !myTurn)
        {
            return;
        }
        Message request = new Message();
        request.setType(MessageType.CONNECT4_MOVE_REQUEST);
        request.setMatchId(matchId);
        request.setCellIndex(column);
        NetworkManager.sendAsync(request);
    }

    @Override
    public void onPush(final Message message)
    {
        MessageType type = message.getType();
        boolean isType = type == MessageType.CONNECT4_MATCH_FOUND || type == MessageType.CONNECT4_UPDATE
            || type == MessageType.CONNECT4_RESULT || type == MessageType.CONNECT4_MOVE_REJECTED;
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

        if (type == MessageType.CONNECT4_MATCH_FOUND)
        {
            matchId = message.getMatchId();
            mySymbol = message.getSymbol();
            opponentUsername = message.getOpponentUsername();
            applyBoardState(message.getBoardState());
            cardLayout.show(cards, BOARD);
            pack();
            setLocationRelativeTo(null);
        }
        else if (type == MessageType.CONNECT4_UPDATE)
        {
            applyBoardState(message.getBoardState());
            myTurn = message.getSymbol().equals(mySymbol);
            updateStatus();
        }
        else if (type == MessageType.CONNECT4_MOVE_REJECTED)
        {
            applyBoardState(message.getBoardState());
            statusLabel.setText(message.getErrorText());
        }
        else if (type == MessageType.CONNECT4_RESULT)
        {
            applyBoardState(message.getBoardState());
            gameOver = true;
            boardPanel.winningLine = message.getWinningLine();
            String result = message.getMatchResult();
            String text = "WIN".equals(result) ? "You won!"
                : "LOSE".equals(result) ? "You lost."
                : "OPPONENT_LEFT".equals(result) ? "Your opponent left - you win!"
                : "It's a draw.";
            statusLabel.setText(text);
            boardPanel.repaint();

            String shareText = "WIN".equals(result) || "OPPONENT_LEFT".equals(result)
                ? "I won a Connect Four match on Vertex!"
                : "It's a draw".equals(text) ? "I drew a Connect Four match on Vertex!" : null;

            SnakeGameOverDialog.show(this, 0, text, shareText, new SnakeGameOverDialog.Choice()
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
                public void onClose() { ConnectFourWindow.this.dispose(); }
            });
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

    private void updateStatus()
    {
        String opponentLabel = opponentUsername != null ? opponentUsername : "opponent";
        statusLabel.setText(myTurn ? "Your move (" + mySymbol + ")" : opponentLabel + "'s move...");
    }

    private class BoardPanel extends JPanel
    {
        private static final int CELL = 60;
        private int[] winningLine;

        BoardPanel()
        {
            setPreferredSize(new Dimension(CELL * ConnectFourMatch.COLS, CELL * ConnectFourMatch.ROWS));
            setBackground(new Color(40, 70, 160));
            addMouseListener(new MouseAdapter()
            {
                public void mouseClicked(MouseEvent e)
                {
                    int column = e.getX() / CELL;
                    makeMove(column);
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.applyAntialiasing(g2);

            for (int row = 0; row < ConnectFourMatch.ROWS; row++)
            {
                for (int col = 0; col < ConnectFourMatch.COLS; col++)
                {
                    int index = row * ConnectFourMatch.COLS + col;
                    char piece = index < board.length ? board[index] : '.';
                    // Board is stored bottom-up (row 0 = bottom row), flip for on-screen drawing.
                    int screenRow = ConnectFourMatch.ROWS - 1 - row;

                    Color color = piece == 'R' ? new Color(220, 70, 70)
                        : piece == 'Y' ? new Color(230, 200, 60)
                        : new Color(20, 40, 100);

                    boolean highlighted = isWinningCell(index);
                    g2.setColor(color);
                    Ellipse2D.Double circle = new Ellipse2D.Double(
                        col * CELL + 6, screenRow * CELL + 6, CELL - 12, CELL - 12);
                    g2.fill(circle);

                    if (highlighted)
                    {
                        g2.setColor(Color.WHITE);
                        g2.setStroke(new java.awt.BasicStroke(3));
                        g2.draw(circle);
                    }
                }
            }
            g2.dispose();
        }

        private boolean isWinningCell(int index)
        {
            if (winningLine == null) return false;
            for (int i = 0; i < winningLine.length; i++)
            {
                if (winningLine[i] == index) return true;
            }
            return false;
        }
    }
}
