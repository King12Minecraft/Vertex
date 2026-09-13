package games;

import net.ClientHandler;
import net.Message;
import net.MessageType;
import economy.EconomyManager;
import economy.LeaderboardManager;
import economy.EconomyConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * MemoryMatchMatch
 * ----------------
 * Turn-based 1v1 card matching (Concentration), an original
 * implementation of the well-known, uncopyrightable game. 4x4 grid,
 * 8 symbol pairs shuffled into 16 positions. On your turn, flip two
 * cards - a match keeps them revealed permanently under your name and
 * earns another turn; a miss flips both back face-down after a brief
 * pause and passes the turn. Most pairs once the whole board is
 * cleared wins.
 *
 * Board state sent to clients only ever reveals a position's actual
 * symbol once it's been flipped (this turn or permanently matched) -
 * '.' for anything still face-down - so there's nothing for a
 * modified client to peek at that a real player couldn't already see
 * on screen.
 */
public class MemoryMatchMatch
{
    public static final int CARD_COUNT = 16;
    public static final int PAIR_COUNT = CARD_COUNT / 2;
    private static final long MISMATCH_REVEAL_MS = 1200;
    private static final String GAME_ID = "memory-match";
    private static final char[] SYMBOLS = { 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H' };

    private final String matchId;
    private final ClientHandler playerA;
    private final ClientHandler playerB;
    private final MemoryMatchMatchManager matchManager;
    private final EconomyManager economyManager;
    private final LeaderboardManager leaderboardManager;

    private final char[] cardValues = new char[CARD_COUNT];
    private final boolean[] matched = new boolean[CARD_COUNT];
    private final int[] scores = new int[2];
    private int turnPlayerIndex = 0;
    private Integer firstFlipIndex = null;
    private boolean awaitingMismatchClear = false;
    private boolean over = false;

    public MemoryMatchMatch(String matchId, ClientHandler playerA, ClientHandler playerB,
                             MemoryMatchMatchManager matchManager, EconomyManager economyManager,
                             LeaderboardManager leaderboardManager)
    {
        this.matchId = matchId;
        this.playerA = playerA;
        this.playerB = playerB;
        this.matchManager = matchManager;
        this.economyManager = economyManager;
        this.leaderboardManager = leaderboardManager;
        shuffleCards();
    }

    private void shuffleCards()
    {
        List<Character> deck = new ArrayList<Character>();
        for (char symbol : SYMBOLS)
        {
            deck.add(symbol);
            deck.add(symbol);
        }
        Collections.shuffle(deck);
        for (int i = 0; i < CARD_COUNT; i++) cardValues[i] = deck.get(i);
    }

    public void start()
    {
        sendMatchFound(playerA, "0", playerB.getLoggedInUsername());
        sendMatchFound(playerB, "1", playerA.getLoggedInUsername());
    }

    private void sendMatchFound(ClientHandler to, String symbol, String opponentUsername)
    {
        Message msg = new Message();
        msg.setType(MessageType.MEMORY_MATCH_FOUND);
        msg.setMatchId(matchId);
        msg.setSymbol(symbol);
        msg.setOpponentUsername(opponentUsername);
        msg.setBoardState(publicBoardString());
        to.sendMessage(msg);
    }

    public synchronized void flipCard(ClientHandler requester, int index)
    {
        if (over || awaitingMismatchClear) return;
        int playerIndex = requester == playerA ? 0 : requester == playerB ? 1 : -1;
        if (playerIndex != turnPlayerIndex) return;
        if (index < 0 || index >= CARD_COUNT || matched[index]) return;
        if (firstFlipIndex != null && firstFlipIndex == index) return;

        if (firstFlipIndex == null)
        {
            firstFlipIndex = index;
            broadcastUpdate();
            return;
        }

        int secondFlipIndex = index;
        boolean isMatch = cardValues[firstFlipIndex] == cardValues[secondFlipIndex];

        if (isMatch)
        {
            matched[firstFlipIndex] = true;
            matched[secondFlipIndex] = true;
            scores[turnPlayerIndex]++;
            firstFlipIndex = null;

            if (allMatched())
            {
                over = true;
                broadcastUpdateWithReveal(secondFlipIndex);
                finish();
                return;
            }
            broadcastUpdateWithReveal(secondFlipIndex);
            // A match earns another turn - turnPlayerIndex stays the same.
        }
        else
        {
            awaitingMismatchClear = true;
            broadcastUpdateWithBothRevealed(secondFlipIndex);

            final int clearedFirst = firstFlipIndex;
            Timer delay = new Timer(true);
            delay.schedule(new TimerTask()
            {
                public void run() { clearMismatch(clearedFirst, secondFlipIndex); }
            }, MISMATCH_REVEAL_MS);
        }
    }

    private synchronized void clearMismatch(int first, int second)
    {
        if (over) return;
        firstFlipIndex = null;
        awaitingMismatchClear = false;
        turnPlayerIndex = 1 - turnPlayerIndex;
        broadcastUpdate();
    }

    private boolean allMatched()
    {
        for (boolean m : matched) if (!m) return false;
        return true;
    }

    /** Normal update - shows the permanently-matched cards plus firstFlipIndex (if a card is currently flipped waiting for its pair), everything else hidden. */
    private void broadcastUpdate()
    {
        broadcastState(publicBoardString());
    }

    /** Same as broadcastUpdate, but temporarily also reveals oneExtraIndex (used right after a successful match, so the just-flipped second card shows in the same update before firstFlipIndex gets cleared). */
    private void broadcastUpdateWithReveal(int oneExtraIndex)
    {
        char[] state = publicBoardChars();
        state[oneExtraIndex] = cardValues[oneExtraIndex];
        broadcastState(new String(state));
    }

    /** Reveals both mismatched cards (they're not "matched" so publicBoardString alone wouldn't show them) for the brief pause before they flip back down. */
    private void broadcastUpdateWithBothRevealed(int secondIndex)
    {
        char[] state = publicBoardChars();
        state[firstFlipIndex] = cardValues[firstFlipIndex];
        state[secondIndex] = cardValues[secondIndex];
        broadcastState(new String(state));
    }

    private void broadcastState(String state)
    {
        for (ClientHandler player : new ClientHandler[] { playerA, playerB })
        {
            Message msg = new Message();
            msg.setType(MessageType.MEMORY_UPDATE);
            msg.setMatchId(matchId);
            msg.setBoardState(state);
            msg.setSymbol(String.valueOf(turnPlayerIndex));
            msg.setTriviaScores(java.util.Arrays.asList(scores[0] + ":" + scores[1]));
            player.sendMessage(msg);
        }
    }

    private char[] publicBoardChars()
    {
        char[] state = new char[CARD_COUNT];
        for (int i = 0; i < CARD_COUNT; i++)
        {
            if (matched[i]) state[i] = cardValues[i];
            else if (firstFlipIndex != null && firstFlipIndex == i) state[i] = cardValues[i];
            else state[i] = '.';
        }
        return state;
    }

    private String publicBoardString()
    {
        return new String(publicBoardChars());
    }

    private void finish()
    {
        matchManager.endMatch(matchId);

        String winnerResult = scores[0] == scores[1] ? "DRAW" : (scores[0] > scores[1] ? "0" : "1");
        recordRating(winnerResult);

        if (!"DRAW".equals(winnerResult))
        {
            economyManager.awardWin("0".equals(winnerResult) ? playerA : playerB, GAME_ID);
        }

        sendResult(playerA, winnerResult, scores[0]);
        sendResult(playerB, winnerResult, scores[1]);
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
        msg.setType(MessageType.MEMORY_RESULT);
        msg.setMatchId(matchId);
        msg.setBoardState(new String(cardValues));
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
        msg.setType(MessageType.MEMORY_RESULT);
        msg.setMatchId(matchId);
        msg.setMatchResult("OPPONENT_LEFT");
        msg.setBoardState(new String(cardValues));
        remaining.sendMessage(msg);

        economyManager.awardWin(remaining, GAME_ID);
    }
}
