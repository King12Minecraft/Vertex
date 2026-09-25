package games;
import net.MessageType;
import ui.ThemedButton;
import ui.GameModeCard;
import theme.ThemeManager;
import theme.UITheme;
import theme.ThemeColor;
import ui.RoundedPanel;
import net.NetworkManager;
import pages.MainMenu;
import net.Message;
import ai.AiKernel;
import ai.search.GenericBotStrategy;
import ai.search.RandomMoveStrategy;
import ai.search.PracticeMatch;

import javax.swing.BoxLayout;
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
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;

/**
 * ConnectFourWindow
 * -----------------
 * Online ranked Connect Four, 1v1 - same mode-select/searching/board
 * shell shape as the other short 1v1 games (Tic-Tac-Toe, Chess). Click
 * a column to drop a disc into the lowest open row.
 *
 * Embedded in MainMenu's game-host slot rather than its own window -
 * see ChessWindow/ReversiWindow's javadoc for the pattern. Never
 * confirmed before leaving mid-match even as its own window, so
 * requestLeave() here stays unconditional, same as Reversi.
 */
public class ConnectFourWindow extends JPanel implements NetworkManager.PushListener, EmbeddedGamePanel
{
    private static final String MODE_SELECT = "MODE_SELECT";
    private static final String SEARCHING = "SEARCHING";
    private static final String BOARD = "BOARD";

    /** "Practice" mode's AI - registered once per JVM, same pattern TicTacToePracticeMatch uses. Reused by every practice match rather than rebuilt each time, since GenericBotStrategy/RandomMoveStrategy are stateless. */
    private static final String AI_GAME_ID = "connect-four-practice";
    private static final ConnectFourGameModel AI_MODEL = new ConnectFourGameModel();
    static
    {
        AiKernel.register(AI_GAME_ID,
            new GenericBotStrategy<char[], Integer>(AI_MODEL, ConnectFourGameModel.YELLOW, 6),
            new RandomMoveStrategy<char[], Integer>(AI_MODEL, ConnectFourGameModel.YELLOW));
    }

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);

    private JLabel searchingLabel;
    private BoardPanel boardPanel;
    private JLabel statusLabel;
    private ThemedButton hintButton;

    private boolean isPracticeMode = false;
    private PracticeMatch<char[], Integer> practiceMatch;

    private String matchId;
    private String mySymbol;
    private String opponentUsername;
    private char[] board = new char[ConnectFourMatch.COLS * ConnectFourMatch.ROWS];
    private boolean myTurn;
    private boolean gameOver;

    public ConnectFourWindow()
    {
        setLayout(new BorderLayout());
        java.util.Arrays.fill(board, '.');

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
        if (!isPracticeMode) leaveMatch();
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

        JLabel title = new JLabel("Connect Four");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        JLabel subtitle = new JLabel("Choose how you want to play.");
        subtitle.setFont(UITheme.FONT_SUBHEAD);
        subtitle.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setBorder(new EmptyBorder(6, 0, 24, 0));
        panel.add(subtitle);

        JPanel tileRow = new JPanel(new java.awt.GridLayout(1, 2, 16, 0));
        tileRow.setOpaque(false);
        tileRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        tileRow.setMaximumSize(new Dimension(2000, 150));

        tileRow.add(new GameModeCard("Play Online", "Ranked 1v1 against a real opponent.",
            ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_START), new GameModeCard.ClickListener()
            {
                public void onClick() { chooseMode(true); }
            }));

        tileRow.add(new GameModeCard("Practice Mode", "Against the computer, fully offline.",
            ThemeManager.getColor(ThemeColor.TEXT_MUTED), new GameModeCard.ClickListener()
            {
                public void onClick() { chooseMode(false); }
            }));

        panel.add(tileRow);

        return wrapper;
    }

    private void chooseMode(boolean online)
    {
        isPracticeMode = !online;
        if (online)
        {
            cardLayout.show(cards, SEARCHING);
            findMatch();
        }
        else
        {
            startPracticeMatch();
        }
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

        statusLabel = new JLabel("Waiting...");
        statusLabel.setFont(UITheme.FONT_NAV_BOLD);
        statusLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        statusLabel.setBorder(new EmptyBorder(0, 0, 12, 0));
        wrap.add(statusLabel, BorderLayout.NORTH);

        boardPanel = new BoardPanel();
        JPanel boardCenterer = new JPanel(new GridBagLayout());
        boardCenterer.setOpaque(false);
        boardCenterer.add(boardPanel, new GridBagConstraints());
        wrap.add(boardCenterer, BorderLayout.CENTER);

        ThemedButton leaveButton = new ThemedButton("Leave", false);
        leaveButton.setPreferredSize(new Dimension(90, 34));
        leaveButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                if (requestLeave()) { MainMenu.getInstance().returnToGames(); }
            }
        });

        hintButton = new ThemedButton("Hint", false);
        hintButton.setPreferredSize(new Dimension(90, 34));
        hintButton.setVisible(false);
        hintButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { showHint(); }
        });
        JPanel bottomRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        bottomRow.setOpaque(false);
        bottomRow.setBorder(new EmptyBorder(12, 0, 0, 0));
        bottomRow.add(hintButton);
        bottomRow.add(leaveButton);
        wrap.add(bottomRow, BorderLayout.SOUTH);

        return wrap;
    }

    private void findMatch()
    {
        Message request = new Message();
        request.setType(MessageType.CONNECT4_FIND_MATCH_REQUEST);
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
        request.setType(MessageType.CONNECT4_LEAVE_QUEUE_REQUEST);
        request.setMatchId(matchId);
        NetworkManager.sendAsync(request);
    }

    private void makeMove(int column)
    {
        if (isPracticeMode)
        {
            makePracticeMove(column);
            return;
        }

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

    // ==================== PRACTICE MODE (local, offline) ====================

    private void startPracticeMatch()
    {
        mySymbol = "RED";
        opponentUsername = "CPU";
        practiceMatch = new PracticeMatch<char[], Integer>(AI_MODEL, ConnectFourGameModel.newBoard(), AI_GAME_ID,
            ConnectFourGameModel.RED, ConnectFourGameModel.YELLOW, ConnectFourGameModel.RED);
        cardLayout.show(cards, BOARD);
        hintButton.setVisible(true);
        gameOver = false;
        refreshPracticeBoard();
        statusLabel.setText("Your move (RED)");
    }

    private void makePracticeMove(int column)
    {
        if (gameOver || !practiceMatch.isHumanTurn())
        {
            return;
        }
        boardPanel.hintColumn = null;
        boolean applied = practiceMatch.humanMove(column);
        if (!applied)
        {
            return;
        }
        refreshPracticeBoard();

        if (practiceMatch.isOver())
        {
            handlePracticeGameOver();
        }
        else
        {
            triggerBotMove();
        }
    }

    private void triggerBotMove()
    {
        statusLabel.setText("CPU is thinking...");
        Timer botTimer = new Timer(500, null);
        botTimer.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                ((Timer) e.getSource()).stop();
                practiceMatch.botMove();
                refreshPracticeBoard();
                if (practiceMatch.isOver())
                {
                    handlePracticeGameOver();
                }
                else
                {
                    statusLabel.setText("Your move (RED)");
                }
            }
        });
        botTimer.setRepeats(false);
        botTimer.start();
    }

    private void showHint()
    {
        if (gameOver || !practiceMatch.isHumanTurn())
        {
            return;
        }
        boardPanel.hintColumn = practiceMatch.suggestMove();
        boardPanel.repaint();
    }

    private void handlePracticeGameOver()
    {
        gameOver = true;
        hintButton.setVisible(false);
        Integer winner = practiceMatch.getWinnerIndex();
        String text = winner == null ? "It's a draw."
            : winner == ConnectFourGameModel.RED ? "You won!" : "You lost.";
        statusLabel.setText(text);

        String shareText = winner != null && winner == ConnectFourGameModel.RED
            ? "I won a Connect Four practice match on Vertex!" : null;
        SnakeGameOverDialog.show(this, 0, text, shareText, new SnakeGameOverDialog.Choice()
        {
            public void onPlayAgain() { startPracticeMatch(); }
            public void onClose() { MainMenu.getInstance().returnToGames(); }
        });
    }

    private void refreshPracticeBoard()
    {
        // Practice mode doesn't compute a winning-line overlay (ConnectFourGameModel only
        // reports who won, not which 4 cells) - the status text alone reports the outcome.
        board = practiceMatch.getState();
        boardPanel.repaint();
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
                    findMatch();
                }
                public void onClose() { MainMenu.getInstance().returnToGames(); }
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
        private Integer hintColumn;

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

            if (hintColumn != null)
            {
                int screenRow = ConnectFourMatch.ROWS - 1 - lowestEmptyRowForHint(hintColumn);
                g2.setColor(new Color(255, 255, 255, 200));
                g2.setStroke(new java.awt.BasicStroke(3f));
                g2.drawOval(hintColumn * CELL + 6, screenRow * CELL + 6, CELL - 12, CELL - 12);
            }

            g2.dispose();
        }

        private int lowestEmptyRowForHint(int column)
        {
            for (int row = 0; row < ConnectFourMatch.ROWS; row++)
            {
                int index = row * ConnectFourMatch.COLS + column;
                if (index >= board.length || board[index] == '.')
                {
                    return row;
                }
            }
            return 0;
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
