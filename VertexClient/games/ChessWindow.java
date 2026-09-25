package games;
import economy.GuestPlayTracker;
import pages.MainMenu;
import ui.GameHubDialog;
import ui.GameModeCard;
import economy.PlayerColorRegistry;
import account.Session;
import net.NetworkManager;
import net.MessageType;
import ui.ThemedButton;
import theme.ThemeManager;
import theme.UITheme;
import theme.ThemeColor;
import ui.RoundedPanel;
import net.Message;
import ai.AiKernel;
import ai.search.GenericBotStrategy;
import ai.search.RandomMoveStrategy;
import ai.search.PracticeMatch;

import javax.swing.BoxLayout;
import javax.swing.JButton;
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
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * ChessWindow
 * -----------
 * Two ways to play: online (matchmaking, ChessMatch is the sole
 * authority on legality) or a local "Practice" mode against the
 * shared ai.search engine (ChessGameModel), fully offline - the same
 * mode-select pattern every other ai.search-backed game uses. Click a
 * piece to select it, click a destination to attempt the move; online
 * mode round-trips that through the server, practice mode validates it
 * locally via ChessGameModel/PracticeMatch. Pieces render as Unicode
 * chess glyphs directly in the cell buttons - no image assets needed.
 *
 * Embedded, not its own window: the proof-of-concept for converting
 * every game away from opening a separate JFrame, to instead embed in
 * MainMenu's game-host slot (see MainMenu.showGame(...)) and fill
 * whatever space the launcher gives it. EmbeddedGamePanel.requestLeave()
 * is the replacement for the old windowClosing confirmation - there's
 * no window-close event once this isn't a window, so MainMenu calls it
 * before navigating away instead (e.g. a Sidebar click mid-match), and
 * the in-panel "Leave" button calls the same method directly.
 */
public class ChessWindow extends JPanel implements NetworkManager.PushListener, EmbeddedGamePanel
{
    private static final String WHITE_PIECES = "PNBRQK";
    private static final String BLACK_PIECES = "pnbrqk";

    private static final String MODE_SELECT = "MODE_SELECT";
    private static final String MAIN = "MAIN";

    /** "Practice" mode's AI - registered once per JVM, same pattern the other ai.search-backed games use. Depth kept conservative given chess's much wider branching factor (~35) than the other games on this engine. */
    private static final String AI_GAME_ID = "chess-practice";
    private static final ChessGameModel AI_MODEL = new ChessGameModel();
    static
    {
        AiKernel.register(AI_GAME_ID,
            new GenericBotStrategy<ChessState, ChessMove>(AI_MODEL, ChessGameModel.BLACK, 3),
            new RandomMoveStrategy<ChessState, ChessMove>(AI_MODEL, ChessGameModel.BLACK));
    }

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);

    private final JButton[] cells = new JButton[64];
    private JLabel statusLabel;
    private ThemedButton offerDrawButton;
    private ThemedButton resignButton;
    private ThemedButton hintButton;

    private boolean isPracticeMode = false;
    private PracticeMatch<ChessState, ChessMove> practiceMatch;

    private String matchId;
    private String myColor;
    private String opponentUsername;
    private boolean myTurn;
    private int selectedSquare = -1;
    private boolean isSpectator;

    public ChessWindow()
    {
        this(null);
    }

    /** Pass a matchId to spectate that specific in-progress match instead of queueing to play. */
    public ChessWindow(String spectateMatchId)
    {
        this(spectateMatchId, null);
    }

    /** Waits for the CHESS_MATCH_FOUND the server sends once a rematch is accepted (see ChessMatchManager.createDirectMatch) - no public queue, no spectating. opponentUsername is shown so the wait screen isn't blank. */
    public static ChessWindow forRematchWait(String opponentUsername)
    {
        return new ChessWindow(null, opponentUsername);
    }

    private ChessWindow(String spectateMatchId, String rematchWaitOpponent)
    {
        isSpectator = spectateMatchId != null;
        boolean isRematchWait = rematchWaitOpponent != null;

        setLayout(new BorderLayout());

        RoundedPanel outer = new RoundedPanel(ThemeColor.BG_APP, 0);
        outer.setLayout(new BorderLayout());

        cards.add(createMainScreen(isSpectator, isRematchWait, rematchWaitOpponent), MAIN);
        if (!isSpectator && !isRematchWait)
        {
            cards.add(createModeSelectScreen(), MODE_SELECT);
        }
        outer.add(cards, BorderLayout.CENTER);

        add(outer, BorderLayout.CENTER);

        NetworkManager.addPushListener(this);

        if (isSpectator)
        {
            cardLayout.show(cards, MAIN);
            matchId = spectateMatchId;
            Message request = new Message();
            request.setType(MessageType.SPECTATE_REQUEST);
            request.setGameId("chess");
            request.setMatchId(spectateMatchId);
            NetworkManager.sendAsync(request);
        }
        else if (isRematchWait)
        {
            cardLayout.show(cards, MAIN);
            // Nothing to send - just sit registered as a push listener until the server's
            // createDirectMatch (triggered by the other player's REMATCH_RESPONSE handling)
            // sends CHESS_MATCH_FOUND, which the existing handler below already knows how to render.
        }
        else
        {
            cardLayout.show(cards, MODE_SELECT);
        }
    }

    /** The embedded replacement for the old windowClosing confirmation - see EmbeddedGamePanel's own javadoc for when MainMenu calls this, and why. */
    public boolean requestLeave()
    {
        if (matchId != null && !isSpectator)
        {
            int choice = javax.swing.JOptionPane.showConfirmDialog(this,
                "Leave this game? " + (opponentUsername != null ? opponentUsername : "Your opponent") + " will win by default.",
                "Leave Match", javax.swing.JOptionPane.YES_NO_OPTION);
            if (choice != javax.swing.JOptionPane.YES_OPTION)
            {
                return false;
            }
        }
        if (matchId == null && !isPracticeMode)
        {
            leaveQueue();
        }
        NetworkManager.removePushListener(this);
        return true;
    }

    /** The board+status+controls screen, shared by online play, spectating, rematch-wait, and Practice mode - only the mode-select screen (added separately, only for the "normal" non-spectate/non-rematch path) decides which of those this window ends up doing. */
    private JPanel createMainScreen(boolean isSpectator, boolean isRematchWait, String rematchWaitOpponent)
    {
        RoundedPanel root = new RoundedPanel(ThemeColor.BG_APP, 0);
        root.setLayout(new BorderLayout());
        root.setBorder(new EmptyBorder(16, 16, 16, 16));

        statusLabel = new JLabel(isSpectator ? "Watching..." : isRematchWait ? "Waiting for " + rematchWaitOpponent + " to accept..." : "Looking for an opponent...");
        statusLabel.setFont(UITheme.FONT_NAV_BOLD);
        statusLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        statusLabel.setBorder(new EmptyBorder(0, 4, 10, 4));
        root.add(statusLabel, BorderLayout.NORTH);

        JPanel board = new JPanel(new GridLayout(8, 8));
        board.setPreferredSize(new Dimension(400, 400));
        for (int row = 7; row >= 0; row--)
        {
            for (int col = 0; col < 8; col++)
            {
                final int square = row * 8 + col;
                JButton cell = new JButton();
                cell.setFont(new Font("Serif", Font.PLAIN, 30));
                cell.setFocusPainted(false);
                cell.setBorderPainted(false);
                cell.setBackground(((row + col) % 2 == 0) ? new Color(90, 70, 60) : new Color(210, 195, 170));
                cell.addActionListener(new ActionListener()
                {
                    public void actionPerformed(ActionEvent e) { handleCellClick(square); }
                });
                cells[square] = cell;
                board.add(cell);
            }
        }
        root.add(board, BorderLayout.CENTER);

        JPanel controls = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 10, 8));
        controls.setOpaque(false);

        // Always present, in every mode (spectating/rematch-wait included) -
        // the embedded replacement for the window's old OS close button,
        // which no longer exists now that this isn't its own window.
        ThemedButton leaveButton = new ThemedButton("Leave", false);
        leaveButton.setPreferredSize(new Dimension(90, 32));
        leaveButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                if (requestLeave())
                {
                    MainMenu.getInstance().returnToGames();
                }
            }
        });
        controls.add(leaveButton);

        if (!isSpectator && !isRematchWait)
        {
            offerDrawButton = new ThemedButton("Offer Draw", false);
            offerDrawButton.setPreferredSize(new Dimension(110, 32));
            offerDrawButton.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e)
                {
                    Message request = new Message();
                    request.setType(MessageType.CHESS_DRAW_OFFER_REQUEST);
                    request.setMatchId(matchId);
                    NetworkManager.sendAsync(request);
                }
            });
            controls.add(offerDrawButton);

            resignButton = new ThemedButton("Resign", false);
            resignButton.setPreferredSize(new Dimension(90, 32));
            resignButton.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e)
                {
                    int choice = javax.swing.JOptionPane.showConfirmDialog(ChessWindow.this,
                        "Resign this game? " + opponentUsername + " will win.", "Resign",
                        javax.swing.JOptionPane.YES_NO_OPTION);
                    if (choice == javax.swing.JOptionPane.YES_OPTION)
                    {
                        Message request = new Message();
                        request.setType(MessageType.CHESS_RESIGN_REQUEST);
                        request.setMatchId(matchId);
                        NetworkManager.sendAsync(request);
                    }
                }
            });
            controls.add(resignButton);

            hintButton = new ThemedButton("Hint", false);
            hintButton.setPreferredSize(new Dimension(90, 32));
            hintButton.setVisible(false);
            hintButton.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e) { showHint(); }
            });
            controls.add(hintButton);
        }

        root.add(controls, BorderLayout.SOUTH);

        return root;
    }

    /** Embedded, this fills whatever space MainMenu's game-host slot gives it - much bigger than the old fixed-size popup window - so the actual mode-select content is centered within that space via an outer GridBagLayout wrapper, rather than pinned to the top-left corner with dead space around it. */
    private JPanel createModeSelectScreen()
    {
        RoundedPanel wrapper = new RoundedPanel(ThemeColor.BG_APP, 0);
        wrapper.setLayout(new GridBagLayout());

        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(50, 60, 50, 60));
        wrapper.add(panel, new GridBagConstraints());

        JLabel title = new JLabel("Chess");
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

        JPanel tileRow = new JPanel(new GridLayout(1, 2, 16, 0));
        tileRow.setOpaque(false);
        tileRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        tileRow.setMaximumSize(new Dimension(2000, 150));

        tileRow.add(new GameModeCard("Play Online", "Ranked 1v1 against a real opponent.",
            ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_START), new GameModeCard.ClickListener()
            {
                public void onClick()
                {
                    cardLayout.show(cards, MAIN);
                    findMatch();
                }
            }));

        tileRow.add(new GameModeCard("Practice Mode", "Against the computer, fully offline.",
            ThemeManager.getColor(ThemeColor.TEXT_MUTED), new GameModeCard.ClickListener()
            {
                public void onClick() { startPracticeMatch(); }
            }));

        panel.add(tileRow);
        return wrapper;
    }

    // ==================== PRACTICE MODE (local, offline) ====================

    private void startPracticeMatch()
    {
        isPracticeMode = true;
        myColor = "WHITE";
        opponentUsername = "CPU";
        selectedSquare = -1;
        practiceMatch = new PracticeMatch<ChessState, ChessMove>(AI_MODEL, ChessGameModel.newBoard(), AI_GAME_ID,
            ChessGameModel.WHITE, ChessGameModel.BLACK, ChessGameModel.WHITE);

        offerDrawButton.setVisible(false);
        resignButton.setVisible(false);
        hintButton.setVisible(true);

        cardLayout.show(cards, MAIN);
        myTurn = true;
        statusLabel.setText("You are WHITE vs CPU - your move");
        refreshPracticeBoard();
    }

    /** Same three-branch selection shape ChessWindow's own online handleCellClick already uses (select / deselect / attempt-move) - clicking a different one of your own pieces isn't a special "re-select" case here either, matching that existing precedent exactly rather than inventing nicer behavior only for practice mode. */
    private void handlePracticeCellClick(int square)
    {
        if (!myTurn || practiceMatch == null || practiceMatch.isOver())
        {
            return;
        }

        if (selectedSquare == -1)
        {
            char piece = pieceCharAt(square);
            if (piece == '.' || !Character.isUpperCase(piece))
            {
                return;
            }
            selectedSquare = square;
            highlightSelection(square);
            return;
        }
        if (selectedSquare == square)
        {
            selectedSquare = -1;
            renderBoard(lastKnownBoard);
            return;
        }

        ChessMove move = new ChessMove(selectedSquare, square);
        selectedSquare = -1;
        boolean applied = practiceMatch.humanMove(move);
        if (!applied)
        {
            renderBoard(lastKnownBoard);
            statusLabel.setText("That move isn't legal.");
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
                    statusLabel.setText("Your move (WHITE)");
                }
            }
        });
        botTimer.setRepeats(false);
        botTimer.start();
    }

    private void showHint()
    {
        if (!myTurn || practiceMatch == null || practiceMatch.isOver())
        {
            return;
        }
        ChessMove suggestion = practiceMatch.suggestMove();
        renderBoard(lastKnownBoard);
        Color hintColor = new Color(90, 220, 120);
        cells[suggestion.from].setBackground(hintColor);
        cells[suggestion.to].setBackground(hintColor);
    }

    private void handlePracticeGameOver()
    {
        myTurn = false;
        Integer winner = practiceMatch.getWinnerIndex();
        String text = winner == null ? "Stalemate - it's a draw."
            : winner == ChessGameModel.WHITE ? "Checkmate! You win." : "Checkmate! CPU wins.";
        statusLabel.setText(text);

        String shareText = winner != null && winner == ChessGameModel.WHITE
            ? "I won a Chess practice match on Vertex!" : null;
        SnakeGameOverDialog.show(this, 0, text, shareText, new SnakeGameOverDialog.Choice()
        {
            public void onPlayAgain() { startPracticeMatch(); }
            public void onClose() { MainMenu.getInstance().returnToGames(); }
        });
    }

    private void refreshPracticeBoard()
    {
        renderBoard(new String(practiceMatch.getState().board));
    }

    private void findMatch()
    {
        Message request = new Message();
        request.setType(MessageType.CHESS_FIND_MATCH_REQUEST);
        boolean sent = NetworkManager.sendAsync(request);
        if (!sent)
        {
            statusLabel.setText("Can't reach the server - is it running?");
        }
    }

    private void leaveQueue()
    {
        Message request = new Message();
        request.setType(MessageType.CHESS_LEAVE_QUEUE_REQUEST);
        NetworkManager.sendAsync(request);
    }

    private void handleCellClick(int square)
    {
        if (isPracticeMode)
        {
            handlePracticeCellClick(square);
            return;
        }

        if (matchId == null || !myTurn)
        {
            return;
        }

        if (selectedSquare == -1)
        {
            char piece = cells[square].getText().isEmpty() ? '.' : pieceCharAt(square);
            if (piece == '.')
            {
                return;
            }
            boolean isMyPiece = "WHITE".equals(myColor)
                ? WHITE_PIECES.indexOf(Character.toUpperCase(piece)) >= 0 && Character.isUpperCase(piece)
                : BLACK_PIECES.indexOf(piece) >= 0 && Character.isLowerCase(piece);
            if (!isMyPiece)
            {
                return;
            }
            selectedSquare = square;
            highlightSelection(square);
        }
        else if (selectedSquare == square)
        {
            selectedSquare = -1;
            renderBoard(lastKnownBoard);
        }
        else
        {
            Message request = new Message();
            request.setType(MessageType.CHESS_MOVE_REQUEST);
            request.setMatchId(matchId);
            request.setCellIndex(selectedSquare);
            request.setChessToSquare(square);
            NetworkManager.sendAsync(request);
            selectedSquare = -1;
        }
    }

    private String lastKnownBoard = buildEmptyBoardString();

    private static String buildEmptyBoardString()
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 64; i++)
        {
            sb.append('.');
        }
        return sb.toString();
    }

    private char pieceCharAt(int square)
    {
        return lastKnownBoard.charAt(square);
    }

    private void highlightSelection(int square)
    {
        renderBoard(lastKnownBoard);
        cells[square].setBackground(selectionColor());
    }

    /** The selection highlight uses the player's purchased username color if set, matching every other game's precedent, falling back to a fixed green. */
    private Color selectionColor()
    {
        if (Session.isLoggedIn())
        {
            Color owned = PlayerColorRegistry.resolve(Session.getCurrentAccount().getPlayerColorName());
            if (owned != null)
            {
                return owned;
            }
        }
        return new Color(120, 170, 90);
    }

    private void renderBoard(String board)
    {
        lastKnownBoard = board;
        for (int square = 0; square < 64; square++)
        {
            int row = square / 8;
            int col = square % 8;
            char piece = board.charAt(square);
            cells[square].setText(piece == '.' ? "" : String.valueOf(glyphFor(piece)));
            cells[square].setForeground(Character.isUpperCase(piece) ? Color.WHITE : Color.BLACK);
            cells[square].setBackground(((row + col) % 2 == 0) ? new Color(90, 70, 60) : new Color(210, 195, 170));
        }
    }

    private char glyphFor(char piece)
    {
        switch (Character.toUpperCase(piece))
        {
            case 'K': return Character.isUpperCase(piece) ? '\u2654' : '\u265A';
            case 'Q': return Character.isUpperCase(piece) ? '\u2655' : '\u265B';
            case 'R': return Character.isUpperCase(piece) ? '\u2656' : '\u265C';
            case 'B': return Character.isUpperCase(piece) ? '\u2657' : '\u265D';
            case 'N': return Character.isUpperCase(piece) ? '\u2658' : '\u265E';
            case 'P': return Character.isUpperCase(piece) ? '\u2659' : '\u265F';
            default: return ' ';
        }
    }

    @Override
    public void onPush(final Message message)
    {
        MessageType type = message.getType();
        boolean isChessType = type == MessageType.CHESS_MATCH_FOUND || type == MessageType.CHESS_UPDATE
            || type == MessageType.CHESS_MOVE_REJECTED || type == MessageType.CHESS_MATCH_OVER
            || type == MessageType.SPECTATE_ENDED || type == MessageType.CHESS_DRAW_OFFERED
            || type == MessageType.CHESS_DRAW_DECLINED;
        if (!isChessType)
        {
            return;
        }
        if (matchId != null && message.getMatchId() != null && !message.getMatchId().equals(matchId))
        {
            return;
        }

        SwingUtilities.invokeLater(new Runnable()
        {
            public void run() { handleChessMessage(message); }
        });
    }

    private void handleChessMessage(Message message)
    {
        if (message.getType() == MessageType.CHESS_MATCH_FOUND)
        {
            matchId = message.getMatchId();
            myColor = message.getSymbol();
            opponentUsername = message.getOpponentUsername();
            myTurn = "WHITE".equals(myColor);
            statusLabel.setText("You are " + myColor + " vs " + opponentUsername
                + (myTurn ? " - your move" : " - waiting for their move"));
            renderBoard(message.getBoardState());
        }
        else if (message.getType() == MessageType.CHESS_UPDATE)
        {
            renderBoard(message.getBoardState());
            if (isSpectator)
            {
                statusLabel.setText((message.getSymbol().equals("WHITE") ? "White" : "Black") + " to move");
            }
            else
            {
                myTurn = message.getSymbol().equals(myColor);
                statusLabel.setText("You are " + myColor + " vs " + opponentUsername
                    + (myTurn ? " - your move" : " - waiting for their move"));
            }
        }
        else if (message.getType() == MessageType.CHESS_MOVE_REJECTED)
        {
            renderBoard(message.getBoardState());
            statusLabel.setText(message.getErrorText());
        }
        else if (message.getType() == MessageType.SPECTATE_ENDED)
        {
            GameHubDialog.show(this, "Chess", "The match you were watching has ended.");
            MainMenu.getInstance().returnToGames();
        }
        else if (message.getType() == MessageType.CHESS_DRAW_OFFERED)
        {
            int choice = javax.swing.JOptionPane.showConfirmDialog(this,
                opponentUsername + " has offered a draw. Accept?", "Draw Offer",
                javax.swing.JOptionPane.YES_NO_OPTION);
            Message response = new Message();
            response.setType(MessageType.CHESS_DRAW_RESPONSE_REQUEST);
            response.setMatchId(matchId);
            response.setSuccess(choice == javax.swing.JOptionPane.YES_OPTION);
            NetworkManager.sendAsync(response);
        }
        else if (message.getType() == MessageType.CHESS_DRAW_DECLINED)
        {
            statusLabel.setText(opponentUsername + " declined the draw offer.");
        }
        else if (message.getType() == MessageType.CHESS_MATCH_OVER)
        {
            renderBoard(message.getBoardState());
            String result = message.getMatchResult();
            String endReason = message.getErrorText();
            String text;
            if ("OPPONENT_LEFT".equals(result))
            {
                text = opponentUsername + " left the game. You win by default!";
            }
            else if ("RESIGNED".equals(endReason))
            {
                text = "WIN".equals(result) ? opponentUsername + " resigned - you win!" : "You resigned. " + opponentUsername + " wins.";
            }
            else if ("DRAW_AGREED".equals(endReason))
            {
                text = "Draw agreed.";
            }
            else if ("WIN".equals(result))
            {
                text = "Checkmate! You win.";
            }
            else if ("LOSE".equals(result))
            {
                text = "Checkmate! " + opponentUsername + " wins.";
            }
            else
            {
                text = "Stalemate - it's a draw.";
            }

            recordPlayed();
            final String finalOpponent = opponentUsername;
            final boolean[] rematchRequested = { false };
            GameHubDialog.showWithAction(this, "Chess", text, "Rematch", new Runnable()
            {
                public void run()
                {
                    rematchRequested[0] = true;
                    requestRematch(finalOpponent);
                }
            });
            // requestRematch (if it ran) already swapped the game-host slot to
            // the new rematch-wait panel via MainMenu.showGame(...) - calling
            // returnToGames() here too would immediately undo that, so only
            // do it when the dialog was just dismissed with no rematch.
            if (!rematchRequested[0])
            {
                MainMenu.getInstance().returnToGames();
            }
        }
    }

    private void requestRematch(String opponent)
    {
        Message request = new Message();
        request.setType(MessageType.REMATCH_REQUEST);
        request.setToUsername(opponent);
        request.setGameId("chess");
        NetworkManager.sendAsync(request);

        ChessWindow panel = ChessWindow.forRematchWait(opponent);
        MainMenu.getInstance().showGame(panel);
    }

    private void recordPlayed()
    {
        if (!Session.isLoggedIn())
        {
            GuestPlayTracker.recordGuestPlay("chess", 0);
            return;
        }

        Message request = new Message();
        request.setType(MessageType.GAME_PLAYED_REQUEST);
        request.setGameId("chess");
        NetworkManager.sendAsync(request);
    }
}
