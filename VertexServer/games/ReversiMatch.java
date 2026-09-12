package games;

import net.ClientHandler;
import net.Message;
import net.MessageType;
import economy.EconomyManager;
import economy.LeaderboardManager;

/**
 * ReversiMatch
 * ------------
 * Standard Reversi/Othello rules, 1v1, ELO-rated - an original
 * implementation of the well-known, decades-old, uncopyrightable
 * game. 8x8 board, starts with the standard 4-piece diagonal center
 * setup. Placing a piece that "flanks" one or more opponent pieces in
 * a straight line (any of the 8 directions) flips every flanked
 * piece to your color - the core mechanic that makes this a genuine
 * strategy game rather than just alternating placement.
 *
 * If the player to move has no legal move anywhere on the board,
 * their turn is skipped automatically (the real rule - Reversi
 * doesn't let you pass voluntarily, only when truly forced to). The
 * game ends once NEITHER player has a legal move; most pieces wins.
 */
public class ReversiMatch
{
    public static final int SIZE = 8;
    private static final String GAME_ID = "reversi";
    private static final int[][] DIRECTIONS = {
        {-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}
    };

    private final String matchId;
    private final ClientHandler blackPlayer;
    private final ClientHandler whitePlayer;
    private final ReversiMatchManager matchManager;
    private final EconomyManager economyManager;
    private final LeaderboardManager leaderboardManager;

    private final char[] board = new char[SIZE * SIZE];
    private boolean blackTurn = true;
    private boolean over = false;

    public ReversiMatch(String matchId, ClientHandler blackPlayer, ClientHandler whitePlayer,
                         ReversiMatchManager matchManager, EconomyManager economyManager,
                         LeaderboardManager leaderboardManager)
    {
        this.matchId = matchId;
        this.blackPlayer = blackPlayer;
        this.whitePlayer = whitePlayer;
        this.matchManager = matchManager;
        this.economyManager = economyManager;
        this.leaderboardManager = leaderboardManager;
        setupBoard();
    }

    private void setupBoard()
    {
        java.util.Arrays.fill(board, '.');
        int mid = SIZE / 2;
        board[(mid - 1) * SIZE + (mid - 1)] = 'W';
        board[(mid - 1) * SIZE + mid] = 'B';
        board[mid * SIZE + (mid - 1)] = 'B';
        board[mid * SIZE + mid] = 'W';
    }

    public void start()
    {
        sendMatchFound(blackPlayer, "BLACK", whitePlayer.getLoggedInUsername());
        sendMatchFound(whitePlayer, "WHITE", blackPlayer.getLoggedInUsername());
        broadcastUpdate();
    }

    private void sendMatchFound(ClientHandler to, String symbol, String opponentUsername)
    {
        Message msg = new Message();
        msg.setType(MessageType.REVERSI_MATCH_FOUND);
        msg.setMatchId(matchId);
        msg.setSymbol(symbol);
        msg.setOpponentUsername(opponentUsername);
        msg.setBoardState(boardString());
        to.sendMessage(msg);
    }

    public synchronized void placePiece(ClientHandler requester, int index)
    {
        if (over) return;
        boolean isBlack = requester == blackPlayer;
        if (isBlack != blackTurn)
        {
            sendRejected(requester, "It's not your turn.");
            return;
        }
        if (index < 0 || index >= board.length || board[index] != '.')
        {
            sendRejected(requester, "Illegal move.");
            return;
        }

        java.util.List<Integer> toFlip = flanksFor(index, isBlack);
        if (toFlip.isEmpty())
        {
            sendRejected(requester, "That move doesn't flank any pieces.");
            return;
        }

        board[index] = isBlack ? 'B' : 'W';
        for (int flip : toFlip) board[flip] = isBlack ? 'B' : 'W';

        advanceTurn();
    }

    /** Passes to the other player if they have a legal move; skips them again (back to the same player) if they don't; ends the match if NEITHER player has one anywhere. */
    private void advanceTurn()
    {
        blackTurn = !blackTurn;

        if (hasAnyLegalMove(blackTurn))
        {
            broadcastUpdate();
            return;
        }

        boolean otherHasMove = hasAnyLegalMove(!blackTurn);
        if (!otherHasMove)
        {
            over = true;
            finish();
            return;
        }

        // Current side has no legal move but the other side does - skip back to them.
        blackTurn = !blackTurn;
        broadcastUpdate();
    }

    private boolean hasAnyLegalMove(boolean forBlack)
    {
        for (int i = 0; i < board.length; i++)
        {
            if (board[i] == '.' && !flanksFor(i, forBlack).isEmpty())
            {
                return true;
            }
        }
        return false;
    }

    /** Every opponent cell that placing a piece of the given color at fromIndex would flip - empty if this isn't actually a legal move here. */
    private java.util.List<Integer> flanksFor(int fromIndex, boolean isBlack)
    {
        java.util.List<Integer> result = new java.util.ArrayList<Integer>();
        int fromRow = fromIndex / SIZE, fromCol = fromIndex % SIZE;
        char own = isBlack ? 'B' : 'W';
        char enemy = isBlack ? 'W' : 'B';

        for (int[] dir : DIRECTIONS)
        {
            java.util.List<Integer> line = new java.util.ArrayList<Integer>();
            int r = fromRow + dir[0], c = fromCol + dir[1];
            while (r >= 0 && r < SIZE && c >= 0 && c < SIZE && board[r * SIZE + c] == enemy)
            {
                line.add(r * SIZE + c);
                r += dir[0];
                c += dir[1];
            }
            if (!line.isEmpty() && r >= 0 && r < SIZE && c >= 0 && c < SIZE && board[r * SIZE + c] == own)
            {
                result.addAll(line);
            }
        }
        return result;
    }

    private void broadcastUpdate()
    {
        String state = boardString();
        for (ClientHandler player : new ClientHandler[] { blackPlayer, whitePlayer })
        {
            Message msg = new Message();
            msg.setType(MessageType.REVERSI_UPDATE);
            msg.setMatchId(matchId);
            msg.setBoardState(state);
            msg.setSymbol(blackTurn ? "BLACK" : "WHITE");
            player.sendMessage(msg);
        }
    }

    private void finish()
    {
        matchManager.endMatch(matchId);

        int blackCount = 0, whiteCount = 0;
        for (char c : board)
        {
            if (c == 'B') blackCount++;
            else if (c == 'W') whiteCount++;
        }

        String winnerSymbol = blackCount == whiteCount ? "DRAW" : (blackCount > whiteCount ? "BLACK" : "WHITE");
        recordRating(winnerSymbol);

        if (!"DRAW".equals(winnerSymbol))
        {
            economyManager.awardWin("BLACK".equals(winnerSymbol) ? blackPlayer : whitePlayer, GAME_ID);
        }

        sendResult(blackPlayer, winnerSymbol, blackCount);
        sendResult(whitePlayer, winnerSymbol, whiteCount);
    }

    private void recordRating(String winnerSymbol)
    {
        if (leaderboardManager == null || blackPlayer.getAccountId() == null || whitePlayer.getAccountId() == null)
        {
            return;
        }
        double outcomeForBlack = "DRAW".equals(winnerSymbol) ? 0.5 : "BLACK".equals(winnerSymbol) ? 1.0 : 0.0;
        leaderboardManager.recordRatedMatch(GAME_ID, blackPlayer.getAccountId(), whitePlayer.getAccountId(), outcomeForBlack);
    }

    private void sendResult(ClientHandler to, String winnerSymbol, int myCount)
    {
        Message msg = new Message();
        msg.setType(MessageType.REVERSI_RESULT);
        msg.setMatchId(matchId);
        msg.setBoardState(boardString());
        msg.setScore(myCount);
        boolean toIsWinner = (to == blackPlayer && "BLACK".equals(winnerSymbol)) || (to == whitePlayer && "WHITE".equals(winnerSymbol));
        msg.setMatchResult("DRAW".equals(winnerSymbol) ? "DRAW" : (toIsWinner ? "WIN" : "LOSE"));
        to.sendMessage(msg);
    }

    private void sendRejected(ClientHandler to, String reason)
    {
        Message msg = new Message();
        msg.setType(MessageType.REVERSI_MOVE_REJECTED);
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

        ClientHandler remaining = (who == blackPlayer) ? whitePlayer : blackPlayer;
        Message msg = new Message();
        msg.setType(MessageType.REVERSI_RESULT);
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
