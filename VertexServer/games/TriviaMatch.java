package games;
import ai.knowledge.CachingFactLookup;
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
 * Round-based quiz, 2-6 players, mixing an original static question
 * bank (basic general-knowledge facts - math, geography, science -
 * written for this project, not copied from any trivia product) with
 * several LIVE-resolved question categories (see the ai.knowledge
 * package): country capitals (restcountries.com), and companies'
 * founding years, historical events' years, inventions' credited
 * inventor, and cities' country (all via Wikidata's free SPARQL
 * endpoint - see WikidataFactSource). Every category is cache-first,
 * falling back to a real free web lookup on a miss, so the question
 * pool grows on its own over time instead of staying fixed at
 * whatever was hand-written, and every lookup source is free/keyless
 * with no meaningful rate limit at this volume - a deliberate
 * constraint (no paid API, nothing to run out of). A live lookup that
 * fails just means this match uses one more bank question instead -
 * it never stalls a match waiting on the network (see
 * resolveLiveQuestions()). Everyone gets the same question at the
 * same time and answers independently within the time limit; correct
 * answers score points, with a speed bonus for answering faster. Same
 * "server broadcasts state, clients submit discrete actions" shape
 * every other real-time game on this platform uses - a round
 * auto-advances via a server-side Timer once ROUND_DURATION_MS
 * elapses, regardless of who's answered, so one slow/disconnected
 * player can't stall the whole match.
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

    /**
     * Countries deliberately NOT in the static BANK above, whose
     * capitals are resolved live via TriviaLiveLookups.capital (cache
     * first, then a real web lookup on a miss). A country that fails
     * to resolve is simply skipped for that match - see buildAllLiveTasks().
     */
    private static final String[] LIVE_CAPITAL_COUNTRIES = {
        "Kazakhstan", "Mongolia", "Uruguay", "Bhutan", "Eritrea",
        "Suriname", "Laos", "Moldova"
    };

    /** Real capitals used as multiple-choice distractors for a live-resolved capital question - never the static BANK's own answers, kept thematically consistent (all real national capitals). */
    private static final String[] DISTRACTOR_CAPITALS = {
        "Astana", "Ulaanbaatar", "Montevideo", "Thimphu", "Asmara", "Paramaribo",
        "Vientiane", "Chisinau", "Reykjavik", "Ljubljana", "Bratislava", "Vilnius",
        "Riga", "Tallinn", "Minsk", "Yerevan", "Baku", "Tbilisi", "Bishkek",
        "Dushanbe", "Ashgabat", "Tashkent"
    };

    /** Real inventors/scientists used as distractors for a live-resolved "who invented X" question. */
    private static final String[] DISTRACTOR_PEOPLE = {
        "Alexander Graham Bell", "Thomas Edison", "Nikola Tesla", "Alessandro Volta",
        "Guglielmo Marconi", "the Wright brothers", "Alexander Fleming", "Tim Berners-Lee",
        "James Watt", "Michael Faraday", "Marie Curie", "Louis Pasteur"
    };

    /** Real country names used as distractors for a live-resolved "which country is city X in" question. */
    private static final String[] DISTRACTOR_COUNTRIES = {
        "Japan", "Morocco", "Canada", "Switzerland", "Peru", "Croatia", "Brazil", "Egypt",
        "Thailand", "Portugal", "Greece", "Kenya", "Vietnam", "Chile", "Norway"
    };

    private static final int LIVE_QUESTIONS_PER_MATCH = 4;

    /** Which "shape" of answer a LiveTask expects - determines whether buildYearQuestion() or buildNameQuestion() renders it. */
    private enum AnswerShape { YEAR, NAME }

    /**
     * One live-sourceable trivia question, not yet resolved: which
     * lookup answers it, the exact subject text to send that lookup
     * (must match the live source's expected label - e.g. Wikidata's
     * own rdfs:label text, for anything routed through a
     * WikidataFactSource), the fully-composed human-readable question
     * text, and enough to build distractors once the real answer comes
     * back. questionText and subject are kept separate (rather than
     * deriving one from the other) so the question can read naturally
     * ("Who is credited with inventing the telephone?") even when the
     * lookup subject itself can't include an article ("telephone",
     * not "the telephone" - Wikidata's label has no "the").
     */
    private static class LiveTask
    {
        final String subject;
        final CachingFactLookup lookup;
        final String questionText;
        final AnswerShape shape;
        final String[] distractorPool; // null for YEAR shape - see buildYearQuestion()

        LiveTask(String subject, CachingFactLookup lookup, String questionText,
                 AnswerShape shape, String[] distractorPool)
        {
            this.subject = subject;
            this.lookup = lookup;
            this.questionText = questionText;
            this.shape = shape;
            this.distractorPool = distractorPool;
        }
    }

    private final String matchId;
    private final List<ClientHandler> players;
    private final TriviaMatchManager matchManager;
    private final EconomyManager economyManager;
    private final LeaderboardManager leaderboardManager;
    private final TriviaLiveLookups liveLookups;

    private final List<Question> matchQuestions = new ArrayList<Question>();
    private final Map<ClientHandler, Integer> scores = new HashMap<ClientHandler, Integer>();
    private final Map<ClientHandler, Integer> roundAnswers = new HashMap<ClientHandler, Integer>();
    private final Map<ClientHandler, Long> roundAnswerTimes = new HashMap<ClientHandler, Long>();

    private int currentRound = 0;
    private long roundStartedAt;
    private boolean over = false;
    private Timer roundTimer;

    public TriviaMatch(String matchId, List<ClientHandler> players, TriviaMatchManager matchManager,
                        EconomyManager economyManager, LeaderboardManager leaderboardManager,
                        TriviaLiveLookups liveLookups)
    {
        this.matchId = matchId;
        this.players = players;
        this.matchManager = matchManager;
        this.economyManager = economyManager;
        this.leaderboardManager = leaderboardManager;
        this.liveLookups = liveLookups;

        // Live-resolved questions first (they're the ones that can fail/skip), THEN top up
        // with however many static-bank questions are needed to fill the round - so a live
        // lookup failure just means slightly more bank questions this match, never a short round.
        matchQuestions.addAll(resolveLiveQuestions(LIVE_QUESTIONS_PER_MATCH));

        List<Question> shuffled = new ArrayList<Question>(BANK);
        Collections.shuffle(shuffled);
        int bankNeeded = Math.min(ROUND_COUNT - matchQuestions.size(), shuffled.size());
        for (int i = 0; i < bankNeeded; i++)
        {
            matchQuestions.add(shuffled.get(i));
        }
        Collections.shuffle(matchQuestions); // don't always put live questions first/last

        for (int i = 0; i < players.size(); i++)
        {
            scores.put(players.get(i), 0);
        }
    }

    /**
     * Every live-sourceable question across every category, not yet
     * resolved - built fresh per match (cheap: it's just data, no
     * network calls happen here) so resolveLiveQuestions() can shuffle
     * the FULL cross-category pool together, rather than picking a
     * fixed number per category. Adding a new category later is just
     * adding more entries here - no other method needs to change.
     */
    private List<LiveTask> buildAllLiveTasks()
    {
        List<LiveTask> tasks = new ArrayList<LiveTask>();

        for (String country : LIVE_CAPITAL_COUNTRIES)
        {
            tasks.add(new LiveTask(country, liveLookups.capital,
                "What is the capital of " + country + "?", AnswerShape.NAME, DISTRACTOR_CAPITALS));
        }

        // Companies - founding year (Wikidata property P571, inception).
        String[] companies = { "Apple Inc.", "Sony", "Nintendo", "Ford Motor Company", "Samsung", "Nike, Inc." };
        for (String company : companies)
        {
            tasks.add(new LiveTask(company, liveLookups.companyFoundingYear,
                "In what year was " + company + " founded?", AnswerShape.YEAR, null));
        }

        // Historical events - year (Wikidata property P585, point in time).
        String[] events = {
            "Sinking of the Titanic", "Chernobyl disaster", "Fall of the Berlin Wall",
            "Attack on Pearl Harbor", "Assassination of Abraham Lincoln"
        };
        for (String event : events)
        {
            tasks.add(new LiveTask(event, liveLookups.historicalEventYear,
                "In what year did the " + event + " occur?", AnswerShape.YEAR, null));
        }

        // Inventions/discoveries - who's credited (Wikidata property P61, discoverer or inventor).
        tasks.add(new LiveTask("telephone", liveLookups.inventor,
            "Who is credited with inventing the telephone?", AnswerShape.NAME, DISTRACTOR_PEOPLE));
        tasks.add(new LiveTask("light bulb", liveLookups.inventor,
            "Who is credited with inventing the light bulb?", AnswerShape.NAME, DISTRACTOR_PEOPLE));
        tasks.add(new LiveTask("World Wide Web", liveLookups.inventor,
            "Who is credited with inventing the World Wide Web?", AnswerShape.NAME, DISTRACTOR_PEOPLE));
        tasks.add(new LiveTask("penicillin", liveLookups.inventor,
            "Who is credited with discovering penicillin?", AnswerShape.NAME, DISTRACTOR_PEOPLE));
        tasks.add(new LiveTask("radio", liveLookups.inventor,
            "Who is credited with inventing the radio?", AnswerShape.NAME, DISTRACTOR_PEOPLE));

        // Cities - which country (Wikidata property P17, country).
        String[] cities = { "Kyoto", "Marrakesh", "Vancouver", "Zurich", "Cusco", "Dubrovnik" };
        for (String city : cities)
        {
            tasks.add(new LiveTask(city, liveLookups.cityCountry,
                "Which country is the city of " + city + " located in?", AnswerShape.NAME, DISTRACTOR_COUNTRIES));
        }

        return tasks;
    }

    /** Tries live tasks from the FULL cross-category pool (shuffled) until maxCount succeed or the pool is exhausted, skipping any that fail to resolve (cache-first, one live retry on a miss - see CachingFactLookup). Never throws and never blocks longer than a few lookups' worth of network timeouts. */
    private List<Question> resolveLiveQuestions(int maxCount)
    {
        List<Question> resolved = new ArrayList<Question>();
        List<LiveTask> tasks = buildAllLiveTasks();
        Collections.shuffle(tasks);

        for (LiveTask task : tasks)
        {
            if (resolved.size() >= maxCount)
            {
                break;
            }

            String result = task.lookup.lookup(task.subject);
            if (result == null)
            {
                continue; // cache miss + live lookup failed (or retried and still failed) - skip this one
            }

            if (task.shape == AnswerShape.YEAR)
            {
                Integer year = parseYear(result);
                if (year == null)
                {
                    continue; // unexpected/malformed response shape - skip rather than risk a bad question
                }
                resolved.add(buildYearQuestion(task.questionText, year));
            }
            else
            {
                resolved.add(buildNameQuestion(task.questionText, result, task.distractorPool));
            }
        }
        return resolved;
    }

    private Integer parseYear(String raw)
    {
        try
        {
            return Integer.valueOf(raw.trim());
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    /**
     * Builds a "what year" style question - the correct year plus
     * synthetic distractor years generated as offsets from it, rather
     * than a curated pool. Any resolved year automatically gets
     * plausible-looking wrong answers this way, which is what makes
     * this ONE method reusable across every year-shaped category
     * (companies' founding year, historical events' year, and any
     * future one) without a distractor list per subject.
     */
    private Question buildYearQuestion(String questionText, int correctYear)
    {
        int[] offsets = { -50, -30, -15, -8, 8, 15, 30, 50 };
        List<Integer> shuffledOffsets = new ArrayList<Integer>();
        for (int offset : offsets)
        {
            shuffledOffsets.add(offset);
        }
        Collections.shuffle(shuffledOffsets);

        int currentYear = java.time.Year.now().getValue();
        List<Integer> distractorYears = new ArrayList<Integer>();
        for (int offset : shuffledOffsets)
        {
            if (distractorYears.size() >= 3)
            {
                break;
            }
            int candidate = correctYear + offset;
            if (candidate != correctYear && candidate > 1000 && candidate <= currentYear
                && !distractorYears.contains(candidate))
            {
                distractorYears.add(candidate);
            }
        }

        List<String> options = new ArrayList<String>();
        options.add(String.valueOf(correctYear));
        for (int year : distractorYears)
        {
            options.add(String.valueOf(year));
        }
        Collections.shuffle(options);

        int correctIndex = options.indexOf(String.valueOf(correctYear));
        return new Question(questionText, correctIndex, options.toArray(new String[0]));
    }

    /**
     * Builds a question whose answer is a name/label (a capital, a
     * country, a person, ...) - the correct answer plus up to 3
     * distractors drawn from the category's own distractorPool
     * (excluding the correct answer itself), all shuffled into a
     * random position. Reused by every name-shaped category.
     */
    private Question buildNameQuestion(String questionText, String correctAnswer, String[] distractorPool)
    {
        List<String> pool = new ArrayList<String>();
        for (String candidate : distractorPool)
        {
            if (!candidate.equalsIgnoreCase(correctAnswer))
            {
                pool.add(candidate);
            }
        }
        Collections.shuffle(pool);

        List<String> options = new ArrayList<String>();
        options.add(correctAnswer);
        for (int i = 0; i < 3 && i < pool.size(); i++)
        {
            options.add(pool.get(i));
        }
        Collections.shuffle(options);

        int correctIndex = options.indexOf(correctAnswer);
        return new Question(questionText, correctIndex, options.toArray(new String[0]));
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
