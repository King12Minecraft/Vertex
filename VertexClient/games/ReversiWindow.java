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
import ui.GameModeCard;
import ai.AiKernel;
import ai.search.GenericBotStrategy;
import ai.search.RandomMoveStrategy;
import ai.search.PracticeMatch;

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
import java.awt.FlowLayout;
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
 * ReversiWindow
 * -------------
 * Online ranked Reversi/Othello, 1v1 - click a cell to place a piece
 * there. ReversiMatch is the sole authority on which moves actually
 * flank something (the only legal moves in Reversi); this window just
 * forwards the click and redraws whatever board state comes back,
 * including automatic turn-skips when a player has no legal move.
 */
public class ReversiWindow extends JFrame implements NetworkManager.PushListener
{
    private static final String MODE_SELECT = "MODE_SELECT";
    private static final String SEARCHING = "SEARCHING";
    private static final String BOARD = "BOARD";

    /** "Practice" mode's AI - registered once per JVM, same pattern ConnectFourWindow/TicTacToePracticeMatch use. */
    private static final String AI_GAME_ID = "reversi-practice";
    private static final ReversiGameModel AI_MODEL = new ReversiGameModel();
    static
    {
        AiKernel.register(AI_GAME_ID,
            new GenericBotStrategy<char[], Integer>(AI_MODEL, ReversiGameModel.WHITE, 4),
            new RandomMoveStrategy<char[], Integer>(AI_MODEL, ReversiGameModel.WHITE));
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
    private char[] board = new char[ReversiMatch.SIZE * ReversiMatch.SIZE];
    private boolean myTurn;
    private boolean gameOver;

    public ReversiWindow()
    {
        super("Vertex - Reversi");
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
                if (!isPracticeMode) leaveMatch();
                NetworkManager.removePushListener(ReversiWindow.this);
            }
        });
    }

    private JPanel createModeSelectScreen()
    {
        RoundedPanel panel = new RoundedPanel(ThemeColor.BG_APP, 0);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(50, 60, 50, 60));
        panel.setPreferredSize(new Dimension(460, 320));

        JLabel title = new JLabel("Reversi");
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

        return panel;
    }

    private void chooseMode(boolean online)
    {
        isPracticeMode = !online;
        if (online)
        {
            cardLayout.show(cards, SEARCHING);
            pack();
            setLocationRelativeTo(null);
            findMatch();
        }
        else
        {
            startPracticeMatch();
        }
    }

    private JPanel createSearchingScreen()
    {
        RoundedPanel panel = new RoundedPanel(ThemeColor.BG_APP, 0);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(60, 60, 60, 60));
        panel.setPreferredSize(new Dimension(360, 220));

        JLabel title = new JLabel("Reversi");
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

        hintButton = new ThemedButton("Hint", false);
        hintButton.setPreferredSize(new Dimension(90, 34));
        hintButton.setVisible(false);
        hintButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { showHint(); }
        });
        JPanel bottomRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        bottomRow.setOpaque(false);
        bottomRow.setBorder(new EmptyBorder(12, 0, 0, 0));
        bottomRow.add(hintButton);
        wrap.add(bottomRow, BorderLayout.SOUTH);

        return wrap;
    }

    private void findMatch()
    {
        Message request = new Message();
        request.setType(MessageType.REVERSI_FIND_MATCH_REQUEST);
        boolean sent = NetworkManager.sendAsync(request);
        if (!sent)
        {
            searchingLabel.setText("Can't reach the server - is it running?");
        }
    }

    private void leaveMatch()
    {
        Message request = new Message();
        request.setType(MessageType.REVERSI_LEAVE_QUEUE_REQUEST);
        request.setMatchId(matchId);
        NetworkManager.sendAsync(request);
    }

    private void tryMove(int index)
    {
        if (isPracticeMode)
        {
            makePracticeMove(index);
            return;
        }

        if (gameOver || !myTurn) return;
        Message request = new Message();
        request.setType(MessageType.REVERSI_MOVE_REQUEST);
        request.setMatchId(matchId);
        request.setCellIndex(index);
        NetworkManager.sendAsync(request);
    }

    // ==================== PRACTICE MODE (local, offline) ====================

    private void startPracticeMatch()
    {
        mySymbol = "BLACK";
        opponentUsername = "CPU";
        practiceMatch = new PracticeMatch<char[], Integer>(AI_MODEL, ReversiGameModel.newBoard(), AI_GAME_ID,
            ReversiGameModel.BLACK, ReversiGameModel.WHITE, ReversiGameModel.BLACK);
        cardLayout.show(cards, BOARD);
        pack();
        setLocationRelativeTo(null);
        hintButton.setVisible(true);
        gameOver = false;
        refreshPracticeBoard();
        settleTurn();
    }

    private void makePracticeMove(int index)
    {
        if (gameOver || !practiceMatch.isHumanTurn())
        {
            return;
        }
        boardPanel.hintCell = null;
        boolean applied = practiceMatch.humanMove(index);
        if (!applied)
        {
            return;
        }
        refreshPracticeBoard();
        settleTurn();
    }

    /** Applies real Reversi's forced-pass rule after every state change: if the game just ended, reports the result; if it's now the bot's turn, triggers it; if it's the human's turn but they have no real move (only ReversiGameModel.PASS available), auto-plays the pass and re-checks - the real rule is that passing isn't optional, so there's nothing for the player to click. */
    private void settleTurn()
    {
        if (practiceMatch.isOver())
        {
            handlePracticeGameOver();
            return;
        }

        if (practiceMatch.isHumanTurn())
        {
            java.util.List<Integer> humanMoves = practiceMatch.legalMovesForCurrentPlayer();
            if (humanMoves.size() == 1 && humanMoves.get(0) == ReversiGameModel.PASS)
            {
                statusLabel.setText("No legal move - passing...");
                practiceMatch.humanMove(ReversiGameModel.PASS);
                refreshPracticeBoard();
                settleTurn();
                return;
            }
            statusLabel.setText("Your move (BLACK)");
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
        Integer suggestion = practiceMatch.suggestMove();
        boardPanel.hintCell = suggestion == ReversiGameModel.PASS ? null : suggestion;
        boardPanel.repaint();
    }

    private void handlePracticeGameOver()
    {
        gameOver = true;
        hintButton.setVisible(false);
        Integer winner = practiceMatch.getWinnerIndex();
        String text = winner == null ? "It's a draw."
            : winner == ReversiGameModel.BLACK ? "You won!" : "You lost.";
        statusLabel.setText(text);

        String shareText = winner != null && winner == ReversiGameModel.BLACK
            ? "I won a Reversi practice match on Vertex!" : null;
        SnakeGameOverDialog.show(this, 0, text, shareText, new SnakeGameOverDialog.Choice()
        {
            public void onPlayAgain() { startPracticeMatch(); }
            public void onClose() { ReversiWindow.this.dispose(); }
        });
    }

    private void refreshPracticeBoard()
    {
        board = practiceMatch.getState();
        boardPanel.repaint();
    }

    @Override
    public void onPush(final Message message)
    {
        MessageType type = message.getType();
        boolean isType = type == MessageType.REVERSI_MATCH_FOUND || type == MessageType.REVERSI_UPDATE
            || type == MessageType.REVERSI_RESULT || type == MessageType.REVERSI_MOVE_REJECTED;
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

        if (type == MessageType.REVERSI_MATCH_FOUND)
        {
            matchId = message.getMatchId();
            mySymbol = message.getSymbol();
            opponentUsername = message.getOpponentUsername();
            applyBoardState(message.getBoardState());
            myTurn = "BLACK".equals(mySymbol);
            updateStatus();
            cardLayout.show(cards, BOARD);
            pack();
            setLocationRelativeTo(null);
        }
        else if (type == MessageType.REVERSI_UPDATE)
        {
            applyBoardState(message.getBoardState());
            myTurn = message.getSymbol().equals(mySymbol);
            updateStatus();
        }
        else if (type == MessageType.REVERSI_MOVE_REJECTED)
        {
            applyBoardState(message.getBoardState());
            statusLabel.setText(message.getErrorText());
        }
        else if (type == MessageType.REVERSI_RESULT)
        {
            applyBoardState(message.getBoardState());
            gameOver = true;
            String result = message.getMatchResult();
            String text = "WIN".equals(result) ? "You won with " + message.getScore() + " pieces!"
                : "LOSE".equals(result) ? "You lost - " + message.getScore() + " pieces."
                : "DRAW".equals(result) ? "It's a draw."
                : "OPPONENT_LEFT".equals(result) ? "Your opponent left - you win!"
                : "Game over.";
            statusLabel.setText(text);
            boardPanel.repaint();

            String shareText = ("WIN".equals(result) || "OPPONENT_LEFT".equals(result))
                ? "I won a Reversi match on Vertex!" : null;
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
                public void onClose() { ReversiWindow.this.dispose(); }
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
        private static final int CELL = 52;
        private Integer hintCell;

        BoardPanel()
        {
            setPreferredSize(new Dimension(CELL * ReversiMatch.SIZE, CELL * ReversiMatch.SIZE));
            addMouseListener(new MouseAdapter()
            {
                public void mouseClicked(MouseEvent e)
                {
                    int col = e.getX() / CELL;
                    int row = e.getY() / CELL;
                    if (row >= 0 && row < ReversiMatch.SIZE && col >= 0 && col < ReversiMatch.SIZE)
                    {
                        tryMove(row * ReversiMatch.SIZE + col);
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

            for (int row = 0; row < ReversiMatch.SIZE; row++)
            {
                for (int col = 0; col < ReversiMatch.SIZE; col++)
                {
                    g2.setColor(new Color(40, 100, 60));
                    g2.fillRect(col * CELL, row * CELL, CELL - 1, CELL - 1);

                    int index = row * ReversiMatch.SIZE + col;
                    char piece = index < board.length ? board[index] : '.';
                    if (piece != '.')
                    {
                        drawPiece(g2, col, row, piece);
                    }
                    else if (hintCell != null && hintCell == index)
                    {
                        g2.setColor(new Color(255, 255, 255, 160));
                        g2.setStroke(new java.awt.BasicStroke(3f));
                        int pad = 10;
                        g2.drawOval(col * CELL + pad, row * CELL + pad, CELL - pad * 2, CELL - pad * 2);
                    }
                }
            }
            g2.dispose();
        }

        private void drawPiece(Graphics2D g2, int col, int row, char piece)
        {
            boolean isBlack = piece == 'B';
            int pad = 6;
            Ellipse2D.Double circle = new Ellipse2D.Double(
                col * CELL + pad, row * CELL + pad, CELL - pad * 2, CELL - pad * 2);

            g2.setColor(isBlack ? new Color(20, 20, 20) : new Color(240, 240, 240));
            g2.fill(circle);
            g2.setColor(isBlack ? Color.BLACK : new Color(200, 200, 200));
            g2.setStroke(new java.awt.BasicStroke(1.5f));
            g2.draw(circle);
        }
    }
}
