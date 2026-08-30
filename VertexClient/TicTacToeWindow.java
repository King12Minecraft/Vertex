import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * TicTacToeWindow
 * ----------------
 * Standalone window for Tic-Tac-Toe, with two ways to play:
 *
 *   - Play Online: matchmaking, then straight into a single match -
 *     entirely driven by server pushes (MATCH_FOUND / MATCH_UPDATE /
 *     MATCH_OVER / MOVE_REJECTED). The earlier best-of-N round
 *     selection was removed here - it was a genuine source of
 *     confusion/bugs in real online play, so online matches are back
 *     to one game, start to finish, same as the original design.
 *   - Practice Mode: a local best-of-N series against TicTacToeAI
 *     (TicTacToePracticeMatch) - entirely offline, no server
 *     involvement, so its round picker is unaffected by any of the
 *     above (nothing to synchronize, nothing to go wrong).
 *
 * Both modes share the same board rendering and win display (real
 * strike-through line via WinLineOverlay, clear "X WINS"/"O WINS"
 * text) - only how moves get resolved differs.
 */
public class TicTacToeWindow extends JFrame implements NetworkManager.PushListener
{
    private static final String MODE_SELECT = "MODE_SELECT";
    private static final String SEARCHING = "SEARCHING";
    private static final String ROUNDS = "ROUNDS";
    private static final String BOARD = "BOARD";

    private final java.awt.CardLayout cardLayout = new java.awt.CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private final TicTacToeCellButton[] cellButtons = new TicTacToeCellButton[9];
    private WinLineOverlay winLineOverlay;

    private JLabel statusLabel;
    private JLabel searchingLabel;

    private boolean isPracticeMode = false;
    private TicTacToePracticeMatch practiceMatch;
    private int bestOf = 1;

    private String matchId;
    private String mySymbol;
    private String opponentUsername;
    private boolean myTurn;
    private boolean canPlay;

    public TicTacToeWindow()
    {
        super("Vertex - Tic-Tac-Toe");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);
        setIconImage(GameLogo.renderIcon(64));

        cards.add(createModeSelectScreen(), MODE_SELECT);
        cards.add(createSearchingScreen(), SEARCHING);
        cards.add(createPracticeRoundsScreen(), ROUNDS);
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
            }
        });
    }

    private JPanel createModeSelectScreen()
    {
        RoundedPanel panel = new RoundedPanel(ThemeColor.BG_APP, 0);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(50, 60, 50, 60));
        panel.setPreferredSize(new Dimension(460, 320));

        JLabel title = new JLabel("Tic-Tac-Toe");
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

        tileRow.add(new GameModeCard("Play Online", "Matched with a real opponent, one game.",
            ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_START), new GameModeCard.ClickListener()
            {
                public void onClick() { chooseMode(true); }
            }));

        tileRow.add(new GameModeCard("Practice Mode", "Best-of-N series against the computer, fully offline.",
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
            mySymbol = "X";
            opponentUsername = "CPU";
            cardLayout.show(cards, ROUNDS);
            pack();
            setLocationRelativeTo(null);
        }
    }

    private JPanel createSearchingScreen()
    {
        RoundedPanel panel = new RoundedPanel(ThemeColor.BG_APP, 0);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(60, 60, 60, 60));
        panel.setPreferredSize(new Dimension(380, 240));

        JLabel title = new JLabel("Tic-Tac-Toe");
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

    /** Practice Mode only now - online play has no round-selection step at all. */
    private JPanel createPracticeRoundsScreen()
    {
        RoundedPanel panel = new RoundedPanel(ThemeColor.BG_APP, 0);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(50, 50, 50, 50));
        panel.setPreferredSize(new Dimension(380, 300));

        JLabel title = new JLabel("Choose Match Length");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        JLabel subtitle = new JLabel("You are X - vs CPU");
        subtitle.setFont(UITheme.FONT_SUBHEAD);
        subtitle.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setBorder(new EmptyBorder(8, 0, 24, 0));
        panel.add(subtitle);

        panel.add(buildRoundsButton("Best of 1", 1));
        panel.add(Box.createVerticalStrut(10));
        panel.add(buildRoundsButton("Best of 3", 3));
        panel.add(Box.createVerticalStrut(10));
        panel.add(buildRoundsButton("Best of 5", 5));

        return panel;
    }

    private ThemedButton buildRoundsButton(String label, final int rounds)
    {
        ThemedButton button = new ThemedButton(label, true);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(2000, 42));
        button.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { startPracticeMatch(rounds); }
        });
        return button;
    }

    private JPanel createBoardScreen()
    {
        RoundedPanel panel = new RoundedPanel(ThemeColor.BG_APP, 0);
        panel.setLayout(new BorderLayout());
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));

        statusLabel = new JLabel(" ");
        statusLabel.setFont(UITheme.FONT_NAV_BOLD);
        statusLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        statusLabel.setBorder(new EmptyBorder(0, 0, 14, 0));
        panel.add(statusLabel, BorderLayout.NORTH);

        final JPanel grid = new JPanel(new GridLayout(3, 3, 8, 8));
        grid.setOpaque(false);

        for (int i = 0; i < 9; i++)
        {
            final int index = i;
            final TicTacToeCellButton cell = new TicTacToeCellButton();
            cell.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e) { makeMove(index); }
            });
            cellButtons[i] = cell;
            grid.add(cell);
        }

        winLineOverlay = new WinLineOverlay(cellButtons);

        final javax.swing.JLayeredPane boardLayers = new javax.swing.JLayeredPane();
        boardLayers.setPreferredSize(new Dimension(280, 280));
        boardLayers.add(grid, javax.swing.JLayeredPane.DEFAULT_LAYER);
        boardLayers.add(winLineOverlay, javax.swing.JLayeredPane.PALETTE_LAYER);
        grid.setBounds(0, 0, 280, 280);
        winLineOverlay.setBounds(0, 0, 280, 280);
        boardLayers.addComponentListener(new java.awt.event.ComponentAdapter()
        {
            public void componentResized(java.awt.event.ComponentEvent e)
            {
                grid.setBounds(0, 0, boardLayers.getWidth(), boardLayers.getHeight());
                winLineOverlay.setBounds(0, 0, boardLayers.getWidth(), boardLayers.getHeight());
            }
        });

        JPanel centerWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        centerWrap.setOpaque(false);
        centerWrap.add(boardLayers);
        panel.add(centerWrap, BorderLayout.CENTER);

        JPanel bottomRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        bottomRow.setOpaque(false);
        bottomRow.setBorder(new EmptyBorder(14, 0, 0, 0));

        ThemedButton close = new ThemedButton("Close", false);
        close.setPreferredSize(new Dimension(100, 36));
        close.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                if (!isPracticeMode) leaveMatch();
                dispose();
            }
        });
        bottomRow.add(close);
        panel.add(bottomRow, BorderLayout.SOUTH);

        return panel;
    }

    private void findMatch()
    {
        Message request = new Message();
        request.setType(MessageType.FIND_MATCH_REQUEST);
        boolean sent = NetworkManager.sendAsync(request);
        if (!sent)
        {
            searchingLabel.setText("Can't reach the server - is it running?");
        }
    }

    private void leaveMatch()
    {
        Message request = new Message();
        request.setType(MessageType.LEAVE_MATCH_REQUEST);
        request.setMatchId(matchId);
        NetworkManager.sendAsync(request);
        NetworkManager.removePushListener(this);
    }

    // ==================== PRACTICE MODE (local, offline) ====================

    private void startPracticeMatch(int rounds)
    {
        bestOf = rounds;
        practiceMatch = new TicTacToePracticeMatch(rounds);
        cardLayout.show(cards, BOARD);
        pack();
        setLocationRelativeTo(null);
        refreshPracticeBoard();
        updateStatus("Your turn");
    }

    private void makePracticeMove(int index)
    {
        if (!canPlay || !practiceMatch.isHumanTurn() || practiceMatch.getBoard()[index] != '.')
        {
            return;
        }

        TicTacToePracticeMatch.RoundResult result = practiceMatch.humanMove(index);
        refreshPracticeBoard();

        if (result != null)
        {
            handlePracticeRoundResult(result);
        }
        else
        {
            triggerAiMove();
        }
    }

    private void triggerAiMove()
    {
        canPlay = false;
        updateStatus("CPU is thinking...");

        Timer aiTimer = new Timer(500, null);
        aiTimer.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                ((Timer) e.getSource()).stop();
                TicTacToePracticeMatch.RoundResult result = practiceMatch.aiMove();
                refreshPracticeBoard();

                if (result != null)
                {
                    handlePracticeRoundResult(result);
                }
                else
                {
                    canPlay = true;
                    updateStatus("Your turn");
                }
            }
        });
        aiTimer.setRepeats(false);
        aiTimer.start();
    }

    private void handlePracticeRoundResult(TicTacToePracticeMatch.RoundResult result)
    {
        canPlay = false;

        if (result.winLine != null)
        {
            winLineOverlay.setWinningLine(result.winLine);
        }

        String scoreLine = "Round " + result.roundNumber + " of " + bestOf
            + "  \u2022  You " + result.scoreHuman + " - " + result.scoreAI + " CPU";

        if (result.winnerSymbol == null)
        {
            updateStatus((result.seriesOver ? "SERIES DRAWN" : "ROUND DRAWN") + "  \u2022  " + scoreLine);
        }
        else
        {
            boolean isMe = result.winnerSymbol.equals(mySymbol);
            String who = isMe ? "You" : "CPU";
            String scope = result.seriesOver ? "WINS THE SERIES!" : "WINS THE ROUND";
            updateStatus(result.winnerSymbol + " " + scope + "  (" + who + ")  \u2022  " + scoreLine);
        }

        if (!result.seriesOver)
        {
            Timer nextRoundTimer = new Timer(1400, null);
            nextRoundTimer.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e)
                {
                    ((Timer) e.getSource()).stop();
                    refreshPracticeBoard();
                    if (practiceMatch.isHumanTurn())
                    {
                        canPlay = true;
                        updateStatus("Your turn");
                    }
                    else
                    {
                        triggerAiMove();
                    }
                }
            });
            nextRoundTimer.setRepeats(false);
            nextRoundTimer.start();
        }
    }

    private void refreshPracticeBoard()
    {
        winLineOverlay.clear();
        applyBoardState(new String(practiceMatch.getBoard()));
        canPlay = true;
    }

    // ==================== ONLINE MODE (server-driven, single match) ====================

    private void makeMove(int index)
    {
        if (isPracticeMode)
        {
            makePracticeMove(index);
            return;
        }

        if (!canPlay || !myTurn)
        {
            return;
        }
        if (cellButtons[index].getValue() != ' ')
        {
            return;
        }

        Message request = new Message();
        request.setType(MessageType.MAKE_MOVE_REQUEST);
        request.setMatchId(matchId);
        request.setCellIndex(index);
        NetworkManager.sendAsync(request);

        // Optimistic lock - the server's MATCH_UPDATE is what actually confirms this.
        myTurn = false;
    }

    @Override
    public void onPush(final Message message)
    {
        MessageType type = message.getType();
        boolean isMatchType = type == MessageType.MATCH_FOUND || type == MessageType.MATCH_UPDATE
            || type == MessageType.MATCH_OVER || type == MessageType.MOVE_REJECTED;
        if (!isMatchType)
        {
            return;
        }
        if (matchId != null && message.getMatchId() != null && !message.getMatchId().equals(matchId))
        {
            return;
        }

        SwingUtilities.invokeLater(new Runnable()
        {
            public void run() { handleMatchMessage(message); }
        });
    }

    private void handleMatchMessage(Message message)
    {
        if (message.getType() == MessageType.MATCH_FOUND)
        {
            matchId = message.getMatchId();
            mySymbol = message.getSymbol();
            opponentUsername = message.getOpponentUsername();
            myTurn = "X".equals(mySymbol);
            applyBoardState(message.getBoardState());
            updateStatus("You are " + mySymbol + " - vs " + opponentUsername
                + (myTurn ? " (your turn)" : " (their turn)"));
            cardLayout.show(cards, BOARD);
            pack();
            setLocationRelativeTo(null);
        }
        else if (message.getType() == MessageType.MATCH_UPDATE)
        {
            winLineOverlay.clear();
            applyBoardState(message.getBoardState());
            canPlay = true;
            myTurn = message.getSymbol() != null && message.getSymbol().equals(mySymbol);
            updateStatus(myTurn ? "Your turn" : "Waiting for " + opponentUsername + "...");
        }
        else if (message.getType() == MessageType.MOVE_REJECTED)
        {
            applyBoardState(message.getBoardState());
            updateStatus(message.getErrorText());
        }
        else if (message.getType() == MessageType.MATCH_OVER)
        {
            applyBoardState(message.getBoardState());
            canPlay = false;

            if (message.getWinningLine() != null)
            {
                winLineOverlay.setWinningLine(message.getWinningLine());
            }

            String result = message.getMatchResult();
            if ("OPPONENT_LEFT".equals(result))
            {
                updateStatus(opponentUsername + " left the match.");
            }
            else if ("WIN".equals(result))
            {
                updateStatus(mySymbol + " WINS!  (You)");
            }
            else if ("LOSE".equals(result))
            {
                String theirSymbol = "X".equals(mySymbol) ? "O" : "X";
                updateStatus(theirSymbol + " WINS!  (" + opponentUsername + ")");
            }
            else
            {
                updateStatus("DRAW");
            }
        }
    }

    private void updateStatus(String text)
    {
        statusLabel.setText(text);
    }

    private void applyBoardState(String boardState)
    {
        if (boardState == null)
        {
            return;
        }
        for (int i = 0; i < 9 && i < boardState.length(); i++)
        {
            char c = boardState.charAt(i);
            cellButtons[i].setValue(c == '.' ? ' ' : c);
        }
    }
}
