import java.util.Arrays;

public class TicTacToeMatch
{
    private static final String GAME_ID = "tictactoe-online";
    private static final int[][] WIN_LINES = {
        {0, 1, 2}, {3, 4, 5}, {6, 7, 8},
        {0, 3, 6}, {1, 4, 7}, {2, 5, 8},
        {0, 4, 8}, {2, 4, 6}
    };

    private final String matchId;
    private final ClientHandler playerX;
    private final ClientHandler playerO;
    private final MatchManager matchManager;
    private final EconomyManager economyManager;
    private final LeaderboardManager leaderboardManager;

    private final char[] board = new char[9];
    private boolean xTurn = true;
    private boolean over = false;

    public TicTacToeMatch(String matchId, ClientHandler playerX, ClientHandler playerO,
                           MatchManager matchManager, EconomyManager economyManager, LeaderboardManager leaderboardManager)
    {
        this.matchId = matchId;
        this.playerX = playerX;
        this.playerO = playerO;
        this.matchManager = matchManager;
        this.economyManager = economyManager;
        this.leaderboardManager = leaderboardManager;
        Arrays.fill(board, '.');
    }

    public void start()
    {
        sendMatchFound(playerX, "X", playerO.getLoggedInUsername());
        sendMatchFound(playerO, "O", playerX.getLoggedInUsername());
        broadcastUpdate();
    }

    private void sendMatchFound(ClientHandler to, String symbol, String opponentUsername)
    {
        Message msg = new Message();
        msg.setType(MessageType.MATCH_FOUND);
        msg.setMatchId(matchId);
        msg.setSymbol(symbol);
        msg.setOpponentUsername(opponentUsername);
        msg.setBoardState(boardString());
        to.sendMessage(msg);
    }

    public synchronized void makeMove(ClientHandler requester, int cellIndex)
    {
        if (over)
        {
            return;
        }

        boolean isX = requester == playerX;
        if (isX != xTurn)
        {
            sendRejected(requester, "It's not your turn.");
            return;
        }
        if (cellIndex < 0 || cellIndex > 8 || board[cellIndex] != '.')
        {
            sendRejected(requester, "That cell is already taken.");
            return;
        }

        board[cellIndex] = isX ? 'X' : 'O';

        int[] winLine = checkWinner();
        boolean draw = (winLine == null) && !hasEmptyCell();

        if (winLine != null || draw)
        {
            over = true;
            String winnerSymbol = winLine != null ? String.valueOf(board[winLine[0]]) : null;
            broadcastResult(winnerSymbol, winLine);
            matchManager.endMatch(matchId);

            if (winnerSymbol != null)
            {
                ClientHandler winnerHandler = "X".equals(winnerSymbol) ? playerX : playerO;
                economyManager.awardWin(winnerHandler, GAME_ID);
            }
        }
        else
        {
            xTurn = !xTurn;
            broadcastUpdate();
        }
    }

    private void broadcastUpdate()
    {
        sendUpdate(playerX);
        sendUpdate(playerO);
    }

    private void sendUpdate(ClientHandler to)
    {
        Message msg = new Message();
        msg.setType(MessageType.MATCH_UPDATE);
        msg.setMatchId(matchId);
        msg.setBoardState(boardString());
        msg.setSymbol(xTurn ? "X" : "O");
        to.sendMessage(msg);
    }

    private void broadcastResult(String winnerSymbol, int[] winLine)
    {
        recordRating(winnerSymbol);
        sendResult(playerX, winnerSymbol, winLine);
        sendResult(playerO, winnerSymbol, winLine);
    }

    private void recordRating(String winnerSymbol)
    {
        if (leaderboardManager == null || playerX.getAccountId() == null || playerO.getAccountId() == null)
        {
            return;
        }
        double outcomeForX = winnerSymbol == null ? 0.5 : "X".equals(winnerSymbol) ? 1.0 : 0.0;
        leaderboardManager.recordRatedMatch(GAME_ID, playerX.getAccountId(), playerO.getAccountId(), outcomeForX);
    }

    private void sendResult(ClientHandler to, String winnerSymbol, int[] winLine)
    {
        Message msg = new Message();
        msg.setType(MessageType.MATCH_OVER);
        msg.setMatchId(matchId);
        msg.setBoardState(boardString());
        msg.setWinningLine(winLine);

        if (winnerSymbol == null)
        {
            msg.setMatchResult("DRAW");
        }
        else
        {
            boolean toIsWinner = (to == playerX && "X".equals(winnerSymbol))
                || (to == playerO && "O".equals(winnerSymbol));
            msg.setMatchResult(toIsWinner ? "WIN" : "LOSE");
        }
        to.sendMessage(msg);
    }

    private void sendRejected(ClientHandler to, String reason)
    {
        Message msg = new Message();
        msg.setType(MessageType.MOVE_REJECTED);
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

        ClientHandler remaining = (who == playerX) ? playerO : playerX;
        Message msg = new Message();
        msg.setType(MessageType.MATCH_OVER);
        msg.setMatchId(matchId);
        msg.setMatchResult("OPPONENT_LEFT");
        msg.setBoardState(boardString());
        remaining.sendMessage(msg);

        economyManager.awardWin(remaining, GAME_ID);
    }

    private boolean hasEmptyCell()
    {
        for (int i = 0; i < board.length; i++)
        {
            if (board[i] == '.') return true;
        }
        return false;
    }

    private int[] checkWinner()
    {
        for (int i = 0; i < WIN_LINES.length; i++)
        {
            int a = WIN_LINES[i][0];
            int b = WIN_LINES[i][1];
            int c = WIN_LINES[i][2];
            if (board[a] != '.' && board[a] == board[b] && board[b] == board[c])
            {
                return WIN_LINES[i];
            }
        }
        return null;
    }

    private String boardString()
    {
        return new String(board);
    }
}
