package games;

import net.Message;
import net.MessageType;
import net.NetworkManager;
import pages.MainMenu;
import theme.ThemeColor;
import theme.ThemeManager;
import ui.RoundedPanel;
import ui.ThemedButton;
import ui.GameModeCard;
import theme.UITheme;
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

/**
 * DotsAndBoxesWindow
 * -------------------
 * Online Dots and Boxes, 1v1. Click near a line between two dots to
 * draw it - DotsAndBoxesMatch is the sole authority on which lines
 * are legal and whose turn it is; this window just forwards the
 * click (as a line index, found by proximity - see findNearestLine)
 * and redraws whatever board state comes back.
 *
 * Embedded in MainMenu's game-host slot rather than its own window -
 * see ChessWindow/ReversiWindow's javadoc for the pattern. Never
 * confirmed before leaving mid-match even as its own window, so
 * requestLeave() here stays unconditional.
 */
public class DotsAndBoxesWindow extends JPanel implements NetworkManager.PushListener, EmbeddedGamePanel
{
    private static final String MODE_SELECT = "MODE_SELECT";
    private static final String SEARCHING = "SEARCHING";
    private static final String BOARD = "BOARD";

    private static final Color[] PLAYER_COLORS = { new Color(220, 70, 70), new Color(70, 140, 220) };

    /** "Practice" mode's AI - registered once per JVM, same pattern ConnectFourWindow/ReversiWindow use. Depth kept shallow (branching is up to 40 early on, far wider than Connect Four/Reversi) to stay responsive. */
    private static final String AI_GAME_ID = "dots-and-boxes-practice";
    private static final DotsAndBoxesGameModel AI_MODEL = new DotsAndBoxesGameModel();
    static
    {
        AiKernel.register(AI_GAME_ID,
            new GenericBotStrategy<char[], Integer>(AI_MODEL, DotsAndBoxesGameModel.PLAYER_B, 3),
            new RandomMoveStrategy<char[], Integer>(AI_MODEL, DotsAndBoxesGameModel.PLAYER_B));
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
    private int mySymbol = -1;
    private String opponentUsername;
    private char[] lines = new char[DotsAndBoxesMatch.LINE_COUNT];
    private char[] boxOwners = new char[DotsAndBoxesMatch.BOX_COUNT];
    private boolean gameOver;

    public DotsAndBoxesWindow()
    {
        setLayout(new BorderLayout());
        java.util.Arrays.fill(lines, '.');
        java.util.Arrays.fill(boxOwners, '.');

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

        JLabel title = new JLabel("Dots and Boxes");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        JLabel subtitle = new JLabel("Complete a box's 4th side to claim it and go again.");
        subtitle.setFont(UITheme.FONT_SUBHEAD);
        subtitle.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setBorder(new EmptyBorder(6, 0, 24, 0));
        panel.add(subtitle);

        JPanel tileRow = new JPanel(new java.awt.GridLayout(1, 2, 16, 0));
        tileRow.setOpaque(false);
        tileRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        tileRow.setMaximumSize(new Dimension(2000, 150));

        tileRow.add(new GameModeCard("Play Online", "1v1 against a real opponent.",
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

        JLabel title = new JLabel("Dots and Boxes");
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
        request.setType(MessageType.DOTS_FIND_MATCH_REQUEST);
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
        request.setType(MessageType.DOTS_LEAVE_QUEUE_REQUEST);
        request.setMatchId(matchId);
        NetworkManager.sendAsync(request);
    }

    private void drawLine(int lineIndex)
    {
        if (isPracticeMode)
        {
            makePracticeMove(lineIndex);
            return;
        }

        if (gameOver || lines[lineIndex] != '.') return;
        Message request = new Message();
        request.setType(MessageType.DOTS_LINE_REQUEST);
        request.setMatchId(matchId);
        request.setCellIndex(lineIndex);
        NetworkManager.sendAsync(request);
    }

    // ==================== PRACTICE MODE (local, offline) ====================

    private void startPracticeMatch()
    {
        mySymbol = DotsAndBoxesGameModel.PLAYER_A;
        opponentUsername = "CPU";
        practiceMatch = new PracticeMatch<char[], Integer>(AI_MODEL, DotsAndBoxesGameModel.newBoard(), AI_GAME_ID,
            DotsAndBoxesGameModel.PLAYER_A, DotsAndBoxesGameModel.PLAYER_B, DotsAndBoxesGameModel.PLAYER_A);
        cardLayout.show(cards, BOARD);
        hintButton.setVisible(true);
        gameOver = false;
        refreshPracticeBoard();
        statusLabel.setText("Your move");
    }

    private void makePracticeMove(int lineIndex)
    {
        if (gameOver || !practiceMatch.isHumanTurn() || lines[lineIndex] != '.')
        {
            return;
        }
        boardPanel.hintLine = null;
        boolean applied = practiceMatch.humanMove(lineIndex);
        if (!applied)
        {
            return;
        }
        refreshPracticeBoard();

        if (practiceMatch.isOver())
        {
            handlePracticeGameOver();
        }
        else if (practiceMatch.isHumanTurn())
        {
            // Completing a box earns another turn - the real Dots and Boxes rule.
            statusLabel.setText("You completed a box - go again!");
        }
        else
        {
            triggerBotMove();
        }
    }

    /** A single bot turn - if the bot completes a box, it earns another turn too, so this reschedules itself (with the same "thinking" delay) rather than handing control back to the human. */
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
                else if (!practiceMatch.isHumanTurn())
                {
                    triggerBotMove();
                }
                else
                {
                    statusLabel.setText("Your move");
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
        boardPanel.hintLine = practiceMatch.suggestMove();
        boardPanel.repaint();
    }

    private void handlePracticeGameOver()
    {
        gameOver = true;
        hintButton.setVisible(false);
        Integer winner = practiceMatch.getWinnerIndex();
        String text = winner == null ? "It's a draw."
            : winner == DotsAndBoxesGameModel.PLAYER_A ? "You won!" : "You lost.";
        statusLabel.setText(text);

        String shareText = winner != null && winner == DotsAndBoxesGameModel.PLAYER_A
            ? "I won a Dots and Boxes practice match on Vertex!" : null;
        SnakeGameOverDialog.show(this, 0, text, shareText, new SnakeGameOverDialog.Choice()
        {
            public void onPlayAgain() { startPracticeMatch(); }
            public void onClose() { MainMenu.getInstance().returnToGames(); }
        });
    }

    private void refreshPracticeBoard()
    {
        char[] state = practiceMatch.getState();
        lines = java.util.Arrays.copyOfRange(state, 0, DotsAndBoxesMatch.LINE_COUNT);
        boxOwners = java.util.Arrays.copyOfRange(state, DotsAndBoxesMatch.LINE_COUNT,
            DotsAndBoxesMatch.LINE_COUNT + DotsAndBoxesMatch.BOX_COUNT);
        boardPanel.repaint();
    }

    @Override
    public void onPush(final Message message)
    {
        MessageType type = message.getType();
        boolean isType = type == MessageType.DOTS_MATCH_FOUND || type == MessageType.DOTS_UPDATE
            || type == MessageType.DOTS_RESULT;
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

        if (type == MessageType.DOTS_MATCH_FOUND)
        {
            matchId = message.getMatchId();
            mySymbol = Integer.parseInt(message.getSymbol());
            opponentUsername = message.getOpponentUsername();
            applyBoardState(message.getBoardState());
            updateStatus(0);
            cardLayout.show(cards, BOARD);
        }
        else if (type == MessageType.DOTS_UPDATE)
        {
            applyBoardState(message.getBoardState());
            updateStatus(Integer.parseInt(message.getSymbol()));
        }
        else if (type == MessageType.DOTS_RESULT)
        {
            applyBoardState(message.getBoardState());
            gameOver = true;
            String result = message.getMatchResult();
            String text = "WIN".equals(result) ? "You won with " + message.getScore() + " boxes!"
                : "LOSE".equals(result) ? "You lost - " + message.getScore() + " boxes."
                : "DRAW".equals(result) ? "It's a draw."
                : "OPPONENT_LEFT".equals(result) ? "Your opponent left - you win!"
                : "Game over.";
            statusLabel.setText(text);
            boardPanel.repaint();

            String shareText = ("WIN".equals(result) || "OPPONENT_LEFT".equals(result))
                ? "I won a Dots and Boxes match on Vertex!" : null;
            SnakeGameOverDialog.show(this, message.getScore(), text, shareText, new SnakeGameOverDialog.Choice()
            {
                public void onPlayAgain()
                {
                    gameOver = false;
                    matchId = null;
                    java.util.Arrays.fill(lines, '.');
                    java.util.Arrays.fill(boxOwners, '.');
                    cardLayout.show(cards, SEARCHING);
                    findMatch();
                }
                public void onClose() { MainMenu.getInstance().returnToGames(); }
            });
        }
    }

    private void updateStatus(int turnPlayerIndex)
    {
        String opponentLabel = opponentUsername != null ? opponentUsername : "opponent";
        statusLabel.setText(turnPlayerIndex == mySymbol ? "Your move" : opponentLabel + "'s move...");
    }

    private void applyBoardState(String boardState)
    {
        if (boardState != null && boardState.length() >= DotsAndBoxesMatch.LINE_COUNT + DotsAndBoxesMatch.BOX_COUNT)
        {
            lines = boardState.substring(0, DotsAndBoxesMatch.LINE_COUNT).toCharArray();
            boxOwners = boardState.substring(DotsAndBoxesMatch.LINE_COUNT).toCharArray();
        }
        boardPanel.repaint();
    }

    private class BoardPanel extends JPanel
    {
        private static final int SPACING = 56;
        private static final int MARGIN = 24;
        private static final int DOT_RADIUS = 5;
        private static final int CLICK_TOLERANCE = 12;
        private Integer hintLine;

        BoardPanel()
        {
            int size = MARGIN * 2 + SPACING * DotsAndBoxesMatch.BOX_COLS;
            setPreferredSize(new Dimension(size, size));
            addMouseListener(new MouseAdapter()
            {
                public void mouseClicked(MouseEvent e)
                {
                    int nearest = findNearestLine(e.getX(), e.getY());
                    if (nearest >= 0) drawLine(nearest);
                }
            });
        }

        /** Finds the closest undrawn line to a click point, within CLICK_TOLERANCE pixels - returns -1 if nothing is close enough. Checking every line each click is cheap (only 40 of them) and avoids needing to precompute/cache clickable regions. */
        private int findNearestLine(int mouseX, int mouseY)
        {
            int best = -1;
            double bestDist = CLICK_TOLERANCE;

            for (int i = 0; i < DotsAndBoxesMatch.LINE_COUNT; i++)
            {
                if (lines[i] != '.') continue;
                double dist;
                if (i < 20)
                {
                    int row = i / DotsAndBoxesMatch.BOX_COLS, col = i % DotsAndBoxesMatch.BOX_COLS;
                    int x1 = MARGIN + col * SPACING, y1 = MARGIN + row * SPACING;
                    int x2 = MARGIN + (col + 1) * SPACING, y2 = y1;
                    dist = distanceToSegment(mouseX, mouseY, x1, y1, x2, y2);
                }
                else
                {
                    int vIndex = i - 20;
                    int row = vIndex / (DotsAndBoxesMatch.BOX_COLS + 1), col = vIndex % (DotsAndBoxesMatch.BOX_COLS + 1);
                    int x1 = MARGIN + col * SPACING, y1 = MARGIN + row * SPACING;
                    int x2 = x1, y2 = MARGIN + (row + 1) * SPACING;
                    dist = distanceToSegment(mouseX, mouseY, x1, y1, x2, y2);
                }
                if (dist < bestDist)
                {
                    bestDist = dist;
                    best = i;
                }
            }
            return best;
        }

        private double distanceToSegment(double px, double py, double x1, double y1, double x2, double y2)
        {
            double dx = x2 - x1, dy = y2 - y1;
            double lengthSq = dx * dx + dy * dy;
            double t = lengthSq == 0 ? 0 : Math.max(0, Math.min(1, ((px - x1) * dx + (py - y1) * dy) / lengthSq));
            double closestX = x1 + t * dx, closestY = y1 + t * dy;
            return Math.hypot(px - closestX, py - closestY);
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.applyAntialiasing(g2);

            for (int box = 0; box < DotsAndBoxesMatch.BOX_COUNT; box++)
            {
                if (boxOwners[box] == '.') continue;
                int r = box / DotsAndBoxesMatch.BOX_COLS, c = box % DotsAndBoxesMatch.BOX_COLS;
                int ownerIndex = boxOwners[box] - '0';
                Color color = (ownerIndex >= 0 && ownerIndex < PLAYER_COLORS.length) ? PLAYER_COLORS[ownerIndex] : Color.GRAY;
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 70));
                g2.fillRect(MARGIN + c * SPACING + 2, MARGIN + r * SPACING + 2, SPACING - 4, SPACING - 4);
            }

            g2.setStroke(new java.awt.BasicStroke(4, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            for (int i = 0; i < DotsAndBoxesMatch.LINE_COUNT; i++)
            {
                if (lines[i] != 'X') continue;
                if (i < 20)
                {
                    int row = i / DotsAndBoxesMatch.BOX_COLS, col = i % DotsAndBoxesMatch.BOX_COLS;
                    int x1 = MARGIN + col * SPACING, y1 = MARGIN + row * SPACING;
                    g2.setColor(ThemeManager.getColor(ThemeColor.ACCENT));
                    g2.drawLine(x1, y1, x1 + SPACING, y1);
                }
                else
                {
                    int vIndex = i - 20;
                    int row = vIndex / (DotsAndBoxesMatch.BOX_COLS + 1), col = vIndex % (DotsAndBoxesMatch.BOX_COLS + 1);
                    int x1 = MARGIN + col * SPACING, y1 = MARGIN + row * SPACING;
                    g2.setColor(ThemeManager.getColor(ThemeColor.ACCENT));
                    g2.drawLine(x1, y1, x1, y1 + SPACING);
                }
            }

            if (hintLine != null && lines[hintLine] == '.')
            {
                g2.setStroke(new java.awt.BasicStroke(4, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(255, 255, 255, 200));
                if (hintLine < 20)
                {
                    int row = hintLine / DotsAndBoxesMatch.BOX_COLS, col = hintLine % DotsAndBoxesMatch.BOX_COLS;
                    int x1 = MARGIN + col * SPACING, y1 = MARGIN + row * SPACING;
                    g2.drawLine(x1, y1, x1 + SPACING, y1);
                }
                else
                {
                    int vIndex = hintLine - 20;
                    int row = vIndex / (DotsAndBoxesMatch.BOX_COLS + 1), col = vIndex % (DotsAndBoxesMatch.BOX_COLS + 1);
                    int x1 = MARGIN + col * SPACING, y1 = MARGIN + row * SPACING;
                    g2.drawLine(x1, y1, x1, y1 + SPACING);
                }
            }

            g2.setColor(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
            for (int row = 0; row <= DotsAndBoxesMatch.BOX_ROWS; row++)
            {
                for (int col = 0; col <= DotsAndBoxesMatch.BOX_COLS; col++)
                {
                    int x = MARGIN + col * SPACING, y = MARGIN + row * SPACING;
                    g2.fillOval(x - DOT_RADIUS, y - DOT_RADIUS, DOT_RADIUS * 2, DOT_RADIUS * 2);
                }
            }

            g2.dispose();
        }
    }
}
