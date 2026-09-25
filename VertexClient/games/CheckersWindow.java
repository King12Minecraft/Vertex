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
 * CheckersWindow
 * --------------
 * Online ranked Checkers, 1v1 - same mode-select/searching/board shell
 * as Connect Four/Chess. Click a piece then click a destination
 * square; the server (CheckersMatch) is the sole authority on which
 * moves are legal, including mandatory captures and multi-jump
 * continuation - this window just forwards clicks and redraws
 * whatever board state comes back.
 *
 * Embedded in MainMenu's game-host slot rather than its own window -
 * see ChessWindow/ReversiWindow's javadoc for the pattern. Never
 * confirmed before leaving mid-match even as its own window, so
 * requestLeave() here stays unconditional.
 */
public class CheckersWindow extends JPanel implements NetworkManager.PushListener, EmbeddedGamePanel
{
    private static final String MODE_SELECT = "MODE_SELECT";
    private static final String SEARCHING = "SEARCHING";
    private static final String BOARD = "BOARD";

    /** "Practice" mode's AI - registered once per JVM, same pattern the other ai.search-backed games use. */
    private static final String AI_GAME_ID = "checkers-practice";
    private static final CheckersGameModel AI_MODEL = new CheckersGameModel();
    static
    {
        AiKernel.register(AI_GAME_ID,
            new GenericBotStrategy<CheckersState, CheckersMove>(AI_MODEL, CheckersGameModel.BLACK, 5),
            new RandomMoveStrategy<CheckersState, CheckersMove>(AI_MODEL, CheckersGameModel.BLACK));
    }

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);

    private JLabel searchingLabel;
    private BoardPanel boardPanel;
    private JLabel statusLabel;
    private ThemedButton hintButton;

    private boolean isPracticeMode = false;
    private PracticeMatch<CheckersState, CheckersMove> practiceMatch;

    private String matchId;
    private String mySymbol;
    private String opponentUsername;
    private char[] board = new char[CheckersMatch.SIZE * CheckersMatch.SIZE];
    private boolean myTurn;
    private boolean gameOver;
    private Integer selectedIndex;

    public CheckersWindow()
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

        JLabel title = new JLabel("Checkers");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        JLabel subtitle = new JLabel("Standard rules, mandatory captures.");
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

        JLabel title = new JLabel("Checkers");
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
        request.setType(MessageType.CHECKERS_FIND_MATCH_REQUEST);
        boolean sent = NetworkManager.sendAsync(request);
        if (!sent)
        {
            searchingLabel.setText("Can't reach the server - is it running?");
        }
    }

    private void leaveMatch()
    {
        Message request = new Message();
        request.setType(MessageType.CHECKERS_LEAVE_QUEUE_REQUEST);
        request.setMatchId(matchId);
        NetworkManager.sendAsync(request);
    }

    private void trySelectOrMove(int index)
    {
        if (isPracticeMode)
        {
            tryPracticeSelectOrMove(index);
            return;
        }

        if (gameOver || !myTurn)
        {
            return;
        }
        char piece = board[index];
        boolean isMyPiece = piece != '.' && (Character.toLowerCase(piece) == 'r') == "RED".equals(mySymbol);

        if (selectedIndex == null)
        {
            if (isMyPiece)
            {
                selectedIndex = index;
                boardPanel.repaint();
            }
            return;
        }

        if (isMyPiece)
        {
            selectedIndex = index;
            boardPanel.repaint();
            return;
        }

        Message request = new Message();
        request.setType(MessageType.CHECKERS_MOVE_REQUEST);
        request.setMatchId(matchId);
        request.setCellIndex(selectedIndex);
        request.setChessToSquare(index);
        NetworkManager.sendAsync(request);
        selectedIndex = null;
        boardPanel.repaint();
    }

    // ==================== PRACTICE MODE (local, offline) ====================

    private void startPracticeMatch()
    {
        mySymbol = "RED";
        opponentUsername = "CPU";
        selectedIndex = null;
        practiceMatch = new PracticeMatch<CheckersState, CheckersMove>(AI_MODEL, CheckersGameModel.newBoard(), AI_GAME_ID,
            CheckersGameModel.RED, CheckersGameModel.BLACK, CheckersGameModel.RED);
        cardLayout.show(cards, BOARD);
        hintButton.setVisible(true);
        gameOver = false;
        refreshPracticeBoard();
        settleTurn();
    }

    private void tryPracticeSelectOrMove(int index)
    {
        if (gameOver || !practiceMatch.isHumanTurn())
        {
            return;
        }
        boardPanel.hintMove = null;

        boolean forcedContinuation = practiceMatch.getState().mustContinueFrom != null;
        char piece = board[index];
        boolean isMyPiece = piece != '.' && (Character.toLowerCase(piece) == 'r') == "RED".equals(mySymbol);

        if (selectedIndex == null)
        {
            if (isMyPiece)
            {
                selectedIndex = index;
                boardPanel.repaint();
            }
            return;
        }

        // Re-selecting a different one of my own pieces - only allowed when not mid a
        // mandatory multi-jump (the real rule: that same piece must keep jumping).
        if (isMyPiece && !forcedContinuation && index != selectedIndex.intValue())
        {
            selectedIndex = index;
            boardPanel.repaint();
            return;
        }

        boolean applied = practiceMatch.humanMove(new CheckersMove(selectedIndex, index));
        if (!applied)
        {
            return;
        }
        selectedIndex = null;
        refreshPracticeBoard();
        settleTurn();
    }

    /** Dispatches after every practice-mode state change: reports the result if the match just ended, triggers the bot (chaining through repeated jumps on its own turn too) if it's now the bot's turn, or - if it's the human's turn but they're mid a mandatory multi-jump - auto-selects the continuing piece so they only need to click the next landing square. */
    private void settleTurn()
    {
        if (practiceMatch.isOver())
        {
            handlePracticeGameOver();
            return;
        }

        if (practiceMatch.isHumanTurn())
        {
            CheckersState state = practiceMatch.getState();
            if (state.mustContinueFrom != null)
            {
                selectedIndex = state.mustContinueFrom;
                statusLabel.setText("Keep jumping with the same piece!");
            }
            else
            {
                statusLabel.setText("Your move (RED)");
            }
            boardPanel.repaint();
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
                settleTurn();
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
        boardPanel.hintMove = practiceMatch.suggestMove();
        boardPanel.repaint();
    }

    private void handlePracticeGameOver()
    {
        gameOver = true;
        hintButton.setVisible(false);
        selectedIndex = null;
        Integer winner = practiceMatch.getWinnerIndex();
        String text = winner == null ? "It's a draw."
            : winner == CheckersGameModel.RED ? "You won!" : "You lost.";
        statusLabel.setText(text);

        String shareText = winner != null && winner == CheckersGameModel.RED
            ? "I won a Checkers practice match on Vertex!" : null;
        SnakeGameOverDialog.show(this, 0, text, shareText, new SnakeGameOverDialog.Choice()
        {
            public void onPlayAgain() { startPracticeMatch(); }
            public void onClose() { MainMenu.getInstance().returnToGames(); }
        });
    }

    private void refreshPracticeBoard()
    {
        board = practiceMatch.getState().board;
        boardPanel.repaint();
    }

    @Override
    public void onPush(final Message message)
    {
        MessageType type = message.getType();
        boolean isType = type == MessageType.CHECKERS_MATCH_FOUND || type == MessageType.CHECKERS_UPDATE
            || type == MessageType.CHECKERS_RESULT || type == MessageType.CHECKERS_MOVE_REJECTED;
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

        if (type == MessageType.CHECKERS_MATCH_FOUND)
        {
            matchId = message.getMatchId();
            mySymbol = message.getSymbol();
            opponentUsername = message.getOpponentUsername();
            applyBoardState(message.getBoardState());
            cardLayout.show(cards, BOARD);
        }
        else if (type == MessageType.CHECKERS_UPDATE)
        {
            applyBoardState(message.getBoardState());
            selectedIndex = null;
            myTurn = message.getSymbol().equals(mySymbol);
            updateStatus();
        }
        else if (type == MessageType.CHECKERS_MOVE_REJECTED)
        {
            applyBoardState(message.getBoardState());
            selectedIndex = null;
            statusLabel.setText(message.getErrorText());
        }
        else if (type == MessageType.CHECKERS_RESULT)
        {
            applyBoardState(message.getBoardState());
            gameOver = true;
            String result = message.getMatchResult();
            String text = "WIN".equals(result) ? "You won!"
                : "LOSE".equals(result) ? "You lost."
                : "OPPONENT_LEFT".equals(result) ? "Your opponent left - you win!"
                : "Game over.";
            statusLabel.setText(text);
            boardPanel.repaint();

            String shareText = "WIN".equals(result) || "OPPONENT_LEFT".equals(result)
                ? "I won a Checkers match on Vertex!" : null;

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
        private static final int CELL = 56;
        private CheckersMove hintMove;

        BoardPanel()
        {
            setPreferredSize(new Dimension(CELL * CheckersMatch.SIZE, CELL * CheckersMatch.SIZE));
            addMouseListener(new MouseAdapter()
            {
                public void mouseClicked(MouseEvent e)
                {
                    int col = e.getX() / CELL;
                    int row = e.getY() / CELL;
                    if (row >= 0 && row < CheckersMatch.SIZE && col >= 0 && col < CheckersMatch.SIZE)
                    {
                        trySelectOrMove(row * CheckersMatch.SIZE + col);
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

            for (int row = 0; row < CheckersMatch.SIZE; row++)
            {
                for (int col = 0; col < CheckersMatch.SIZE; col++)
                {
                    boolean dark = (row + col) % 2 == 1;
                    g2.setColor(dark ? new Color(90, 60, 40) : new Color(220, 200, 170));
                    g2.fillRect(col * CELL, row * CELL, CELL, CELL);

                    int index = row * CheckersMatch.SIZE + col;
                    if (selectedIndex != null && selectedIndex == index)
                    {
                        g2.setColor(new Color(255, 230, 80, 120));
                        g2.fillRect(col * CELL, row * CELL, CELL, CELL);
                    }
                    if (hintMove != null && (hintMove.from == index || hintMove.to == index))
                    {
                        g2.setColor(new Color(90, 220, 120, 130));
                        g2.fillRect(col * CELL, row * CELL, CELL, CELL);
                    }

                    char piece = index < board.length ? board[index] : '.';
                    if (piece != '.')
                    {
                        drawPiece(g2, col, row, piece);
                    }
                }
            }
            g2.dispose();
        }

        private void drawPiece(Graphics2D g2, int col, int row, char piece)
        {
            boolean isRed = Character.toLowerCase(piece) == 'r';
            boolean isKing = Character.isUpperCase(piece);
            int pad = 8;
            Ellipse2D.Double circle = new Ellipse2D.Double(
                col * CELL + pad, row * CELL + pad, CELL - pad * 2, CELL - pad * 2);

            g2.setColor(isRed ? new Color(200, 60, 60) : new Color(40, 40, 40));
            g2.fill(circle);
            g2.setColor(isRed ? new Color(140, 30, 30) : new Color(10, 10, 10));
            g2.setStroke(new java.awt.BasicStroke(2));
            g2.draw(circle);

            if (isKing)
            {
                g2.setColor(new Color(255, 215, 90));
                g2.setFont(UITheme.FONT_NAV_BOLD);
                String star = "K";
                int textWidth = g2.getFontMetrics().stringWidth(star);
                g2.drawString(star, col * CELL + CELL / 2 - textWidth / 2, row * CELL + CELL / 2 + 5);
            }
        }
    }
}
