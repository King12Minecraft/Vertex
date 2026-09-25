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
import ui.GameModeCard;
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
 * SignalGridWindow
 * ----------------
 * Online ranked Signal Grid, 1v1 - click an empty cell to select
 * where you'll place your node, then click one of the 4 direction
 * buttons to fire it that way (both choices travel together in one
 * move). SignalGridMatch is the sole authority on what the signal
 * actually hits; this window just sends the placement+direction and
 * redraws whatever board state comes back.
 *
 * Embedded in MainMenu's game-host slot rather than its own window -
 * see ChessWindow/ReversiWindow's javadoc for the pattern. Never
 * confirmed before leaving mid-match even as its own window, so
 * requestLeave() here stays unconditional.
 */
public class SignalGridWindow extends JPanel implements NetworkManager.PushListener, EmbeddedGamePanel
{
    private static final String MODE_SELECT = "MODE_SELECT";
    private static final String SEARCHING = "SEARCHING";
    private static final String BOARD = "BOARD";

    private static final Color[] PLAYER_COLORS = { new Color(90, 170, 230), new Color(230, 110, 100) };

    /** "Practice" mode's AI - registered once per JVM, same pattern the other ai.search-backed games use. */
    private static final String AI_GAME_ID = "signal-grid-practice";
    private static final SignalGridGameModel AI_MODEL = new SignalGridGameModel();
    static
    {
        AiKernel.register(AI_GAME_ID,
            new GenericBotStrategy<int[], SignalGridMove>(AI_MODEL, SignalGridGameModel.PLAYER_B, 2),
            new RandomMoveStrategy<int[], SignalGridMove>(AI_MODEL, SignalGridGameModel.PLAYER_B));
    }

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);

    private JLabel searchingLabel;
    private BoardPanel boardPanel;
    private JLabel statusLabel;
    private ThemedButton hintButton;

    private boolean isPracticeMode = false;
    private PracticeMatch<int[], SignalGridMove> practiceMatch;

    private String matchId;
    private int mySymbol = -1;
    private String opponentUsername;
    private int[] owners = new int[SignalGridMatch.GRID_SIZE * SignalGridMatch.GRID_SIZE];
    private int selectedIndex = -1;
    private boolean myTurn;
    private boolean gameOver;

    public SignalGridWindow()
    {
        setLayout(new BorderLayout());
        java.util.Arrays.fill(owners, -1);

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

        JLabel title = new JLabel("Signal Grid");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        JLabel subtitle = new JLabel("Place a node, fire it, capture what it hits.");
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

        JLabel title = new JLabel("Signal Grid");
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

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        boardPanel = new BoardPanel();
        JPanel boardWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        boardWrap.setOpaque(false);
        boardWrap.add(boardPanel);
        center.add(boardWrap);
        center.add(javax.swing.Box.createVerticalStrut(12));

        JPanel directionRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        directionRow.setOpaque(false);
        directionRow.add(directionButton("UP", "\u2191"));
        directionRow.add(directionButton("DOWN", "\u2193"));
        directionRow.add(directionButton("LEFT", "\u2190"));
        directionRow.add(directionButton("RIGHT", "\u2192"));
        center.add(directionRow);

        JPanel centerer = new JPanel(new GridBagLayout());
        centerer.setOpaque(false);
        centerer.add(center, new GridBagConstraints());
        wrap.add(centerer, BorderLayout.CENTER);

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

    private ThemedButton directionButton(final String direction, String arrow)
    {
        ThemedButton button = new ThemedButton(arrow, false);
        button.setPreferredSize(new Dimension(56, 44));
        button.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { fireInDirection(direction); }
        });
        return button;
    }

    private void findMatch()
    {
        Message request = new Message();
        request.setType(MessageType.SIGNALGRID_FIND_MATCH_REQUEST);
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
        request.setType(MessageType.SIGNALGRID_LEAVE_QUEUE_REQUEST);
        request.setMatchId(matchId);
        NetworkManager.sendAsync(request);
    }

    private void selectCell(int index)
    {
        if (gameOver || !myTurn || owners[index] != -1) return;
        selectedIndex = index;
        statusLabel.setText("Node selected - pick a direction to fire it.");
        boardPanel.repaint();
    }

    private void fireInDirection(String direction)
    {
        if (gameOver || !myTurn || selectedIndex < 0) return;

        if (isPracticeMode)
        {
            makePracticeMove(selectedIndex, directionCodeFor(direction));
            return;
        }

        Message request = new Message();
        request.setType(MessageType.SIGNALGRID_FIRE_REQUEST);
        request.setMatchId(matchId);
        request.setCellIndex(selectedIndex);
        request.setSymbol(direction);
        NetworkManager.sendAsync(request);
        selectedIndex = -1;
    }

    private int directionCodeFor(String direction)
    {
        if ("UP".equals(direction)) return SignalGridMatch.UP;
        if ("DOWN".equals(direction)) return SignalGridMatch.DOWN;
        if ("LEFT".equals(direction)) return SignalGridMatch.LEFT;
        return SignalGridMatch.RIGHT;
    }

    // ==================== PRACTICE MODE (local, offline) ====================

    private void startPracticeMatch()
    {
        mySymbol = SignalGridGameModel.PLAYER_A;
        opponentUsername = "CPU";
        selectedIndex = -1;
        practiceMatch = new PracticeMatch<int[], SignalGridMove>(AI_MODEL, SignalGridGameModel.newBoard(), AI_GAME_ID,
            SignalGridGameModel.PLAYER_A, SignalGridGameModel.PLAYER_B, SignalGridGameModel.PLAYER_A);
        cardLayout.show(cards, BOARD);
        hintButton.setVisible(true);
        gameOver = false;
        myTurn = true;
        refreshPracticeBoard();
        statusLabel.setText("Your move - click an empty cell");
    }

    private void makePracticeMove(int index, int direction)
    {
        if (gameOver || !practiceMatch.isHumanTurn())
        {
            return;
        }
        boolean applied = practiceMatch.humanMove(new SignalGridMove(index, direction));
        selectedIndex = -1;
        if (!applied)
        {
            return;
        }
        refreshPracticeBoard();
        myTurn = false;

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
                    myTurn = true;
                    statusLabel.setText("Your move - click an empty cell");
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
        SignalGridMove suggestion = practiceMatch.suggestMove();
        selectedIndex = suggestion.index;
        String dirLabel = suggestion.direction == SignalGridMatch.UP ? "UP"
            : suggestion.direction == SignalGridMatch.DOWN ? "DOWN"
            : suggestion.direction == SignalGridMatch.LEFT ? "LEFT" : "RIGHT";
        statusLabel.setText("Hint: place here, then fire " + dirLabel);
        boardPanel.repaint();
    }

    private void handlePracticeGameOver()
    {
        gameOver = true;
        hintButton.setVisible(false);
        Integer winner = practiceMatch.getWinnerIndex();
        String text = winner == null ? "It's a draw."
            : winner == SignalGridGameModel.PLAYER_A ? "You won!" : "You lost.";
        statusLabel.setText(text);

        String shareText = winner != null && winner == SignalGridGameModel.PLAYER_A
            ? "I won a Signal Grid practice match on Vertex!" : null;
        SnakeGameOverDialog.show(this, 0, text, shareText, new SnakeGameOverDialog.Choice()
        {
            public void onPlayAgain() { startPracticeMatch(); }
            public void onClose() { MainMenu.getInstance().returnToGames(); }
        });
    }

    private void refreshPracticeBoard()
    {
        owners = practiceMatch.getState();
        boardPanel.repaint();
    }

    @Override
    public void onPush(final Message message)
    {
        MessageType type = message.getType();
        boolean isType = type == MessageType.SIGNALGRID_MATCH_FOUND || type == MessageType.SIGNALGRID_UPDATE
            || type == MessageType.SIGNALGRID_RESULT;
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

        if (type == MessageType.SIGNALGRID_MATCH_FOUND)
        {
            matchId = message.getMatchId();
            mySymbol = Integer.parseInt(message.getSymbol());
            opponentUsername = message.getOpponentUsername();
            applyBoardState(message.getBoardState());
            myTurn = mySymbol == 0;
            updateStatus();
            cardLayout.show(cards, BOARD);
        }
        else if (type == MessageType.SIGNALGRID_UPDATE)
        {
            applyBoardState(message.getBoardState());
            myTurn = Integer.parseInt(message.getSymbol()) == mySymbol;
            selectedIndex = -1;
            updateStatus();
        }
        else if (type == MessageType.SIGNALGRID_RESULT)
        {
            applyBoardState(message.getBoardState());
            gameOver = true;
            String result = message.getMatchResult();
            String text = "WIN".equals(result) ? "You won with " + message.getScore() + " nodes!"
                : "LOSE".equals(result) ? "You lost - " + message.getScore() + " nodes."
                : "DRAW".equals(result) ? "It's a draw."
                : "OPPONENT_LEFT".equals(result) ? "Your opponent left - you win!"
                : "Game over.";
            statusLabel.setText(text);
            boardPanel.repaint();

            String shareText = ("WIN".equals(result) || "OPPONENT_LEFT".equals(result))
                ? "I won a Signal Grid match on Vertex!" : null;
            SnakeGameOverDialog.show(this, message.getScore(), text, shareText, new SnakeGameOverDialog.Choice()
            {
                public void onPlayAgain()
                {
                    gameOver = false;
                    matchId = null;
                    java.util.Arrays.fill(owners, -1);
                    cardLayout.show(cards, SEARCHING);
                    findMatch();
                }
                public void onClose() { MainMenu.getInstance().returnToGames(); }
            });
        }
    }

    private void applyBoardState(String boardState)
    {
        if (boardState == null) return;
        String[] parts = boardState.split(",");
        for (int i = 0; i < owners.length && i < parts.length; i++)
        {
            try { owners[i] = Integer.parseInt(parts[i]); }
            catch (NumberFormatException ignored) { }
        }
        boardPanel.repaint();
    }

    private void updateStatus()
    {
        String opponentLabel = opponentUsername != null ? opponentUsername : "opponent";
        statusLabel.setText(myTurn ? "Your move - click an empty cell" : opponentLabel + "'s move...");
    }

    private class BoardPanel extends JPanel
    {
        private static final int CELL = 44;

        BoardPanel()
        {
            setPreferredSize(new Dimension(CELL * SignalGridMatch.GRID_SIZE, CELL * SignalGridMatch.GRID_SIZE));
            addMouseListener(new MouseAdapter()
            {
                public void mouseClicked(MouseEvent e)
                {
                    int col = e.getX() / CELL, row = e.getY() / CELL;
                    if (row >= 0 && row < SignalGridMatch.GRID_SIZE && col >= 0 && col < SignalGridMatch.GRID_SIZE)
                    {
                        selectCell(row * SignalGridMatch.GRID_SIZE + col);
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

            for (int row = 0; row < SignalGridMatch.GRID_SIZE; row++)
            {
                for (int col = 0; col < SignalGridMatch.GRID_SIZE; col++)
                {
                    int index = row * SignalGridMatch.GRID_SIZE + col;
                    int x = col * CELL, y = row * CELL;

                    g2.setColor(index == selectedIndex ? new Color(70, 74, 50) : new Color(38, 40, 50));
                    g2.fillRect(x, y, CELL - 2, CELL - 2);

                    int owner = owners[index];
                    if (owner != -1)
                    {
                        g2.setColor(PLAYER_COLORS[owner]);
                        g2.fillOval(x + 8, y + 8, CELL - 20, CELL - 20);
                    }
                }
            }
            g2.dispose();
        }
    }
}
