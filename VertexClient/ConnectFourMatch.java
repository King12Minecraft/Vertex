/**
 * ConnectFourMatch
 * ----------------
 * Classic Connect Four, 1v1, ELO-rated - same shape as TicTacToeMatch
 * (this is an original implementation of Connect Four's public-domain
 * game rules, not copied from any other project). 7 columns x 6 rows,
 * stored flat (index = row*7 + col, row 0 = bottom). A move drops a
 * disc into a column; gravity finds the lowest empty row.
 */
public class ConnectFourMatch
{
    public static final int COLS = 7;
    public static final int ROWS = 6;
    private static final String GAME_ID = "connect-four";

    private final String matchId;
    private final ClientHandler playerRed;
    private final ClientHandler playerYellow;
    private final ConnectFourMatchManager matchManager;
    private final EconomyManager economyManager;
    private final LeaderboardManager leaderboardManager;

    private final char[] board = new char[COLS * ROWS];
    private boolean redTurn = true;
    private boolean over = false;

    public ConnectFourMatch(String matchId, ClientHandler playerRed, ClientHandler playerYellow,
                             ConnectFourMatchManager matchManager, EconomyManager economyManager,
                             LeaderboardManager leaderboardManager)
    {
        this.matchId = matchId;
        this.playerRed = playerRed;
        this.playerYellow = playerYellow;
        this.matchManager = matchManager;
        this.economyManager = economyManager;
        this.leaderboardManager = leaderboardManager;
        java.util.Arrays.fill(board, '.');
    }

    public void start()
    {
        sendMatchFound(playerRed, "RED", playerYellow.getLoggedInUsername());
        sendMatchFound(playerYellow, "YELLOW", playerRed.getLoggedInUsername());
        broadcastUpdate();
    }

    private void sendMatchFound(ClientHandler to, String symbol, String opponentUsername)
    {
        Message msg = new Message();
        msg.setType(MessageType.CONNECT4_MATCH_FOUND);
        msg.setMatchId(matchId);
        msg.setSymbol(symbol);
        msg.setOpponentUsername(opponentUsername);
        msg.setBoardState(boardString());
        to.sendMessage(msg);
    }

    /** column is 0-6 - reuses Message.getCellIndex() for this even though it isn't a flat board index, same "reuse the generic field" approach CHESS_MOVE_REQUEST's javadoc describes for its own destination square. */
    public synchronized void makeMove(ClientHandler requester, int column)
    {
        if (over)
        {
            return;
        }

        boolean isRed = requester == playerRed;
        if (isRed != redTurn)
        {
            sendRejected(requester, "It's not your turn.");
            return;
        }
        if (column < 0 || column >= COLS)
        {
            sendRejected(requester, "Invalid column.");
            return;
        }

        int row = lowestEmptyRow(column);
        if (row < 0)
        {
            sendRejected(requester, "That column is full.");
            return;
        }

        int index = row * COLS + column;
        board[index] = isRed ? 'R' : 'Y';

        int[] winLine = checkWinnerFrom(row, column);
        boolean draw = (winLine == null) && !hasEmptyCell();

        if (winLine != null || draw)
        {
            over = true;
            String winnerSymbol = winLine != null ? (isRed ? "RED" : "YELLOW") : null;
            broadcastResult(winnerSymbol, winLine);
            matchManager.endMatch(matchId);

            if (winnerSymbol != null)
            {
                ClientHandler winnerHandler = "RED".equals(winnerSymbol) ? playerRed : playerYellow;
                economyManager.awardWin(winnerHandler, GAME_ID);
            }
        }
        else
        {
            redTurn = !redTurn;
            broadcastUpdate();
        }
    }

    private int lowestEmptyRow(int column)
    {
        for (int row = 0; row < ROWS; row++)
        {
            if (board[row * COLS + column] == '.')
            {
                return row;
            }
        }
        return -1;
    }

    /** Checks all 4 directions (horizontal, vertical, both diagonals) through the just-placed disc for 4-in-a-row - cheaper than scanning the whole board like TicTacToeMatch's fixed WIN_LINES table does, since Connect Four's win lines aren't a small fixed set. */
    private int[] checkWinnerFrom(int row, int col)
    {
        char piece = board[row * COLS + col];
        int[][] directions = { {0, 1}, {1, 0}, {1, 1}, {1, -1} };

        for (int[] dir : directions)
        {
            java.util.List<Integer> line = new java.util.ArrayList<Integer>();
            line.add(row * COLS + col);

            for (int sign = -1; sign <= 1; sign += 2)
            {
                int r = row + dir[0] * sign;
                int c = col + dir[1] * sign;
                while (r >= 0 && r < ROWS && c >= 0 && c < COLS && board[r * COLS + c] == piece)
                {
                    line.add(r * COLS + c);
                    r += dir[0] * sign;
                    c += dir[1] * sign;
                }
            }

            if (line.size() >= 4)
            {
                int[] result = new int[4];
                for (int i = 0; i < 4; i++) result[i] = line.get(i);
                return result;
            }
        }
        return null;
    }

    private boolean hasEmptyCell()
    {
        for (int i = 0; i < board.length; i++)
        {
            if (board[i] == '.') return true;
        }
        return false;
    }

    private void broadcastUpdate()
    {
        sendUpdate(playerRed);
        sendUpdate(playerYellow);
    }

    private void sendUpdate(ClientHandler to)
    {
        Message msg = new Message();
        msg.setType(MessageType.CONNECT4_UPDATE);
        msg.setMatchId(matchId);
        msg.setBoardState(boardString());
        msg.setSymbol(redTurn ? "RED" : "YELLOW");
        to.sendMessage(msg);
    }

    private void broadcastResult(String winnerSymbol, int[] winLine)
    {
        recordRating(winnerSymbol);
        sendResult(playerRed, winnerSymbol, winLine);
        sendResult(playerYellow, winnerSymbol, winLine);
    }

    private void recordRating(String winnerSymbol)
    {
        if (leaderboardManager == null || playerRed.getAccountId() == null || playerYellow.getAccountId() == null)
        {
            return;
        }
        double outcomeForRed = winnerSymbol == null ? 0.5 : "RED".equals(winnerSymbol) ? 1.0 : 0.0;
        leaderboardManager.recordRatedMatch(GAME_ID, playerRed.getAccountId(), playerYellow.getAccountId(), outcomeForRed);
    }

    private void sendResult(ClientHandler to, String winnerSymbol, int[] winLine)
    {
        Message msg = new Message();
        msg.setType(MessageType.CONNECT4_RESULT);
        msg.setMatchId(matchId);
        msg.setBoardState(boardString());
        msg.setWinningLine(winLine);

        if (winnerSymbol == null)
        {
            msg.setMatchResult("DRAW");
        }
        else
        {
            boolean toIsWinner = (to == playerRed && "RED".equals(winnerSymbol))
                || (to == playerYellow && "YELLOW".equals(winnerSymbol));
            msg.setMatchResult(toIsWinner ? "WIN" : "LOSE");
        }
        to.sendMessage(msg);
    }

    private void sendRejected(ClientHandler to, String reason)
    {
        Message msg = new Message();
        msg.setType(MessageType.CONNECT4_MOVE_REJECTED);
        msg.setMatchId(matchId);
        msg.setErrorText(reason);
        msg.setBoardState(boardString());
        to.sendMessage(msg);
    }

    public synchronized void handleDisconnect(ClientHandler who)
    {
        if (over)
        {
            return;
        }
        over = true;
        matchManager.endMatch(matchId);

        ClientHandler remaining = (who == playerRed) ? playerYellow : playerRed;
        Message msg = new Message();
        msg.setType(MessageType.CONNECT4_RESULT);
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
