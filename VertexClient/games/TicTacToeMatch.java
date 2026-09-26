package games;
import net.MessageType;
import net.Message;
import economy.LeaderboardManager;
import economy.EconomyManager;
import net.ClientHandler;

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
    private ClientHandler playerX;
    private ClientHandler playerO;
    private final MatchManager matchManager;
    private final EconomyManager economyManager;
    private final LeaderboardManager leaderboardManager;
    private final ReconnectRegistry reconnectRegistry;

    private final char[] board = new char[9];
    private boolean xTurn = true;
    private boolean over = false;

    /** '\0' when neither player is in a reconnect grace period; 'X' or 'O' for whichever slot's socket just dropped. Only ever set for a logged-in account (see handleDisconnect) - a guest disconnect finalizes immediately, exactly as before this feature existed. */
    private char disconnectedSlot = '\0';

    public TicTacToeMatch(String matchId, ClientHandler playerX, ClientHandler playerO,
                           MatchManager matchManager, EconomyManager economyManager, LeaderboardManager leaderboardManager,
                           ReconnectRegistry reconnectRegistry)
    {
        this.matchId = matchId;
        this.playerX = playerX;
        this.playerO = playerO;
        this.matchManager = matchManager;
        this.economyManager = economyManager;
        this.leaderboardManager = leaderboardManager;
        this.reconnectRegistry = reconnectRegistry;
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
        if (disconnectedSlot != '\0')
        {
            // Match is paused waiting for the disconnected player to reconnect - the
            // client is expected to already block input here (see TicTacToeWindow's
            // canPlay flag), but the server never trusts that and re-checks itself.
            sendRejected(requester, "Waiting for your opponent to reconnect...");
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

    public void handleDisconnect(ClientHandler who)
    {
        Integer accountIdToRegister = null;
        ClientHandler remainingForNotice = null;
        boolean bothNowGone = false;

        synchronized (this)
        {
            if (over)
            {
                return;
            }
            if (disconnectedSlot != '\0')
            {
                // The other player was already in a grace period and has now ALSO
                // disconnected - nobody left to wait for or to notify. Finalize with
                // no winner (neither side is present to receive or deserve a reward).
                over = true;
                bothNowGone = true;
            }
            else if (who.getAccountId() == null)
            {
                // Guest - no stable identity to reconnect against on a future login,
                // so there's nothing to give a grace period to. Falls back to exactly
                // the immediate-forfeit behavior this match had before reconnection
                // support existed.
                over = true;
                ClientHandler remaining = (who == playerX) ? playerO : playerX;
                sendAbandonedResult(remaining);
                economyManager.awardWin(remaining, GAME_ID);
                matchManager.endMatch(matchId);
                return;
            }
            else
            {
                disconnectedSlot = (who == playerX) ? 'X' : 'O';
                remainingForNotice = (who == playerX) ? playerO : playerX;
                accountIdToRegister = who.getAccountId();

                Message notice = new Message();
                notice.setType(MessageType.OPPONENT_DISCONNECTED_NOTICE);
                notice.setMatchId(matchId);
                notice.setBoardState(boardString());
                notice.setErrorText("Opponent disconnected - waiting to reconnect (up to 45s)...");
                remainingForNotice.sendMessage(notice);
            }
        }

        if (bothNowGone)
        {
            matchManager.endMatch(matchId);
            return;
        }

        // Deliberately called OUTSIDE the synchronized block above: ReconnectRegistry's
        // own lock must never be acquired while holding this match's lock, only the
        // reverse (see ReconnectRegistry's class-level threading note) - nesting them
        // the other way here would risk a deadlock against a concurrent tryReconnect()
        // or grace-timer callback, both of which take the registry's lock first and
        // then call back into this match.
        reconnectRegistry.beginGracePeriod(accountIdToRegister, new ReconnectRegistry.ReconnectableMatch()
        {
            public void onReconnectTimeout()
            {
                TicTacToeMatch.this.onReconnectTimeout();
            }

            public ReconnectRegistry.ReconnectResult onReconnect(ClientHandler newHandler)
            {
                return TicTacToeMatch.this.onReconnect(newHandler);
            }

            public void attachToHandler(ClientHandler handler)
            {
                handler.setCurrentMatch(TicTacToeMatch.this);
            }
        });
    }

    private synchronized void onReconnectTimeout()
    {
        if (over)
        {
            return;
        }
        over = true;
        ClientHandler remaining = (disconnectedSlot == 'X') ? playerO : playerX;
        disconnectedSlot = '\0';
        matchManager.endMatch(matchId);
        sendAbandonedResult(remaining);
        economyManager.awardWin(remaining, GAME_ID);
    }

    /** Called by ReconnectRegistry.tryReconnect() while it holds the registry's own lock - must never call back into the registry from here (see the class-level threading note on ReconnectRegistry). */
    private synchronized ReconnectRegistry.ReconnectResult onReconnect(ClientHandler newHandler)
    {
        if (over || disconnectedSlot == '\0')
        {
            // Already finalized some other way (shouldn't normally happen - the
            // registry only calls this once per grace period - but never resume a
            // match that isn't actually waiting).
            return null;
        }

        if (disconnectedSlot == 'X')
        {
            playerX = newHandler;
        }
        else
        {
            playerO = newHandler;
        }
        disconnectedSlot = '\0';

        ClientHandler opponent = (newHandler == playerX) ? playerO : playerX;
        sendUpdate(opponent);

        String mySymbol = (newHandler == playerX) ? "X" : "O";
        return new ReconnectRegistry.ReconnectResult(
            matchId, GAME_ID, mySymbol, opponent.getLoggedInUsername(), boardString(), xTurn ? "X" : "O");
    }

    private void sendAbandonedResult(ClientHandler remaining)
    {
        Message msg = new Message();
        msg.setType(MessageType.MATCH_OVER);
        msg.setMatchId(matchId);
        msg.setMatchResult("OPPONENT_LEFT");
        msg.setBoardState(boardString());
        remaining.sendMessage(msg);
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
