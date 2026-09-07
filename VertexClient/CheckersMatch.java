import java.util.ArrayList;
import java.util.List;

/**
 * CheckersMatch
 * -------------
 * Standard American checkers, 1v1, ELO-rated - an original
 * implementation of checkers' public-domain rules (same reasoning as
 * ConnectFourMatch's javadoc: not ported from anywhere, to avoid any
 * licensing entanglement). 8x8 board, only the 32 dark squares are
 * ever played on. Mandatory captures and mandatory multi-jump
 * continuation are both implemented (real checkers rules, not a
 * simplified variant) - if a capture is available for the player to
 * move, a non-capturing move is illegal, and after any single jump,
 * that same piece must keep jumping if another capture is
 * immediately available to it.
 *
 * Board is stored flat, index = row*8+col, row 0 at Red's back rank.
 * 'r'/'R' = Red man/king, 'b'/'B' = Black man/king, '.' = empty.
 * Red moves toward increasing row; Black moves toward decreasing row;
 * kings move either direction.
 */
public class CheckersMatch
{
    public static final int SIZE = 8;
    private static final String GAME_ID = "checkers";

    private final String matchId;
    private final ClientHandler redPlayer;
    private final ClientHandler blackPlayer;
    private final CheckersMatchManager matchManager;
    private final EconomyManager economyManager;
    private final LeaderboardManager leaderboardManager;

    private final char[] board = new char[SIZE * SIZE];
    private boolean redTurn = true;
    private boolean over = false;
    /** Set to the index of a piece mid multi-jump - if non-null, the next move from that player MUST move this exact piece and MUST be a capture. */
    private Integer mustContinueFrom = null;

    public CheckersMatch(String matchId, ClientHandler redPlayer, ClientHandler blackPlayer,
                          CheckersMatchManager matchManager, EconomyManager economyManager,
                          LeaderboardManager leaderboardManager)
    {
        this.matchId = matchId;
        this.redPlayer = redPlayer;
        this.blackPlayer = blackPlayer;
        this.matchManager = matchManager;
        this.economyManager = economyManager;
        this.leaderboardManager = leaderboardManager;
        setupBoard();
    }

    private void setupBoard()
    {
        java.util.Arrays.fill(board, '.');
        for (int row = 0; row < SIZE; row++)
        {
            for (int col = 0; col < SIZE; col++)
            {
                if ((row + col) % 2 != 1)
                {
                    continue;
                }
                if (row < 3) board[row * SIZE + col] = 'r';
                else if (row > 4) board[row * SIZE + col] = 'b';
            }
        }
    }

    public void start()
    {
        sendMatchFound(redPlayer, "RED", blackPlayer.getLoggedInUsername());
        sendMatchFound(blackPlayer, "BLACK", redPlayer.getLoggedInUsername());
        broadcastUpdate();
    }

    private void sendMatchFound(ClientHandler to, String symbol, String opponentUsername)
    {
        Message msg = new Message();
        msg.setType(MessageType.CHECKERS_MATCH_FOUND);
        msg.setMatchId(matchId);
        msg.setSymbol(symbol);
        msg.setOpponentUsername(opponentUsername);
        msg.setBoardState(boardString());
        to.sendMessage(msg);
    }

    /** from/to are both flat board indices - reuses getCellIndex()/getWinningLine() (as a 2-element {from,to} pair) rather than adding dedicated fields, same "reuse the generic move fields" approach every other 1v1 board game here uses. */
    public synchronized void makeMove(ClientHandler requester, int from, int to)
    {
        if (over) return;

        boolean isRed = requester == redPlayer;
        if (isRed != redTurn)
        {
            sendRejected(requester, "It's not your turn.");
            return;
        }
        if (mustContinueFrom != null && from != mustContinueFrom)
        {
            sendRejected(requester, "You must continue jumping with the same piece.");
            return;
        }

        char piece = board[from];
        if (piece == '.' || Character.toLowerCase(piece) != (isRed ? 'r' : 'b'))
        {
            sendRejected(requester, "That's not your piece.");
            return;
        }

        List<int[]> captures = capturesFor(from);
        boolean anyCaptureAvailable = mustContinueFrom != null || hasAnyCapture(isRed);

        int[] chosenCapture = null;
        for (int[] c : captures)
        {
            if (c[0] == to) { chosenCapture = c; break; }
        }

        if (anyCaptureAvailable)
        {
            if (chosenCapture == null)
            {
                sendRejected(requester, "You must capture.");
                return;
            }
            applyCapture(from, chosenCapture);
        }
        else
        {
            if (!isSimpleMoveLegal(from, to, piece))
            {
                sendRejected(requester, "Illegal move.");
                return;
            }
            board[to] = piece;
            board[from] = '.';
            maybeKing(to);
            mustContinueFrom = null;
        }

        if (mustContinueFrom == null)
        {
            String winner = checkWinner();
            if (winner != null)
            {
                over = true;
                finish(winner);
                return;
            }
            redTurn = !redTurn;
        }

        broadcastUpdate();
    }

    private void applyCapture(int from, int[] capture)
    {
        int to = capture[0];
        int capturedIndex = capture[1];
        char piece = board[from];
        board[to] = piece;
        board[from] = '.';
        board[capturedIndex] = '.';
        maybeKing(to);

        List<int[]> chained = capturesFor(to);
        mustContinueFrom = chained.isEmpty() ? null : to;
    }

    private void maybeKing(int index)
    {
        int row = index / SIZE;
        char piece = board[index];
        if (piece == 'r' && row == SIZE - 1) board[index] = 'R';
        else if (piece == 'b' && row == 0) board[index] = 'B';
    }

    private boolean isSimpleMoveLegal(int from, int to, char piece)
    {
        if (to < 0 || to >= board.length || board[to] != '.')
        {
            return false;
        }
        int fromRow = from / SIZE, fromCol = from % SIZE;
        int toRow = to / SIZE, toCol = to % SIZE;
        int dRow = toRow - fromRow, dCol = toCol - fromCol;

        if (Math.abs(dRow) != 1 || Math.abs(dCol) != 1)
        {
            return false;
        }
        boolean isKing = Character.isUpperCase(piece);
        boolean isRedPiece = Character.toLowerCase(piece) == 'r';
        if (!isKing && isRedPiece && dRow != 1) return false;
        if (!isKing && !isRedPiece && dRow != -1) return false;
        return true;
    }

    /** Every capture available to the piece at fromIndex - each result is {landingIndex, capturedPieceIndex}. */
    private List<int[]> capturesFor(int fromIndex)
    {
        List<int[]> results = new ArrayList<int[]>();
        char piece = board[fromIndex];
        if (piece == '.') return results;

        boolean isKing = Character.isUpperCase(piece);
        boolean isRed = Character.toLowerCase(piece) == 'r';
        int fromRow = fromIndex / SIZE, fromCol = fromIndex % SIZE;

        int[][] directions = { {1, 1}, {1, -1}, {-1, 1}, {-1, -1} };
        for (int[] dir : directions)
        {
            if (!isKing && ((isRed && dir[0] != 1) || (!isRed && dir[0] != -1)))
            {
                continue;
            }
            int midRow = fromRow + dir[0], midCol = fromCol + dir[1];
            int landRow = fromRow + dir[0] * 2, landCol = fromCol + dir[1] * 2;
            if (landRow < 0 || landRow >= SIZE || landCol < 0 || landCol >= SIZE) continue;

            int midIndex = midRow * SIZE + midCol;
            int landIndex = landRow * SIZE + landCol;
            char midPiece = board[midIndex];
            boolean midIsOpponent = midPiece != '.' && (Character.toLowerCase(midPiece) == 'r') != isRed;

            if (midIsOpponent && board[landIndex] == '.')
            {
                results.add(new int[] { landIndex, midIndex });
            }
        }
        return results;
    }

    private boolean hasAnyCapture(boolean isRed)
    {
        for (int i = 0; i < board.length; i++)
        {
            char piece = board[i];
            if (piece == '.') continue;
            boolean pieceIsRed = Character.toLowerCase(piece) == 'r';
            if (pieceIsRed == isRed && !capturesFor(i).isEmpty())
            {
                return true;
            }
        }
        return false;
    }

    /** "RED"/"BLACK" if that side has no pieces or no legal moves left, otherwise null. */
    private String checkWinner()
    {
        boolean redHasMove = false, blackHasMove = false;
        boolean redHasPiece = false, blackHasPiece = false;

        for (int i = 0; i < board.length; i++)
        {
            char piece = board[i];
            if (piece == '.') continue;
            boolean isRed = Character.toLowerCase(piece) == 'r';
            if (isRed) redHasPiece = true; else blackHasPiece = true;

            if (!capturesFor(i).isEmpty())
            {
                if (isRed) redHasMove = true; else blackHasMove = true;
            }
            else if (hasAnySimpleMove(i, piece))
            {
                if (isRed) redHasMove = true; else blackHasMove = true;
            }
        }

        if (!redHasPiece || !redHasMove) return "BLACK";
        if (!blackHasPiece || !blackHasMove) return "RED";
        return null;
    }

    private boolean hasAnySimpleMove(int fromIndex, char piece)
    {
        int fromRow = fromIndex / SIZE, fromCol = fromIndex % SIZE;
        int[][] directions = { {1, 1}, {1, -1}, {-1, 1}, {-1, -1} };
        boolean isKing = Character.isUpperCase(piece);
        boolean isRed = Character.toLowerCase(piece) == 'r';

        for (int[] dir : directions)
        {
            if (!isKing && ((isRed && dir[0] != 1) || (!isRed && dir[0] != -1))) continue;
            int toRow = fromRow + dir[0], toCol = fromCol + dir[1];
            if (toRow < 0 || toRow >= SIZE || toCol < 0 || toCol >= SIZE) continue;
            if (board[toRow * SIZE + toCol] == '.') return true;
        }
        return false;
    }

    private void broadcastUpdate()
    {
        sendUpdate(redPlayer);
        sendUpdate(blackPlayer);
    }

    private void sendUpdate(ClientHandler to)
    {
        Message msg = new Message();
        msg.setType(MessageType.CHECKERS_UPDATE);
        msg.setMatchId(matchId);
        msg.setBoardState(boardString());
        msg.setSymbol(redTurn ? "RED" : "BLACK");
        to.sendMessage(msg);
    }

    private void finish(String winnerSymbol)
    {
        matchManager.endMatch(matchId);
        recordRating(winnerSymbol);

        ClientHandler winnerHandler = "RED".equals(winnerSymbol) ? redPlayer : blackPlayer;
        economyManager.awardWin(winnerHandler, GAME_ID);

        sendResult(redPlayer, winnerSymbol);
        sendResult(blackPlayer, winnerSymbol);
    }

    private void recordRating(String winnerSymbol)
    {
        if (leaderboardManager == null || redPlayer.getAccountId() == null || blackPlayer.getAccountId() == null)
        {
            return;
        }
        double outcomeForRed = "RED".equals(winnerSymbol) ? 1.0 : 0.0;
        leaderboardManager.recordRatedMatch(GAME_ID, redPlayer.getAccountId(), blackPlayer.getAccountId(), outcomeForRed);
    }

    private void sendResult(ClientHandler to, String winnerSymbol)
    {
        Message msg = new Message();
        msg.setType(MessageType.CHECKERS_RESULT);
        msg.setMatchId(matchId);
        msg.setBoardState(boardString());
        boolean toIsWinner = (to == redPlayer && "RED".equals(winnerSymbol)) || (to == blackPlayer && "BLACK".equals(winnerSymbol));
        msg.setMatchResult(toIsWinner ? "WIN" : "LOSE");
        to.sendMessage(msg);
    }

    private void sendRejected(ClientHandler to, String reason)
    {
        Message msg = new Message();
        msg.setType(MessageType.CHECKERS_MOVE_REJECTED);
        msg.setMatchId(matchId);
        msg.setErrorText(reason);
        msg.setBoardState(boardString());
        to.sendMessage(msg);
    }

    public synchronized void handleDisconnect(ClientHandler who)
    {
        if (over) return;
        over = true;
        matchManager.endMatch(matchId);

        ClientHandler remaining = (who == redPlayer) ? blackPlayer : redPlayer;
        Message msg = new Message();
        msg.setType(MessageType.CHECKERS_RESULT);
        msg.setMatchId(matchId);
        msg.setMatchResult("OPPONENT_LEFT");
        msg.setBoardState(boardString());
        remaining.sendMessage(msg);

        economyManager.awardWin(remaining, GAME_ID);
    }

    private String boardString()
    {
        return new String(board);
    }
}
