package games;

import net.ClientHandler;
import net.Message;
import net.MessageType;
import economy.EconomyManager;
import economy.LeaderboardManager;

import java.util.Random;

/**
 * DiceDuelMatch
 * -------------
 * Turn-based 1v1 push-your-luck dice game, an original design in the
 * spirit of Yahtzee's scoring categories (a well-known, uncopyrightable
 * genre of dice game - this uses a deliberately smaller, original set
 * of 6 categories rather than reproducing Yahtzee's exact 13). Each
 * turn: roll all 5 dice, then choose up to 2 times to re-roll any
 * subset of them (keeping the rest), then lock in a score into one of
 * the 6 categories - each category can only be used once per player
 * across the whole match. Once both players have filled all 6
 * categories, highest total score wins.
 *
 * Categories: ONES/TWOS/.../SIXES (sum of that face value's dice -
 * the same "upper section" idea Yahtzee uses) and THREE_OF_A_KIND
 * (sum of all 5 dice, only if 3+ show the same face).
 */
public class DiceDuelMatch
{
    public static final int DICE_COUNT = 5;
    public static final int MAX_REROLLS_PER_TURN = 2;
    public static final String[] CATEGORIES = { "ONES", "TWOS", "THREES", "FOURS", "FIVES", "SIXES", "THREE_OF_A_KIND" };
    private static final String GAME_ID = "dice-duel";

    private final String matchId;
    private final ClientHandler playerA;
    private final ClientHandler playerB;
    private final DiceDuelMatchManager matchManager;
    private final EconomyManager economyManager;
    private final LeaderboardManager leaderboardManager;
    private final Random random = new Random();

    private final int[] dice = new int[DICE_COUNT];
    private final boolean[] held = new boolean[DICE_COUNT];
    private int rerollsUsedThisTurn = 0;
    private int turnPlayerIndex = 0;
    private final java.util.Map<String, Integer> scoresA = new java.util.LinkedHashMap<String, Integer>();
    private final java.util.Map<String, Integer> scoresB = new java.util.LinkedHashMap<String, Integer>();
    private boolean over = false;

    public DiceDuelMatch(String matchId, ClientHandler playerA, ClientHandler playerB,
                          DiceDuelMatchManager matchManager, EconomyManager economyManager,
                          LeaderboardManager leaderboardManager)
    {
        this.matchId = matchId;
        this.playerA = playerA;
        this.playerB = playerB;
        this.matchManager = matchManager;
        this.economyManager = economyManager;
        this.leaderboardManager = leaderboardManager;
        rollAllDice();
    }

    public void start()
    {
        sendMatchFound(playerA, "0", playerB.getLoggedInUsername());
        sendMatchFound(playerB, "1", playerA.getLoggedInUsername());
    }

    private void sendMatchFound(ClientHandler to, String symbol, String opponentUsername)
    {
        Message msg = new Message();
        msg.setType(MessageType.DICEDUEL_MATCH_FOUND);
        msg.setMatchId(matchId);
        msg.setSymbol(symbol);
        msg.setOpponentUsername(opponentUsername);
        msg.setBoardState(stateString());
        to.sendMessage(msg);
    }

    private void rollAllDice()
    {
        for (int i = 0; i < DICE_COUNT; i++)
        {
            if (!held[i]) dice[i] = 1 + random.nextInt(6);
        }
    }

    /** Re-rolls only the dice at the given indices (leaving the rest as-is) - up to MAX_REROLLS_PER_TURN times per turn. */
    public synchronized void reroll(ClientHandler requester, java.util.List<Integer> indicesToReroll)
    {
        if (over || !isRequestersTurn(requester)) return;
        if (rerollsUsedThisTurn >= MAX_REROLLS_PER_TURN) return;

        for (int index : indicesToReroll)
        {
            if (index >= 0 && index < DICE_COUNT)
            {
                dice[index] = 1 + random.nextInt(6);
            }
        }
        rerollsUsedThisTurn++;
        broadcastUpdate();
    }

    /** Locks in the current dice into the given category for the requesting player - each category can only be used once per player. Ends their turn and rolls a fresh set of dice for the next player. */
    public synchronized void lockCategory(ClientHandler requester, String category)
    {
        if (over || !isRequestersTurn(requester)) return;

        java.util.Map<String, Integer> myScores = requester == playerA ? scoresA : scoresB;
        boolean validCategory = false;
        for (String c : CATEGORIES) if (c.equals(category)) validCategory = true;
        if (!validCategory || myScores.containsKey(category)) return;

        myScores.put(category, scoreFor(category, dice));

        if (scoresA.size() == CATEGORIES.length && scoresB.size() == CATEGORIES.length)
        {
            over = true;
            finish();
            return;
        }

        turnPlayerIndex = 1 - turnPlayerIndex;
        rerollsUsedThisTurn = 0;
        java.util.Arrays.fill(held, false);
        rollAllDice();
        broadcastUpdate();
    }

    private boolean isRequestersTurn(ClientHandler requester)
    {
        int playerIndex = requester == playerA ? 0 : requester == playerB ? 1 : -1;
        return playerIndex == turnPlayerIndex;
    }

    /** Public/static so DiceDuelBotStrategy can score hypothetical categories too, not just DiceDuelMatch itself - it's already stateless, just promoted from a private instance method. */
    public static int scoreFor(String category, int[] roll)
    {
        int[] counts = new int[7];
        for (int value : roll) counts[value]++;

        for (int face = 1; face <= 6; face++)
        {
            if (category.equals(faceCategoryName(face)))
            {
                return counts[face] * face;
            }
        }
        if ("THREE_OF_A_KIND".equals(category))
        {
            for (int face = 1; face <= 6; face++)
            {
                if (counts[face] >= 3)
                {
                    int sum = 0;
                    for (int value : roll) sum += value;
                    return sum;
                }
            }
            return 0;
        }
        return 0;
    }

    public static String faceCategoryName(int face)
    {
        String[] names = { "ONES", "TWOS", "THREES", "FOURS", "FIVES", "SIXES" };
        return names[face - 1];
    }

    private void broadcastUpdate()
    {
        String state = stateString();
        for (ClientHandler player : new ClientHandler[] { playerA, playerB })
        {
            Message msg = new Message();
            msg.setType(MessageType.DICEDUEL_UPDATE);
            msg.setMatchId(matchId);
            msg.setBoardState(state);
            msg.setSymbol(String.valueOf(turnPlayerIndex));
            player.sendMessage(msg);
        }
    }

    /** "d1,d2,d3,d4,d5|rerollsUsed|CAT:score,CAT:score,...(playerA's filled categories)|CAT:score,...(playerB's)" */
    private String stateString()
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < DICE_COUNT; i++)
        {
            if (i > 0) sb.append(",");
            sb.append(dice[i]);
        }
        sb.append("|").append(rerollsUsedThisTurn);
        sb.append("|").append(scoresToString(scoresA));
        sb.append("|").append(scoresToString(scoresB));
        return sb.toString();
    }

    private String scoresToString(java.util.Map<String, Integer> scores)
    {
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, Integer> entry : scores.entrySet())
        {
            if (sb.length() > 0) sb.append(",");
            sb.append(entry.getKey()).append(":").append(entry.getValue());
        }
        return sb.toString();
    }

    private void finish()
    {
        matchManager.endMatch(matchId);

        int totalA = sum(scoresA), totalB = sum(scoresB);
        String winnerResult = totalA == totalB ? "DRAW" : (totalA > totalB ? "0" : "1");
        recordRating(winnerResult);

        if (!"DRAW".equals(winnerResult))
        {
            economyManager.awardWin("0".equals(winnerResult) ? playerA : playerB, GAME_ID);
        }

        sendResult(playerA, winnerResult, totalA);
        sendResult(playerB, winnerResult, totalB);
    }

    private int sum(java.util.Map<String, Integer> scores)
    {
        int total = 0;
        for (int value : scores.values()) total += value;
        return total;
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

    private void sendResult(ClientHandler to, String winnerResult, int myTotal)
    {
        Message msg = new Message();
        msg.setType(MessageType.DICEDUEL_RESULT);
        msg.setMatchId(matchId);
        msg.setBoardState(stateString());
        msg.setScore(myTotal);
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
        msg.setType(MessageType.DICEDUEL_RESULT);
        msg.setMatchId(matchId);
        msg.setMatchResult("OPPONENT_LEFT");
        msg.setBoardState(stateString());
        remaining.sendMessage(msg);

        economyManager.awardWin(remaining, GAME_ID);
    }
}
