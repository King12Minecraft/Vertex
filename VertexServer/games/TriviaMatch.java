package games;
import economy.EconomyConfig;
import net.MessageType;
import net.Message;
import economy.LeaderboardManager;
import economy.EconomyManager;
import net.ClientHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * TriviaMatch
 * -----------
 * Round-based quiz, 2-6 players, an original question bank (basic
 * general-knowledge facts - math, geography, science - written for
 * this project, not copied from any trivia product). Everyone gets
 * the same question at the same time and answers independently
 * within the time limit; correct answers score points, with a speed
 * bonus for answering faster. Same "server broadcasts state, clients
 * submit discrete actions" shape every other real-time game on this
 * platform uses - a round auto-advances via a server-side Timer once
 * ROUND_DURATION_MS elapses, regardless of who's answered, so one
 * slow/disconnected player can't stall the whole match.
 */
public class TriviaMatch
{
    public static final int ROUND_COUNT = 8;
    public static final long ROUND_DURATION_MS = 12_000;
    private static final int BASE_POINTS = 100;
    private static final int MAX_SPEED_BONUS = 50;
    private static final String GAME_ID = "trivia-blitz";

    private static class Question
    {
        final String text;
        final String[] options;
        final int correctIndex;

        Question(String text, int correctIndex, String... options)
        {
            this.text = text;
            this.correctIndex = correctIndex;
            this.options = options;
        }
    }

    /** An original, self-written set of basic general-knowledge questions - not sourced from any trivia game, book, or quiz bank. Deliberately simple/common-knowledge facts (arithmetic, well-known geography, basic science) to avoid anything resembling copyrighted trivia content. */
    private static final List<Question> BANK = new ArrayList<Question>();
    static
    {
        BANK.add(new Question("What is 7 x 8?", 2, "54", "58", "56", "64"));
        BANK.add(new Question("How many continents are there on Earth?", 1, "5", "7", "6", "8"));
        BANK.add(new Question("What is the largest planet in our solar system?", 2, "Earth", "Saturn", "Jupiter", "Neptune"));
        BANK.add(new Question("How many sides does a hexagon have?", 1, "5", "6", "7", "8"));
        BANK.add(new Question("What gas do plants absorb from the air to grow?", 0, "Carbon Dioxide", "Oxygen", "Nitrogen", "Hydrogen"));
        BANK.add(new Question("What is the capital of France?", 3, "Berlin", "Madrid", "Rome", "Paris"));
        BANK.add(new Question("How many minutes are in two hours?", 2, "100", "110", "120", "140"));
        BANK.add(new Question("What is the smallest prime number?", 1, "0", "2", "1", "3"));
        BANK.add(new Question("Which ocean is the largest?", 2, "Atlantic", "Indian", "Pacific", "Arctic"));
        BANK.add(new Question("How many legs does a spider have?", 2, "6", "10", "8", "12"));
        BANK.add(new Question("What is the freezing point of water in Celsius?", 0, "0", "32", "-1", "100"));
        BANK.add(new Question("How many players are on a standard soccer team on the field?", 2, "9", "10", "11", "12"));
        BANK.add(new Question("What is the square root of 81?", 1, "8", "9", "7", "11"));
        BANK.add(new Question("Which planet is known as the Red Planet?", 1, "Venus", "Mars", "Mercury", "Jupiter"));
        BANK.add(new Question("How many letters are in the English alphabet?", 2, "24", "25", "26", "27"));
        BANK.add(new Question("What do bees produce?", 1, "Silk", "Honey", "Wax only", "Pollen only"));
        BANK.add(new Question("How many days are in a leap year?", 1, "364", "366", "365", "367"));
        BANK.add(new Question("What is the tallest animal in the world?", 2, "Elephant", "Horse", "Giraffe", "Camel"));
        BANK.add(new Question("What is 15 x 4?", 0, "60", "45", "50", "65"));
        BANK.add(new Question("Which is the smallest continent by land area?", 2, "Europe", "South America", "Australia", "Antarctica"));
    }

    private final String matchId;
    private final List<ClientHandler> players;
    private final TriviaMatchManager matchManager;
    private final EconomyManager economyManager;
    private final LeaderboardManager leaderboardManager;

    private final List<Question> matchQuestions = new ArrayList<Question>();
    private final Map<ClientHandler, Integer> scores = new HashMap<ClientHandler, Integer>();
    private final Map<ClientHandler, Integer> roundAnswers = new HashMap<ClientHandler, Integer>();
    private final Map<ClientHandler, Long> roundAnswerTimes = new HashMap<ClientHandler, Long>();

    private int currentRound = 0;
    private long roundStartedAt;
    private boolean over = false;
    private Timer roundTimer;

    public TriviaMatch(String matchId, List<ClientHandler> players, TriviaMatchManager matchManager,
                        EconomyManager economyManager, LeaderboardManager leaderboardManager)
    {
        this.matchId = matchId;
        this.players = players;
        this.matchManager = matchManager;
        this.economyManager = economyManager;
        this.leaderboardManager = leaderboardManager;

        List<Question> shuffled = new ArrayList<Question>(BANK);
        Collections.shuffle(shuffled);
        int count = Math.min(ROUND_COUNT, shuffled.size());
        for (int i = 0; i < count; i++)
        {
            matchQuestions.add(shuffled.get(i));
        }
        for (int i = 0; i < players.size(); i++)
        {
            scores.put(players.get(i), 0);
        }
    }

    public void start()
    {
        startRound();
    }

    private void startRound()
    {
        roundAnswers.clear();
        roundAnswerTimes.clear();
        roundStartedAt = System.currentTimeMillis();

        Question q = matchQuestions.get(currentRound);
        for (int i = 0; i < players.size(); i++)
        {
            Message msg = new Message();
            msg.setType(MessageType.TRIVIA_ROUND_START);
            msg.setMatchId(matchId);
            msg.setTriviaQuestion(q.text);
            msg.setTriviaOptions(java.util.Arrays.asList(q.options));
            msg.setTriviaRound(currentRound + 1);
            msg.setTriviaTotalRounds(matchQuestions.size());
            players.get(i).sendMessage(msg);
        }

        roundTimer = new Timer(true);
        final int roundAtSchedule = currentRound;
        roundTimer.schedule(new TimerTask()
        {
            public void run() { finishRound(roundAtSchedule); }
        }, ROUND_DURATION_MS);
    }

    public synchronized void submitAnswer(ClientHandler requester, int optionIndex)
    {
        if (over || !players.contains(requester) || roundAnswers.containsKey(requester))
        {
            return;
        }
        roundAnswers.put(requester, optionIndex);
        roundAnswerTimes.put(requester, System.currentTimeMillis());
    }

    private synchronized void finishRound(int roundThatEnded)
    {
        if (over || roundThatEnded != currentRound)
        {
            return;
        }

        Question q = matchQuestions.get(currentRound);
        for (int i = 0; i < players.size(); i++)
        {
            ClientHandler player = players.get(i);
            Integer answer = roundAnswers.get(player);
            int roundScore = 0;
            if (answer != null && answer == q.correctIndex)
            {
                long elapsed = roundAnswerTimes.get(player) - roundStartedAt;
                double fraction = Math.max(0, 1.0 - (elapsed / (double) ROUND_DURATION_MS));
                roundScore = BASE_POINTS + (int) (MAX_SPEED_BONUS * fraction);
            }
            scores.put(player, scores.get(player) + roundScore);
        }

        List<String> scoreLines = buildScoreLines();
        for (int i = 0; i < players.size(); i++)
        {
            Message msg = new Message();
            msg.setType(MessageType.TRIVIA_ROUND_RESULT);
            msg.setMatchId(matchId);
            msg.setTriviaCorrectIndex(q.correctIndex);
            msg.setTriviaRound(currentRound + 1);
            msg.setTriviaScores(scoreLines);
            players.get(i).sendMessage(msg);
        }

        currentRound++;
        if (currentRound >= matchQuestions.size())
        {
            finishMatch();
        }
        else
        {
            // Brief pause so players can see the correct answer/scores before the next
            // question appears - same idea as RPS's own round-reveal pacing.
            Timer delay = new Timer(true);
            delay.schedule(new TimerTask()
            {
                public void run() { startRound(); }
            }, 3000);
        }
    }

    private List<String> buildScoreLines()
    {
        List<String> lines = new ArrayList<String>();
        for (int i = 0; i < players.size(); i++)
        {
            ClientHandler p = players.get(i);
            String name = p.getLoggedInUsername();
            lines.add((name != null ? name : "?") + ":" + scores.get(p));
        }
        return lines;
    }

    private void finishMatch()
    {
        over = true;
        matchManager.endMatch(matchId);

        int maxScore = 0;
        for (int score : scores.values()) maxScore = Math.max(maxScore, score);

        List<ClientHandler> winners = new ArrayList<ClientHandler>();
        for (int i = 0; i < players.size(); i++)
        {
            if (scores.get(players.get(i)) == maxScore) winners.add(players.get(i));
        }

        int totalReward = EconomyConfig.getWinReward(GAME_ID);
        int perWinnerReward = winners.isEmpty() ? 0 : totalReward / winners.size();
        List<String> scoreLines = buildScoreLines();

        for (int i = 0; i < players.size(); i++)
        {
            ClientHandler player = players.get(i);
            boolean isWinner = winners.contains(player);
            int reward = 0;
            if (isWinner && perWinnerReward > 0)
            {
                economyManager.awardCoins(player, perWinnerReward, "Won a Trivia Blitz match");
                reward = perWinnerReward;
            }
            if (player.getAccountId() != null)
            {
                leaderboardManager.recordScore(GAME_ID, player.getAccountId(), scores.get(player));
            }

            Message msg = new Message();
            msg.setType(MessageType.TRIVIA_RESULT);
            msg.setMatchId(matchId);
            msg.setTriviaScores(scoreLines);
            msg.setMatchResult(isWinner ? "WIN" : "LOSE");
            msg.setTriviaReward(reward);
            player.sendMessage(msg);
        }
    }

    public synchronized void handleDisconnect(ClientHandler who)
    {
        // Their score stays locked in at whatever they'd earned so far - the match keeps
        // running for whoever's left; finishRound()'s own timer still fires regardless.
    }
}
