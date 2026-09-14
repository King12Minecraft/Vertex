package games;

import net.ClientHandler;
import net.Message;
import net.MessageType;
import economy.EconomyManager;
import economy.LeaderboardManager;

/**
 * TypingDuelMatch
 * ---------------
 * Real-time 1v1 typing race, an original design - pure speed and
 * accuracy, no luck. Both players get the exact same sentence
 * (TypingDuelSentences) at the exact same moment and race to type it
 * correctly - live progress (how many correct characters typed so
 * far) is visible to both the whole time. First to finish the
 * sentence correctly wins the round. Best of 5 rounds (first to 3
 * round wins) takes the match.
 *
 * Progress is validated server-side against the actual target
 * sentence on every keystroke report, not trusted from the client -
 * a modified client claiming "I finished" with the wrong text simply
 * won't register as a win.
 */
public class TypingDuelMatch
{
    private static final int ROUNDS_TO_WIN = 3;
    private static final String GAME_ID = "typing-duel";

    private final String matchId;
    private final ClientHandler playerA;
    private final ClientHandler playerB;
    private final TypingDuelMatchManager matchManager;
    private final EconomyManager economyManager;
    private final LeaderboardManager leaderboardManager;

    private String currentSentence;
    private int progressA = 0, progressB = 0;
    private int roundWinsA = 0, roundWinsB = 0;
    private boolean roundOver = false;
    private boolean over = false;

    public TypingDuelMatch(String matchId, ClientHandler playerA, ClientHandler playerB,
                            TypingDuelMatchManager matchManager, EconomyManager economyManager,
                            LeaderboardManager leaderboardManager)
    {
        this.matchId = matchId;
        this.playerA = playerA;
        this.playerB = playerB;
        this.matchManager = matchManager;
        this.economyManager = economyManager;
        this.leaderboardManager = leaderboardManager;
    }

    public void start()
    {
        sendMatchFound(playerA, "A", playerB.getLoggedInUsername());
        sendMatchFound(playerB, "B", playerA.getLoggedInUsername());
        startRound();
    }

    private void sendMatchFound(ClientHandler to, String symbol, String opponentUsername)
    {
        Message msg = new Message();
        msg.setType(MessageType.TYPINGDUEL_MATCH_FOUND);
        msg.setMatchId(matchId);
        msg.setSymbol(symbol);
        msg.setOpponentUsername(opponentUsername);
        to.sendMessage(msg);
    }

    private void startRound()
    {
        currentSentence = TypingDuelSentences.randomSentence();
        progressA = 0;
        progressB = 0;
        roundOver = false;

        for (ClientHandler player : new ClientHandler[] { playerA, playerB })
        {
            Message msg = new Message();
            msg.setType(MessageType.TYPINGDUEL_ROUND_START);
            msg.setMatchId(matchId);
            msg.setTriviaQuestion(currentSentence);
            msg.setTriviaScores(java.util.Arrays.asList(roundWinsA + ":" + roundWinsB));
            player.sendMessage(msg);
        }
    }

    /** typedText is the player's current full text in the input field - checked against the sentence from the start, so a correction (backspacing a typo) is reflected honestly rather than the count only ever going up. */
    public synchronized void reportProgress(ClientHandler requester, String typedText)
    {
        if (over || roundOver || typedText == null) return;
        int matchLength = matchingPrefixLength(typedText);
        boolean isA = requester == playerA;

        if (isA) progressA = matchLength; else progressB = matchLength;
        broadcastProgress();

        if (matchLength == currentSentence.length())
        {
            roundOver = true;
            if (isA) roundWinsA++; else roundWinsB++;

            if (roundWinsA >= ROUNDS_TO_WIN || roundWinsB >= ROUNDS_TO_WIN)
            {
                over = true;
                finish();
            }
            else
            {
                sendRoundResult();
                java.util.Timer delay = new java.util.Timer(true);
                delay.schedule(new java.util.TimerTask()
                {
                    public void run() { startRound(); }
                }, 2000);
            }
        }
    }

    private int matchingPrefixLength(String typedText)
    {
        int max = Math.min(typedText.length(), currentSentence.length());
        int count = 0;
        while (count < max && typedText.charAt(count) == currentSentence.charAt(count))
        {
            count++;
        }
        return count;
    }

    private void broadcastProgress()
    {
        for (ClientHandler player : new ClientHandler[] { playerA, playerB })
        {
            Message msg = new Message();
            msg.setType(MessageType.TYPINGDUEL_UPDATE);
            msg.setMatchId(matchId);
            msg.setTriviaScores(java.util.Arrays.asList(progressA + ":" + progressB));
            player.sendMessage(msg);
        }
    }

    private void sendRoundResult()
    {
        for (ClientHandler player : new ClientHandler[] { playerA, playerB })
        {
            Message msg = new Message();
            msg.setType(MessageType.TYPINGDUEL_ROUND_RESULT);
            msg.setMatchId(matchId);
            msg.setTriviaScores(java.util.Arrays.asList(roundWinsA + ":" + roundWinsB));
            player.sendMessage(msg);
        }
    }

    private void finish()
    {
        matchManager.endMatch(matchId);

        String winnerResult = roundWinsA >= ROUNDS_TO_WIN ? "A" : "B";
        recordRating(winnerResult);
        economyManager.awardWin("A".equals(winnerResult) ? playerA : playerB, GAME_ID);

        sendResult(playerA, winnerResult, roundWinsA);
        sendResult(playerB, winnerResult, roundWinsB);
    }

    private void recordRating(String winnerResult)
    {
        if (leaderboardManager == null || playerA.getAccountId() == null || playerB.getAccountId() == null)
        {
            return;
        }
        double outcomeForA = "A".equals(winnerResult) ? 1.0 : 0.0;
        leaderboardManager.recordRatedMatch(GAME_ID, playerA.getAccountId(), playerB.getAccountId(), outcomeForA);
    }

    private void sendResult(ClientHandler to, String winnerResult, int myRoundWins)
    {
        Message msg = new Message();
        msg.setType(MessageType.TYPINGDUEL_RESULT);
        msg.setMatchId(matchId);
        msg.setScore(myRoundWins);
        boolean toIsWinner = (to == playerA && "A".equals(winnerResult)) || (to == playerB && "B".equals(winnerResult));
        msg.setMatchResult(toIsWinner ? "WIN" : "LOSE");
        to.sendMessage(msg);
    }

    public synchronized void handleDisconnect(ClientHandler who)
    {
        if (over) return;
        over = true;
        matchManager.endMatch(matchId);

        ClientHandler remaining = (who == playerA) ? playerB : playerA;
        Message msg = new Message();
        msg.setType(MessageType.TYPINGDUEL_RESULT);
        msg.setMatchId(matchId);
        msg.setMatchResult("OPPONENT_LEFT");
        remaining.sendMessage(msg);

        economyManager.awardWin(remaining, GAME_ID);
    }
}
