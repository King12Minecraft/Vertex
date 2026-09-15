package games;

import net.ClientHandler;
import net.Message;
import net.MessageType;
import economy.EconomyManager;
import economy.LeaderboardManager;

/**
 * SignalGridMatch
 * ---------------
 * Signal Grid - an original concept, not based on any existing game.
 * 8x8 grid. On your turn: place one node of your color in any empty
 * cell, then fire it in one direction (up/down/left/right). The
 * signal travels in a straight line from the node you just placed
 * until it hits the grid edge or another node - if that node belongs
 * to your opponent, it flips to your color (captured); if it's your
 * own, the signal just fizzles out against it, nothing happens
 * either way. Turns alternate regardless of what the signal hit.
 * Game ends when the grid is completely full; most nodes of your
 * color wins.
 *
 * The strategic idea: you're not just placing pieces, you're aiming
 * a one-shot capture through whatever line-of-sight the board's
 * current layout gives you - so the board you and your opponent have
 * been building together becomes the very thing you're shooting
 * through, and defending a node means blocking the lines that could
 * reach it, not just avoiding the cell itself.
 */
public class SignalGridMatch
{
    public static final int GRID_SIZE = 8;
    public static final int UP = 0, DOWN = 1, LEFT = 2, RIGHT = 3;
    private static final String GAME_ID = "signal-grid";

    private final String matchId;
    private final ClientHandler playerA;
    private final ClientHandler playerB;
    private final SignalGridMatchManager matchManager;
    private final EconomyManager economyManager;
    private final LeaderboardManager leaderboardManager;

    private final int[] owners = new int[GRID_SIZE * GRID_SIZE];
    private int turnPlayerIndex = 0;
    private boolean over = false;

    public SignalGridMatch(String matchId, ClientHandler playerA, ClientHandler playerB,
                            SignalGridMatchManager matchManager, EconomyManager economyManager,
                            LeaderboardManager leaderboardManager)
    {
        this.matchId = matchId;
        this.playerA = playerA;
        this.playerB = playerB;
        this.matchManager = matchManager;
        this.economyManager = economyManager;
        this.leaderboardManager = leaderboardManager;
        java.util.Arrays.fill(owners, -1);
    }

    public void start()
    {
        sendMatchFound(playerA, "0", playerB.getLoggedInUsername());
        sendMatchFound(playerB, "1", playerA.getLoggedInUsername());
    }

    private void sendMatchFound(ClientHandler to, String symbol, String opponentUsername)
    {
        Message msg = new Message();
        msg.setType(MessageType.SIGNALGRID_MATCH_FOUND);
        msg.setMatchId(matchId);
        msg.setSymbol(symbol);
        msg.setOpponentUsername(opponentUsername);
        msg.setBoardState(stateString());
        to.sendMessage(msg);
    }

    /** Places a node at index, then fires it in the given direction - see the class javadoc for exactly what firing does. Both the placement cell and direction are chosen by the player in one move (the client sends both together). */
    public synchronized void placeAndFire(ClientHandler requester, int index, int direction)
    {
        if (over || index < 0 || index >= owners.length || direction < 0 || direction > 3) return;
        int playerIndex = requester == playerA ? 0 : requester == playerB ? 1 : -1;
        if (playerIndex != turnPlayerIndex || owners[index] != -1) return;

        owners[index] = playerIndex;
        fireSignal(index, direction, playerIndex);

        if (isFull())
        {
            over = true;
            finish();
            return;
        }

        turnPlayerIndex = 1 - turnPlayerIndex;
        broadcastUpdate();
    }

    private void fireSignal(int fromIndex, int direction, int playerIndex)
    {
        int row = fromIndex / GRID_SIZE, col = fromIndex % GRID_SIZE;
        int dr = direction == UP ? -1 : direction == DOWN ? 1 : 0;
        int dc = direction == LEFT ? -1 : direction == RIGHT ? 1 : 0;

        int r = row + dr, c = col + dc;
        while (r >= 0 && r < GRID_SIZE && c >= 0 && c < GRID_SIZE)
        {
            int hitIndex = r * GRID_SIZE + c;
            if (owners[hitIndex] != -1)
            {
                if (owners[hitIndex] != playerIndex)
                {
                    owners[hitIndex] = playerIndex;
                }
                return;
            }
            r += dr;
            c += dc;
        }
    }

    private boolean isFull()
    {
        for (int owner : owners) if (owner == -1) return false;
        return true;
    }

    private void broadcastUpdate()
    {
        String state = stateString();
        for (ClientHandler player : new ClientHandler[] { playerA, playerB })
        {
            Message msg = new Message();
            msg.setType(MessageType.SIGNALGRID_UPDATE);
            msg.setMatchId(matchId);
            msg.setBoardState(state);
            msg.setSymbol(String.valueOf(turnPlayerIndex));
            player.sendMessage(msg);
        }
    }

    private String stateString()
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < owners.length; i++)
        {
            if (i > 0) sb.append(",");
            sb.append(owners[i]);
        }
        return sb.toString();
    }

    private void finish()
    {
        matchManager.endMatch(matchId);

        int countA = 0, countB = 0;
        for (int owner : owners)
        {
            if (owner == 0) countA++;
            else if (owner == 1) countB++;
        }

        String winnerResult = countA == countB ? "DRAW" : (countA > countB ? "0" : "1");
        recordRating(winnerResult);

        if (!"DRAW".equals(winnerResult))
        {
            economyManager.awardWin("0".equals(winnerResult) ? playerA : playerB, GAME_ID);
        }

        sendResult(playerA, winnerResult, countA);
        sendResult(playerB, winnerResult, countB);
    }

    private void recordRating(String winnerResult)
    {
        if (leaderboardManager == null || playerA.getAccountId() == null || playerB.getAccountId() == null)
        {
            return;
        }
        double outcomeForA = "DRAW".equals(winnerResult) ? 0.5 : "0".equals(winnerResult) ? 1.0 : 0.0;
        leaderboardManager.recordRatedMatch(GAME_ID, playerA.getAccountId(), playerB.getAccountId(), outcomeForA);
    }

    private void sendResult(ClientHandler to, String winnerResult, int myCount)
    {
        Message msg = new Message();
        msg.setType(MessageType.SIGNALGRID_RESULT);
        msg.setMatchId(matchId);
        msg.setBoardState(stateString());
        msg.setScore(myCount);
        boolean toIsWinner = (to == playerA && "0".equals(winnerResult)) || (to == playerB && "1".equals(winnerResult));
        msg.setMatchResult("DRAW".equals(winnerResult) ? "DRAW" : (toIsWinner ? "WIN" : "LOSE"));
        to.sendMessage(msg);
    }

    public synchronized void handleDisconnect(ClientHandler who)
    {
        if (over) return;
        over = true;
        matchManager.endMatch(matchId);

        ClientHandler remaining = (who == playerA) ? playerB : playerA;
        Message msg = new Message();
        msg.setType(MessageType.SIGNALGRID_RESULT);
        msg.setMatchId(matchId);
        msg.setMatchResult("OPPONENT_LEFT");
        msg.setBoardState(stateString());
        remaining.sendMessage(msg);

        economyManager.awardWin(remaining, GAME_ID);
    }
}
