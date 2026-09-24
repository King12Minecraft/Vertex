package games;

import net.ClientHandler;
import net.Message;
import net.MessageType;
import economy.EconomyManager;
import economy.LeaderboardManager;

import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

/**
 * WordDuelMatch
 * -------------
 * Simultaneous 1v1 word game, an original design. Both players get
 * the same 9 randomly-drawn letters (weighted toward common English
 * letter frequency, same idea Scrabble/Boggle tile bags use, so a
 * draw isn't hopelessly consonant-heavy) and have 60 seconds to
 * submit the longest valid word they can build from them - each
 * letter usable only as many times as it appears in the draw.
 * Submitting a new word only updates a player's best if it's both
 * valid (WordDuelWordList) and longer than their current best. Longest
 * word when time runs out wins; a tie is a draw.
 */
public class WordDuelMatch
{
    private static final int LETTER_COUNT = 9;
    private static final long ROUND_DURATION_MS = 60_000;
    private static final String GAME_ID = "word-duel";

    /** Roughly weighted toward common English letter frequency (more vowels and common consonants, few Q/X/Z) so a draw is usually workable rather than hopeless. */
    private static final String LETTER_POOL =
        "AAAAAAAAABBCCDDDDEEEEEEEEEEEEFFGGGHHIIIIIIIIIJKLLLLMMNNNNNNOOOOOOOOPPQRRRRRRSSSSTTTTTTUUUUVVWWXYYZ";

    private final String matchId;
    private final ClientHandler playerA;
    private final ClientHandler playerB;
    private final WordDuelMatchManager matchManager;
    private final EconomyManager economyManager;
    private final LeaderboardManager leaderboardManager;

    private final String letters;
    private String bestWordA = "";
    private String bestWordB = "";
    private boolean over = false;
    private Timer roundTimer;

    public WordDuelMatch(String matchId, ClientHandler playerA, ClientHandler playerB,
                          WordDuelMatchManager matchManager, EconomyManager economyManager,
                          LeaderboardManager leaderboardManager)
    {
        this.matchId = matchId;
        this.playerA = playerA;
        this.playerB = playerB;
        this.matchManager = matchManager;
        this.economyManager = economyManager;
        this.leaderboardManager = leaderboardManager;
        this.letters = drawLetters();
    }

    /** Public/static so WordDuelWindow's Practice mode (fully offline, no server) can draw its own letters the exact same way an online match does - it's already stateless, just promoted from a private instance method. */
    public static String drawLetters()
    {
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < LETTER_COUNT; i++)
        {
            sb.append(LETTER_POOL.charAt(random.nextInt(LETTER_POOL.length())));
        }
        return sb.toString();
    }

    public void start()
    {
        sendMatchFound(playerA, playerB.getLoggedInUsername());
        sendMatchFound(playerB, playerA.getLoggedInUsername());

        roundTimer = new Timer(true);
        roundTimer.schedule(new TimerTask()
        {
            public void run() { finish(); }
        }, ROUND_DURATION_MS);
    }

    private void sendMatchFound(ClientHandler to, String opponentUsername)
    {
        Message msg = new Message();
        msg.setType(MessageType.WORDDUEL_MATCH_FOUND);
        msg.setMatchId(matchId);
        msg.setOpponentUsername(opponentUsername);
        msg.setTriviaQuestion(letters);
        to.sendMessage(msg);
    }

    /** A submission only updates that player's best if it's a real word (WordDuelWordList), only uses letters actually in the draw (respecting how many of each letter are available), and beats their current best length - shorter or invalid submissions are quietly rejected rather than treated as an error, since someone testing out words as they think of them is the expected way to play. */
    public synchronized void submitWord(ClientHandler requester, String word)
    {
        if (over || word == null) return;
        String candidate = word.trim().toLowerCase();
        if (candidate.isEmpty() || !WordDuelWordList.canBeFormedFrom(candidate, letters) || !WordDuelWordList.isValidWord(candidate))
        {
            return;
        }

        boolean isA = requester == playerA;
        String currentBest = isA ? bestWordA : bestWordB;
        if (candidate.length() <= currentBest.length())
        {
            return;
        }

        if (isA) bestWordA = candidate; else bestWordB = candidate;
        broadcastProgress();
    }

    /** Only reveals each player's current best LENGTH to the other side while the round is live, not the word itself - actual words are only revealed once the round ends, keeping some suspense. */
    private void broadcastProgress()
    {
        for (ClientHandler player : new ClientHandler[] { playerA, playerB })
        {
            Message msg = new Message();
            msg.setType(MessageType.WORDDUEL_UPDATE);
            msg.setMatchId(matchId);
            msg.setTriviaScores(java.util.Arrays.asList(bestWordA.length() + ":" + bestWordB.length()));
            player.sendMessage(msg);
        }
    }

    private synchronized void finish()
    {
        if (over) return;
        over = true;
        matchManager.endMatch(matchId);

        int lenA = bestWordA.length(), lenB = bestWordB.length();
        String winnerResult = lenA == lenB ? "DRAW" : (lenA > lenB ? "A" : "B");
        recordRating(winnerResult);

        if (!"DRAW".equals(winnerResult))
        {
            economyManager.awardWin("A".equals(winnerResult) ? playerA : playerB, GAME_ID);
        }

        sendResult(playerA, winnerResult, bestWordA, bestWordB);
        sendResult(playerB, winnerResult, bestWordB, bestWordA);
    }

    private void recordRating(String winnerResult)
    {
        if (leaderboardManager == null || playerA.getAccountId() == null || playerB.getAccountId() == null)
        {
            return;
        }
        double outcomeForA = "DRAW".equals(winnerResult) ? 0.5 : "A".equals(winnerResult) ? 1.0 : 0.0;
        leaderboardManager.recordRatedMatch(GAME_ID, playerA.getAccountId(), playerB.getAccountId(), outcomeForA);
    }

    private void sendResult(ClientHandler to, String winnerResult, String myWord, String opponentWord)
    {
        Message msg = new Message();
        msg.setType(MessageType.WORDDUEL_RESULT);
        msg.setMatchId(matchId);
        msg.setScore(myWord.length());
        msg.setTriviaQuestion(myWord.isEmpty() ? "(no word submitted)" : myWord);
        msg.setChatText(opponentWord.isEmpty() ? "(no word submitted)" : opponentWord);
        boolean toIsWinner = (to == playerA && "A".equals(winnerResult)) || (to == playerB && "B".equals(winnerResult));
        msg.setMatchResult("DRAW".equals(winnerResult) ? "DRAW" : (toIsWinner ? "WIN" : "LOSE"));
        to.sendMessage(msg);
    }

    public synchronized void handleDisconnect(ClientHandler who)
    {
        if (over) return;
        over = true;
        if (roundTimer != null) roundTimer.cancel();
        matchManager.endMatch(matchId);

        ClientHandler remaining = (who == playerA) ? playerB : playerA;
        Message msg = new Message();
        msg.setType(MessageType.WORDDUEL_RESULT);
        msg.setMatchId(matchId);
        msg.setMatchResult("OPPONENT_LEFT");
        remaining.sendMessage(msg);

        economyManager.awardWin(remaining, GAME_ID);
    }
}
