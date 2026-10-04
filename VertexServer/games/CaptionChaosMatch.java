package games;
import chat.GameChatPolicies;
import chat.MatchChatRoom;
import economy.EconomyManager;
import net.ClientHandler;
import net.Message;
import net.MessageType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

/**
 * CaptionChaosMatch
 * -----------------
 * A prompt-and-vote party game for 3-8 players (original; the genre is the Quiplash/Jackbox kind). Each of
 * ROUNDS rounds: the server shows a silly prompt, everyone privately writes an answer (WRITE_MS), then all the
 * answers are shown anonymously and everyone votes for their favourite - never their own (VOTE_MS) - then the
 * authors are revealed with their votes. 100 points per vote; the highest total after the last round wins.
 *
 * Request/response only, no real-time sync: the same "server broadcasts a phase, clients submit one discrete action,
 * a server-side Timer closes the phase whether or not everyone answered" shape as TriviaMatch and TelephoneMatch. A
 * phase also ends early once every connected player has acted, so a quick group isn't made to wait out the clock.
 *
 * Server-authoritative: a client can only submit text and a vote index; the server decides whose answer is whose,
 * rejects voting for your own answer, and does the counting and the awarding. Answers are anonymous on the wire
 * until the reveal - the options message carries only the texts (in a shuffled order) plus which one is yours.
 *
 * Chat is LOCKED until the match ends (GameChatPolicies) so authorship can't be discussed mid-vote.
 *
 * A player leaving never stalls anyone: their missing answer is simply not shown and their missing vote is not
 * counted. If fewer than two players remain the match ends early without awarding a win.
 */
public class CaptionChaosMatch
{
    public static final int ROUNDS = 3;
    public static final int MAX_ANSWER_LENGTH = 80;
    public static final long WRITE_MS = 45_000;
    public static final long VOTE_MS = 25_000;
    /** How long the round reveal stays up before the next round. Not final only so a test can shorten it (package-private). */
    static long resultMs = 8_000;
    private static final int POINTS_PER_VOTE = 100;
    private static final String GAME_ID = "caption-chaos";
    private static final long POST_MATCH_CHAT_MILLIS = 60_000;

    private enum Phase { WRITING, VOTING, RESULT, OVER }

    private final String matchId;
    private final List<ClientHandler> players;
    private final CaptionChaosMatchManager matchManager;
    private final EconomyManager economyManager;
    private final List<String> prompts = CaptionChaosPrompts.shuffled();

    private final Set<ClientHandler> disconnected = new HashSet<ClientHandler>();
    private final Map<ClientHandler, Integer> scores = new LinkedHashMap<ClientHandler, Integer>();
    private final Map<ClientHandler, String> answers = new HashMap<ClientHandler, String>();
    private final Map<ClientHandler, Integer> votes = new HashMap<ClientHandler, Integer>();
    /** This round's anonymous options: index -> author. Shuffled once per round, so every player sees the same order. */
    private final List<ClientHandler> optionAuthors = new ArrayList<ClientHandler>();

    private MatchChatRoom chatRoom;
    private Phase phase = Phase.OVER;
    private int round = 0;
    private Timer timer;

    public CaptionChaosMatch(String matchId, List<ClientHandler> players, CaptionChaosMatchManager matchManager,
                              EconomyManager economyManager)
    {
        this.matchId = matchId;
        this.players = players;
        this.matchManager = matchManager;
        this.economyManager = economyManager;
        for (ClientHandler player : players)
        {
            scores.put(player, 0);
        }
    }

    public synchronized void start()
    {
        chatRoom = GameChatPolicies.openRoom(matchId, GAME_ID, players);
        startRound();
    }

    // ==================== Writing ====================

    private void startRound()
    {
        round++;
        answers.clear();
        votes.clear();
        optionAuthors.clear();
        phase = Phase.WRITING;

        String prompt = prompts.get((round - 1) % prompts.size());
        for (ClientHandler player : connectedPlayers())
        {
            Message msg = new Message();
            msg.setType(MessageType.CAPTIONCHAOS_WRITE_START);
            msg.setMatchId(matchId);
            msg.setCaptionRound(round);
            msg.setCaptionTotalRounds(ROUNDS);
            msg.setCaptionPrompt(prompt);
            msg.setCaptionSeconds((int) (WRITE_MS / 1000));
            player.sendMessage(msg);
        }
        schedule(WRITE_MS, new Runnable()
        {
            final int forRound = round;
            public void run() { endWriting(forRound); }
        });
    }

    /** Ignored unless it is the writing phase and this player has not answered yet; empty text is ignored too (the client can resubmit). */
    public synchronized void submitAnswer(ClientHandler who, String text)
    {
        if (phase != Phase.WRITING || !players.contains(who) || disconnected.contains(who) || answers.containsKey(who))
        {
            return;
        }
        String clean = clean(text);
        if (clean.isEmpty())
        {
            return;
        }
        answers.put(who, clean);
        if (answers.size() >= connectedPlayers().size())
        {
            endWriting(round);
        }
    }

    private static String clean(String text)
    {
        if (text == null)
        {
            return "";
        }
        String t = text.replaceAll("[\\r\\n\\t]+", " ").trim();
        return t.length() > MAX_ANSWER_LENGTH ? t.substring(0, MAX_ANSWER_LENGTH) : t;
    }

    private synchronized void endWriting(int forRound)
    {
        if (phase != Phase.WRITING || forRound != round)
        {
            return;
        }
        cancelTimer();

        for (ClientHandler author : players)
        {
            if (answers.containsKey(author))
            {
                optionAuthors.add(author);
            }
        }
        if (optionAuthors.size() < 2)
        {
            // Nothing to vote between - show what there is and move on.
            finishVoting(round);
            return;
        }
        Collections.shuffle(optionAuthors);
        phase = Phase.VOTING;

        List<String> options = new ArrayList<String>();
        for (ClientHandler author : optionAuthors)
        {
            options.add(answers.get(author));
        }
        for (ClientHandler player : connectedPlayers())
        {
            Message msg = new Message();
            msg.setType(MessageType.CAPTIONCHAOS_VOTE_START);
            msg.setMatchId(matchId);
            msg.setCaptionRound(round);
            msg.setCaptionTotalRounds(ROUNDS);
            msg.setCaptionPrompt(prompts.get((round - 1) % prompts.size()));
            msg.setCaptionOptions(new ArrayList<String>(options));
            msg.setCaptionIndex(optionAuthors.indexOf(player));   // which option is yours (-1 = you did not answer)
            msg.setCaptionSeconds((int) (VOTE_MS / 1000));
            player.sendMessage(msg);
        }
        schedule(VOTE_MS, new Runnable()
        {
            final int forRound = round;
            public void run() { finishVoting(forRound); }
        });
    }

    // ==================== Voting ====================

    /** One vote per player per round, never for your own answer, only for a real option. */
    public synchronized void submitVote(ClientHandler who, int optionIndex)
    {
        if (phase != Phase.VOTING || !players.contains(who) || disconnected.contains(who) || votes.containsKey(who))
        {
            return;
        }
        if (optionIndex < 0 || optionIndex >= optionAuthors.size() || optionAuthors.get(optionIndex) == who)
        {
            return;
        }
        votes.put(who, optionIndex);
        if (votes.size() >= connectedPlayers().size())
        {
            finishVoting(round);
        }
    }

    private synchronized void finishVoting(int forRound)
    {
        if ((phase != Phase.VOTING && phase != Phase.WRITING) || forRound != round)
        {
            return;
        }
        cancelTimer();
        phase = Phase.RESULT;

        int[] counts = new int[optionAuthors.size()];
        for (Integer index : votes.values())
        {
            counts[index]++;
        }
        List<String> results = new ArrayList<String>();   // "author|text|votes", most votes first
        List<Integer> order = new ArrayList<Integer>();
        for (int i = 0; i < optionAuthors.size(); i++)
        {
            order.add(i);
            ClientHandler author = optionAuthors.get(i);
            scores.put(author, scores.get(author) + counts[i] * POINTS_PER_VOTE);
        }
        final int[] c = counts;
        Collections.sort(order, new java.util.Comparator<Integer>()
        {
            public int compare(Integer a, Integer b) { return c[b] - c[a]; }
        });
        for (int i : order)
        {
            ClientHandler author = optionAuthors.get(i);
            results.add(name(author) + "|" + answers.get(author) + "|" + counts[i]);
        }

        List<String> scoreLines = scoreLines();
        for (ClientHandler player : connectedPlayers())
        {
            Message msg = new Message();
            msg.setType(MessageType.CAPTIONCHAOS_ROUND_RESULT);
            msg.setMatchId(matchId);
            msg.setCaptionRound(round);
            msg.setCaptionTotalRounds(ROUNDS);
            msg.setCaptionResults(new ArrayList<String>(results));
            msg.setCaptionScores(new ArrayList<String>(scoreLines));
            player.sendMessage(msg);
        }

        if (connectedPlayers().size() < 2)
        {
            finishMatch(true);
            return;
        }
        final int justFinished = round;
        schedule(resultMs, new Runnable()
        {
            public void run() { afterResult(justFinished); }
        });
    }

    private synchronized void afterResult(int forRound)
    {
        if (phase != Phase.RESULT || forRound != round)
        {
            return;
        }
        if (connectedPlayers().size() < 2)
        {
            finishMatch(true);
        }
        else if (round >= ROUNDS)
        {
            finishMatch(false);
        }
        else
        {
            startRound();
        }
    }

    // ==================== End ====================

    private void finishMatch(boolean aborted)
    {
        if (phase == Phase.OVER)
        {
            return;
        }
        phase = Phase.OVER;
        cancelTimer();

        int best = 0;
        for (int score : scores.values()) best = Math.max(best, score);
        List<String> winners = new ArrayList<String>();
        for (Map.Entry<ClientHandler, Integer> e : scores.entrySet())
        {
            if (best > 0 && e.getValue() == best) winners.add(name(e.getKey()));
        }

        List<String> scoreLines = scoreLines();
        for (ClientHandler player : connectedPlayers())
        {
            Message msg = new Message();
            msg.setType(MessageType.CAPTIONCHAOS_MATCH_OVER);
            msg.setMatchId(matchId);
            msg.setCaptionScores(new ArrayList<String>(scoreLines));
            msg.setCaptionWinners(new ArrayList<String>(winners));
            msg.setCaptionAborted(aborted);
            player.sendMessage(msg);
        }

        if (!aborted)
        {
            for (Map.Entry<ClientHandler, Integer> e : scores.entrySet())
            {
                if (best > 0 && e.getValue() == best && !disconnected.contains(e.getKey()))
                {
                    economyManager.awardWin(e.getKey(), GAME_ID);
                }
            }
        }

        if (chatRoom != null)
        {
            chatRoom.unlock();
            chatRoom.closeAfter(POST_MATCH_CHAT_MILLIS);
        }
        matchManager.endMatch(matchId);
    }

    /** A player closing the game or losing connection: they stop being counted, the others carry on. */
    public synchronized void handleDisconnect(ClientHandler who)
    {
        if (phase == Phase.OVER || !players.contains(who) || !disconnected.add(who))
        {
            return;
        }
        List<ClientHandler> left = connectedPlayers();
        if (left.size() < 2)
        {
            if (phase == Phase.VOTING || phase == Phase.WRITING)
            {
                cancelTimer();
            }
            if (phase != Phase.RESULT)
            {
                finishMatch(true);
            }
            return;
        }
        // Everyone still here may already have acted - don't leave them waiting on the one who left.
        if (phase == Phase.WRITING && answers.size() >= left.size())
        {
            endWriting(round);
        }
        else if (phase == Phase.VOTING && votes.size() >= left.size())
        {
            finishVoting(round);
        }
    }

    // ==================== helpers ====================

    private List<ClientHandler> connectedPlayers()
    {
        List<ClientHandler> list = new ArrayList<ClientHandler>();
        for (ClientHandler p : players)
        {
            if (!disconnected.contains(p)) list.add(p);
        }
        return list;
    }

    private static String name(ClientHandler player)
    {
        String n = player.getLoggedInUsername();
        return n != null ? n : "?";
    }

    /** "name:score", best first. */
    private List<String> scoreLines()
    {
        List<ClientHandler> sorted = new ArrayList<ClientHandler>(players);
        Collections.sort(sorted, new java.util.Comparator<ClientHandler>()
        {
            public int compare(ClientHandler a, ClientHandler b) { return scores.get(b) - scores.get(a); }
        });
        List<String> lines = new ArrayList<String>();
        for (ClientHandler p : sorted)
        {
            lines.add(name(p) + ":" + scores.get(p));
        }
        return lines;
    }

    private void schedule(long delayMs, final Runnable task)
    {
        cancelTimer();
        timer = new Timer(true);
        timer.schedule(new TimerTask()
        {
            public void run() { task.run(); }
        }, delayMs);
    }

    private void cancelTimer()
    {
        if (timer != null)
        {
            timer.cancel();
            timer = null;
        }
    }
}
