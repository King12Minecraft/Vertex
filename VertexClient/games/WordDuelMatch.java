package games;
import mechanics.ReconnectPolicy;
import mechanics.ReconnectRegistry;

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
    private ClientHandler playerA;
    private ClientHandler playerB;
    private final WordDuelMatchManager matchManager;
    private final EconomyManager economyManager;
    private final LeaderboardManager leaderboardManager;
    private final ReconnectRegistry reconnectRegistry;

    private final String letters;
    private String bestWordA = "";
    private String bestWordB = "";
    private boolean over = false;
    private Timer roundTimer;

    /** null when nobody is in a reconnect grace period; true/false for whichever of playerA/playerB just dropped - same pattern as TicTacToeMatch's char disconnectedSlot, a Boolean here since this game already tracks its two players as named fields rather than a list. Deliberately does NOT pause roundTimer during a grace period - the round's real end time is still server-authoritative and unaffected by a socket dropping, same as it always was; a reconnecting player's local countdown may briefly show more time than actually remains (a known, cosmetic-only V1 gap, not a correctness one). */
    private Boolean disconnectedIsA = null;

    public WordDuelMatch(String matchId, ClientHandler playerA, ClientHandler playerB,
                          WordDuelMatchManager matchManager, EconomyManager economyManager,
                          LeaderboardManager leaderboardManager, ReconnectRegistry reconnectRegistry)
    {
        this.matchId = matchId;
        this.playerA = playerA;
        this.playerB = playerB;
        this.matchManager = matchManager;
        this.economyManager = economyManager;
        this.leaderboardManager = leaderboardManager;
        this.reconnectRegistry = reconnectRegistry;
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

    /**
     * Only reveals each player's current best LENGTH to the other side while the
     * round is live, not the word itself - actual words are only revealed once the
     * round ends, keeping some suspense. Sends each recipient their OWN
     * "mine:opponent" tuple rather than one shared "A:B" string for both - the
     * previous shared-tuple version always put bestWordB's length at index 1 for
     * BOTH recipients, which WordDuelWindow reads unconditionally as "opponent's
     * best": correct for player A, but for player B it silently mislabeled their
     * own progress as the opponent's. A real pre-existing bug, not a hypothetical
     * one - fixed here rather than left in place while adding reconnect support to
     * this same method's disconnect-handling neighbors below.
     */
    private void broadcastProgress()
    {
        sendProgress(playerA, bestWordA, bestWordB);
        sendProgress(playerB, bestWordB, bestWordA);
    }

    private void sendProgress(ClientHandler to, String myWord, String opponentWord)
    {
        Message msg = new Message();
        msg.setType(MessageType.WORDDUEL_UPDATE);
        msg.setMatchId(matchId);
        msg.setTriviaScores(java.util.Arrays.asList(myWord.length() + ":" + opponentWord.length()));
        to.sendMessage(msg);
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

    public void handleDisconnect(ClientHandler who)
    {
        Integer accountIdToRegister = null;
        ClientHandler remainingForNotice = null;
        boolean bothNowGone = false;

        synchronized (this)
        {
            if (over) return;
            boolean isA = who == playerA;
            boolean isB = who == playerB;
            if (!isA && !isB) return;

            if (disconnectedIsA != null)
            {
                // The other player was already in a grace period and has now ALSO
                // disconnected - nobody left to wait for or to notify.
                over = true;
                bothNowGone = true;
            }
            else if (!ReconnectPolicy.canReconnect(who, GAME_ID))
            {
                // Guests never get a grace period - no stable identity to reconnect
                // against, so a guest disconnect finalizes immediately exactly as
                // every disconnect here did before reconnect support existed.
                over = true;
                if (roundTimer != null) roundTimer.cancel();
                matchManager.endMatch(matchId);
                ClientHandler remaining = isA ? playerB : playerA;
                sendAbandonedResult(remaining);
                economyManager.awardWin(remaining, GAME_ID);
                return;
            }
            else
            {
                disconnectedIsA = isA;
                remainingForNotice = isA ? playerB : playerA;
                accountIdToRegister = who.getAccountId();

                Message notice = new Message();
                notice.setType(MessageType.OPPONENT_DISCONNECTED_NOTICE);
                notice.setMatchId(matchId);
                notice.setErrorText(ReconnectPolicy.waitingNotice());
                remainingForNotice.sendMessage(notice);
            }
        }

        if (bothNowGone)
        {
            if (roundTimer != null) roundTimer.cancel();
            matchManager.endMatch(matchId);
            return;
        }

        // See ReconnectRegistry's class-level threading note - never call this while
        // holding this match's own lock.
        reconnectRegistry.beginGracePeriod(accountIdToRegister, new ReconnectRegistry.ReconnectableMatch()
        {
            public void onReconnectTimeout()
            {
                WordDuelMatch.this.onReconnectTimeout();
            }

            public ReconnectRegistry.ReconnectResult onReconnect(ClientHandler newHandler)
            {
                return WordDuelMatch.this.onReconnect(newHandler);
            }

            public void attachToHandler(ClientHandler handler)
            {
                handler.setCurrentWordDuelMatch(WordDuelMatch.this);
            }
        });
    }

    private void sendAbandonedResult(ClientHandler remaining)
    {
        Message msg = new Message();
        msg.setType(MessageType.WORDDUEL_RESULT);
        msg.setMatchId(matchId);
        msg.setMatchResult("OPPONENT_LEFT");
        remaining.sendMessage(msg);
    }

    private synchronized void onReconnectTimeout()
    {
        if (over) return;
        over = true;
        if (roundTimer != null) roundTimer.cancel();
        ClientHandler remaining = disconnectedIsA ? playerB : playerA;
        disconnectedIsA = null;
        matchManager.endMatch(matchId);
        sendAbandonedResult(remaining);
        economyManager.awardWin(remaining, GAME_ID);
    }

    /**
     * Called by ReconnectRegistry.tryReconnect() while it holds the registry's own
     * lock - must never call back into the registry from here. Returns null (no
     * resume) if the round already concluded normally via roundTimer while this
     * player was disconnected - a narrow timing edge case unique to this game among
     * today's reconnect-aware matches (the others are purely turn-based, with no
     * independent clock that can end a match while a grace period is pending): if
     * a player disconnects with less time left in the round than the reconnect grace
     * window allows, the round can finish() before they reconnect, and they won't
     * be retroactively shown the result. Not a regression - the exact same "a
     * disconnected player never learns the outcome" limitation already existed for
     * every disconnect before this method existed at all, just narrowed from
     * "always" to "only in this specific timing window" by adding reconnect
     * support at all.
     */
    private synchronized ReconnectRegistry.ReconnectResult onReconnect(ClientHandler newHandler)
    {
        if (over || disconnectedIsA == null)
        {
            return null;
        }

        boolean reconnectedIsA = disconnectedIsA;
        if (reconnectedIsA) { playerA = newHandler; } else { playerB = newHandler; }
        disconnectedIsA = null;

        ClientHandler opponent = reconnectedIsA ? playerB : playerA;
        String myWord = reconnectedIsA ? bestWordA : bestWordB;
        String opponentWord = reconnectedIsA ? bestWordB : bestWordA;

        // Nudges the still-connected opponent's UI out of its "waiting to reconnect"
        // state - no dedicated "resumed" message type exists; an ordinary progress
        // update does it, same as every other reconnect-aware game's plain *_UPDATE
        // (see TicTacToeWindow's OPPONENT_DISCONNECTED_NOTICE handling for why
        // that's enough).
        sendProgress(opponent, opponentWord, myWord);

        // ReconnectResult has no field of its own for "the letters" or "my current
        // progress" - every other reconnect-aware game today is a flat-string-board
        // game that fits boardState/turnSymbol directly. Word Duel doesn't have a
        // board, so it repurposes those two generic slots for its own single-string
        // state instead: boardState carries the (unchanging, match-long) letters,
        // turnSymbol carries this player's own "mine:opponent" length tuple. See
        // AuthWindow.resumeMatchIfPending()'s "word-duel" branch for where these get
        // unpacked back into WORDDUEL_MATCH_FOUND/WORDDUEL_UPDATE's real fields.
        String myProgress = myWord.length() + ":" + opponentWord.length();
        return new ReconnectRegistry.ReconnectResult(matchId, GAME_ID, "", opponent.getLoggedInUsername(), letters, myProgress);
    }
}
