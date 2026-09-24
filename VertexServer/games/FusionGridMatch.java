package games;

import net.ClientHandler;
import net.Message;
import net.MessageType;
import economy.EconomyManager;
import economy.LeaderboardManager;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

/**
 * FusionGridMatch
 * ---------------
 * Fusion Grid - an original concept, not a clone of any single
 * existing game (it borrows the general idea of merging equal tiles
 * from 2048, which itself has many independent implementations, but
 * combines it with competitive turn-based placement and an ownership
 * rule that 2048 has no equivalent of).
 *
 * 6x6 grid. Each turn the current player is given a random tile
 * value (2, 4, or 8 - weighted toward 2) and places it on any empty
 * cell. Placing checks the tile's 4 neighbors: any neighbor with the
 * SAME value AND owned by the SAME player merges into the new tile,
 * doubling its value and scoring that new value for the player - this
 * can cascade (the doubled tile might now match another of the same
 * player's neighbors) since it's bounded by the grid's size, it
 * always terminates. A same-value tile owned by the OTHER player
 * never merges - it just sits there as a blocker, which is the whole
 * strategic twist: you're racing to grow your own connected cluster
 * while your opponent's tiles get in the way rather than helping you.
 * Game ends when the grid is completely full; highest total score
 * (not highest single tile) wins.
 */
public class FusionGridMatch
{
    public static final int GRID_SIZE = 6;
    private static final String GAME_ID = "fusion-grid";

    private final String matchId;
    private final ClientHandler playerA;
    private final ClientHandler playerB;
    private final FusionGridMatchManager matchManager;
    private final EconomyManager economyManager;
    private final LeaderboardManager leaderboardManager;
    private final Random random = new Random();

    private final int[] values = new int[GRID_SIZE * GRID_SIZE];
    private final int[] owners = new int[GRID_SIZE * GRID_SIZE];
    private int turnPlayerIndex = 0;
    private int currentTileValue;
    private int scoreA = 0, scoreB = 0;
    private boolean over = false;

    public FusionGridMatch(String matchId, ClientHandler playerA, ClientHandler playerB,
                            FusionGridMatchManager matchManager, EconomyManager economyManager,
                            LeaderboardManager leaderboardManager)
    {
        this.matchId = matchId;
        this.playerA = playerA;
        this.playerB = playerB;
        this.matchManager = matchManager;
        this.economyManager = economyManager;
        this.leaderboardManager = leaderboardManager;
        java.util.Arrays.fill(owners, -1);
        drawNextTile();
    }

    private void drawNextTile()
    {
        currentTileValue = randomTileValue();
    }

    /** Public/static so FusionGridWindow's Practice mode (fully offline, no server) can draw tiles with the exact same weighting an online match uses. */
    public static int randomTileValue()
    {
        double roll = new java.util.Random().nextDouble();
        return roll < 0.6 ? 2 : roll < 0.9 ? 4 : 8;
    }

    public void start()
    {
        sendMatchFound(playerA, "0", playerB.getLoggedInUsername());
        sendMatchFound(playerB, "1", playerA.getLoggedInUsername());
    }

    private void sendMatchFound(ClientHandler to, String symbol, String opponentUsername)
    {
        Message msg = new Message();
        msg.setType(MessageType.FUSIONGRID_MATCH_FOUND);
        msg.setMatchId(matchId);
        msg.setSymbol(symbol);
        msg.setOpponentUsername(opponentUsername);
        msg.setBoardState(stateString());
        to.sendMessage(msg);
    }

    public synchronized void placeTile(ClientHandler requester, int index)
    {
        if (over || index < 0 || index >= values.length) return;
        int playerIndex = requester == playerA ? 0 : requester == playerB ? 1 : -1;
        if (playerIndex != turnPlayerIndex || owners[index] != -1) return;

        values[index] = currentTileValue;
        owners[index] = playerIndex;

        int gained = cascadeMerge(index, playerIndex);
        if (playerIndex == 0) scoreA += gained; else scoreB += gained;

        if (isFull())
        {
            over = true;
            finish();
            return;
        }

        turnPlayerIndex = 1 - turnPlayerIndex;
        drawNextTile();
        broadcastUpdate();
    }

    /** Merges the tile at startIndex into any same-value, same-owner neighbor, repeating for the resulting (doubled) tile against ITS neighbors, and so on - a flood-fill-style cascade bounded by the grid's fixed size, so it always terminates. Returns the total value gained from all merges this placement caused (added to the player's score). */
    private int cascadeMerge(int startIndex, int playerIndex)
    {
        int totalGained = 0;
        Deque<Integer> toCheck = new ArrayDeque<Integer>();
        toCheck.add(startIndex);

        while (!toCheck.isEmpty())
        {
            int index = toCheck.poll();
            int value = values[index];

            for (int neighbor : neighborsOf(index))
            {
                if (owners[neighbor] == playerIndex && values[neighbor] == value)
                {
                    owners[neighbor] = -1;
                    values[neighbor] = 0;
                    values[index] = value * 2;
                    totalGained += values[index];
                    toCheck.add(index);
                    break;
                }
            }
        }
        return totalGained;
    }

    private int[] neighborsOf(int index)
    {
        int row = index / GRID_SIZE, col = index % GRID_SIZE;
        java.util.List<Integer> result = new java.util.ArrayList<Integer>();
        if (row > 0) result.add(index - GRID_SIZE);
        if (row < GRID_SIZE - 1) result.add(index + GRID_SIZE);
        if (col > 0) result.add(index - 1);
        if (col < GRID_SIZE - 1) result.add(index + 1);
        int[] array = new int[result.size()];
        for (int i = 0; i < array.length; i++) array[i] = result.get(i);
        return array;
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
            msg.setType(MessageType.FUSIONGRID_UPDATE);
            msg.setMatchId(matchId);
            msg.setBoardState(state);
            msg.setSymbol(String.valueOf(turnPlayerIndex));
            player.sendMessage(msg);
        }
    }

    /** "nextTileValue|Ax:val,Bx:val,...|scoreA,scoreB" - cells with owner -1 are omitted, tile-value entries are "index:value" pairs prefixed with the owning player's digit. */
    private String stateString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(currentTileValue).append("|");
        boolean first = true;
        for (int i = 0; i < values.length; i++)
        {
            if (owners[i] == -1) continue;
            if (!first) sb.append(",");
            sb.append(owners[i]).append(":").append(i).append(":").append(values[i]);
            first = false;
        }
        sb.append("|").append(scoreA).append(",").append(scoreB);
        return sb.toString();
    }

    private void finish()
    {
        matchManager.endMatch(matchId);

        String winnerResult = scoreA == scoreB ? "DRAW" : (scoreA > scoreB ? "0" : "1");
        recordRating(winnerResult);

        if (!"DRAW".equals(winnerResult))
        {
            economyManager.awardWin("0".equals(winnerResult) ? playerA : playerB, GAME_ID);
        }

        sendResult(playerA, winnerResult, scoreA);
        sendResult(playerB, winnerResult, scoreB);
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

    private void sendResult(ClientHandler to, String winnerResult, int myScore)
    {
        Message msg = new Message();
        msg.setType(MessageType.FUSIONGRID_RESULT);
        msg.setMatchId(matchId);
        msg.setBoardState(stateString());
        msg.setScore(myScore);
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
        msg.setType(MessageType.FUSIONGRID_RESULT);
        msg.setMatchId(matchId);
        msg.setMatchResult("OPPONENT_LEFT");
        msg.setBoardState(stateString());
        remaining.sendMessage(msg);

        economyManager.awardWin(remaining, GAME_ID);
    }
}
