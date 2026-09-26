package net;
import admin.AdminLog;
import games.SpaceBattleMatchManager;
import games.ZombieSurvivalMatchManager;
import admin.GameSuggestionStore;
import games.TeamTournamentManager;
import games.TournamentManager;
import economy.AvatarStore;
import games.TriviaMatchManager;
import ai.knowledge.CachingFactLookup;
import ai.knowledge.FactCache;
import ai.knowledge.RestCountriesCapitalSource;
import ai.knowledge.WikidataFactSource;
import ai.knowledge.WikidataSparqlClient;
import games.TriviaLiveLookups;
import games.DotsAndBoxesMatchManager;
import games.ReversiMatchManager;
import games.MemoryMatchMatchManager;
import games.AirHockeyMatchManager;
import games.WordDuelMatchManager;
import games.DiceDuelMatchManager;
import games.SnakeArenaMatchManager;
import games.TetrisDuelMatchManager;
import games.FusionGridMatchManager;
import games.TypingDuelMatchManager;
import games.SignalGridMatchManager;
import games.CardRushMatchManager;
import games.SquareWarsMatchManager;
import games.CheckersMatchManager;
import games.ConnectFourMatchManager;
import games.RockPaperScissorsMatchManager;
import games.BattleshipMatchManager;
import games.ChessMatchManager;
import games.ReplayManager;
import games.FightArenaMatchManager;
import games.AmongUsMatchManager;
import games.TelephoneMatchManager;
import games.RacingMatchManager;
import admin.FeedbackManager;
import social.ModerationManager;
import social.FriendManager;
import social.GroupChatManager;
import games.MatchManager;
import social.PartyManager;
import economy.AchievementManager;
import economy.LeaderboardManager;
import social.ChatManager;
import economy.GameHistoryManager;
import economy.EconomyManager;
import economy.TransactionManager;
import games.GameRegistry;
import account.ServerAccountStore;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class GameServer
{
    private final ServerAccountStore accountStore = new ServerAccountStore();
    private final GameRegistry gameRegistry = new GameRegistry();
    private final TransactionManager transactionManager = new TransactionManager();
    private final EconomyManager economyManager = new EconomyManager(accountStore, transactionManager);
    private final GameHistoryManager gameHistoryManager = new GameHistoryManager();
    private final ChatManager chatManager = new ChatManager();
    private final LeaderboardManager leaderboardManager = new LeaderboardManager(accountStore);
    private final AchievementManager achievementManager = new AchievementManager();
    private final PartyManager partyManager = new PartyManager(chatManager);
    private final MatchManager matchManager = new MatchManager(economyManager, gameHistoryManager, chatManager, leaderboardManager);
    private final GroupChatManager groupChatManager = new GroupChatManager(chatManager);
    private final FriendManager friendManager = new FriendManager(accountStore, chatManager);
    private final ModerationManager moderationManager = new ModerationManager();
    private final FeedbackManager feedbackManager = new FeedbackManager();
    private final RacingMatchManager racingMatchManager = new RacingMatchManager(gameHistoryManager, chatManager, economyManager, achievementManager);
    private final AmongUsMatchManager amongUsMatchManager = new AmongUsMatchManager(gameHistoryManager, chatManager, economyManager);
    private final TelephoneMatchManager telephoneMatchManager = new TelephoneMatchManager(gameHistoryManager, chatManager, economyManager);
    private final FightArenaMatchManager fightArenaMatchManager = new FightArenaMatchManager(gameHistoryManager, chatManager, leaderboardManager, partyManager, economyManager);
    private final ReplayManager replayManager = new ReplayManager();
    private final ChessMatchManager chessMatchManager = new ChessMatchManager(gameHistoryManager, chatManager, leaderboardManager, replayManager, economyManager);
    private final BattleshipMatchManager battleshipMatchManager = new BattleshipMatchManager(gameHistoryManager, chatManager, leaderboardManager, replayManager, economyManager);
    private final RockPaperScissorsMatchManager rpsMatchManager = new RockPaperScissorsMatchManager(gameHistoryManager, chatManager, leaderboardManager, replayManager, economyManager);
    private final ConnectFourMatchManager connectFourMatchManager = new ConnectFourMatchManager(economyManager, gameHistoryManager, chatManager, leaderboardManager);
    private final CheckersMatchManager checkersMatchManager = new CheckersMatchManager(economyManager, gameHistoryManager, chatManager, leaderboardManager);
    private final SquareWarsMatchManager squareWarsMatchManager = new SquareWarsMatchManager(gameHistoryManager, chatManager, economyManager, leaderboardManager);
    // Live trivia lookups for Trivia Blitz (ai.knowledge roadmap item 5) - cache-first, backed by
    // free/keyless web sources on a miss (restcountries.com for capitals; Wikidata's public SPARQL
    // endpoint for everything else - companies, historical events, inventions, cities). One shared
    // on-disk cache per category (each its own gamehub_fact_cache_*.dat file, via FactCache), built
    // once here and threaded through TriviaMatchManager into every match rather than one copy per
    // match. See TriviaLiveLookups/TriviaMatch for how these are used.
    private final WikidataSparqlClient wikidataClient = new WikidataSparqlClient();
    private final TriviaLiveLookups triviaLiveLookups = new TriviaLiveLookups(
        new CachingFactLookup(new FactCache("gamehub_fact_cache_capital.dat"),
            new RestCountriesCapitalSource(), "capital"),
        new CachingFactLookup(new FactCache("gamehub_fact_cache_company.dat"),
            new WikidataFactSource(wikidataClient,
                "SELECT ?year WHERE { ?item rdfs:label \"{subject}\"@en. ?item wdt:P571 ?date. "
                    + "BIND(YEAR(?date) AS ?year) } LIMIT 1",
                "year"),
            "founded"),
        new CachingFactLookup(new FactCache("gamehub_fact_cache_inventor.dat"),
            new WikidataFactSource(wikidataClient,
                "SELECT ?inventorLabel WHERE { ?item rdfs:label \"{subject}\"@en. ?item wdt:P61 ?inventor. "
                    + "?inventor rdfs:label ?inventorLabel. FILTER(LANG(?inventorLabel) = \"en\") } LIMIT 1",
                "inventorLabel"),
            "inventor"),
        new CachingFactLookup(new FactCache("gamehub_fact_cache_event.dat"),
            new WikidataFactSource(wikidataClient,
                "SELECT ?year WHERE { ?item rdfs:label \"{subject}\"@en. ?item wdt:P585 ?date. "
                    + "BIND(YEAR(?date) AS ?year) } LIMIT 1",
                "year"),
            "event"),
        new CachingFactLookup(new FactCache("gamehub_fact_cache_city.dat"),
            new WikidataFactSource(wikidataClient,
                "SELECT ?countryLabel WHERE { ?item rdfs:label \"{subject}\"@en. ?item wdt:P17 ?country. "
                    + "?country rdfs:label ?countryLabel. FILTER(LANG(?countryLabel) = \"en\") } LIMIT 1",
                "countryLabel"),
            "city")
    );
    private final TriviaMatchManager triviaMatchManager = new TriviaMatchManager(gameHistoryManager, chatManager, economyManager, leaderboardManager, triviaLiveLookups);
    private final DotsAndBoxesMatchManager dotsAndBoxesMatchManager = new DotsAndBoxesMatchManager(economyManager, gameHistoryManager, chatManager, leaderboardManager);
    private final ReversiMatchManager reversiMatchManager = new ReversiMatchManager(economyManager, gameHistoryManager, chatManager, leaderboardManager);
    private final MemoryMatchMatchManager memoryMatchMatchManager = new MemoryMatchMatchManager(economyManager, gameHistoryManager, chatManager, leaderboardManager);
    private final AirHockeyMatchManager airHockeyMatchManager = new AirHockeyMatchManager(economyManager, gameHistoryManager, chatManager, leaderboardManager);
    private final WordDuelMatchManager wordDuelMatchManager = new WordDuelMatchManager(economyManager, gameHistoryManager, chatManager, leaderboardManager);
    private final DiceDuelMatchManager diceDuelMatchManager = new DiceDuelMatchManager(economyManager, gameHistoryManager, chatManager, leaderboardManager);
    private final SnakeArenaMatchManager snakeArenaMatchManager = new SnakeArenaMatchManager(economyManager, gameHistoryManager, chatManager, leaderboardManager);
    private final TetrisDuelMatchManager tetrisDuelMatchManager = new TetrisDuelMatchManager(economyManager, gameHistoryManager, chatManager, leaderboardManager);
    private final FusionGridMatchManager fusionGridMatchManager = new FusionGridMatchManager(economyManager, gameHistoryManager, chatManager, leaderboardManager);
    private final TypingDuelMatchManager typingDuelMatchManager = new TypingDuelMatchManager(economyManager, gameHistoryManager, chatManager, leaderboardManager);
    private final SignalGridMatchManager signalGridMatchManager = new SignalGridMatchManager(economyManager, gameHistoryManager, chatManager, leaderboardManager);
    private final CardRushMatchManager cardRushMatchManager = new CardRushMatchManager(economyManager, gameHistoryManager, chatManager, leaderboardManager);
    private final AvatarStore avatarStore = new AvatarStore();
    private final TournamentManager tournamentManager = new TournamentManager(battleshipMatchManager, rpsMatchManager, leaderboardManager, chatManager, replayManager);
    private final TeamTournamentManager teamTournamentManager = new TeamTournamentManager(fightArenaMatchManager, leaderboardManager, partyManager, chatManager);
    private final GameSuggestionStore gameSuggestionStore = new GameSuggestionStore();
    private final ZombieSurvivalMatchManager zombieSurvivalMatchManager = new ZombieSurvivalMatchManager(gameHistoryManager, chatManager, economyManager, achievementManager, leaderboardManager);
    private final SpaceBattleMatchManager spaceBattleMatchManager = new SpaceBattleMatchManager(gameHistoryManager, chatManager, economyManager, achievementManager, leaderboardManager);
    private final AdminLog adminLog = new AdminLog();

    {
        // Wires AchievementManager into the managers that trigger its checks -
        // done here rather than via constructor arguments since AchievementManager
        // itself has no dependency on any of these three, only the reverse.
        leaderboardManager.setAchievementManager(achievementManager);
        gameHistoryManager.setAchievementManager(achievementManager);
        economyManager.setAchievementManager(achievementManager);
        achievementManager.setNotificationTargets(accountStore, chatManager);
    }

    private ServerSocket serverSocket;

    public boolean start()
    {
        try
        {
            serverSocket = new ServerSocket(NetworkConfig.getServerPort());
            System.out.println("Vertex server listening on port " + NetworkConfig.getServerPort());
        }
        catch (IOException e)
        {
            System.err.println("Server error: " + e.getMessage());
            return false;
        }

        Thread acceptThread = new Thread(new Runnable()
        {
            public void run() { acceptLoop(); }
        });
        acceptThread.setDaemon(true);
        acceptThread.start();

        return true;
    }

    private void acceptLoop()
    {
        while (!serverSocket.isClosed())
        {
            try
            {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());
                ClientHandler handler = new ClientHandler(
                    clientSocket, accountStore, gameRegistry, matchManager,
                    chatManager, groupChatManager, economyManager, gameHistoryManager,
                    friendManager, moderationManager, racingMatchManager, amongUsMatchManager,
                    fightArenaMatchManager, chessMatchManager, battleshipMatchManager, rpsMatchManager,
                    leaderboardManager, partyManager, achievementManager, tournamentManager, replayManager,
                    teamTournamentManager,
                    feedbackManager, gameSuggestionStore, zombieSurvivalMatchManager, spaceBattleMatchManager, adminLog,
                    connectFourMatchManager, avatarStore, checkersMatchManager, squareWarsMatchManager,
                    triviaMatchManager, dotsAndBoxesMatchManager, reversiMatchManager, memoryMatchMatchManager,
                    airHockeyMatchManager, wordDuelMatchManager, diceDuelMatchManager, snakeArenaMatchManager,
                    tetrisDuelMatchManager, fusionGridMatchManager, typingDuelMatchManager, signalGridMatchManager,
                    cardRushMatchManager, telephoneMatchManager);
                Thread thread = new Thread(handler);
                thread.start();
            }
            catch (IOException e)
            {
                if (serverSocket.isClosed())
                {
                    System.out.println("Accept loop stopped: server socket closed.");
                    break;
                }
                // A transient accept() failure (e.g. a temporary "too many open
                // files" under a burst of connections) must not permanently kill
                // the accept loop - the process keeps running forever either way
                // (see ServerMain), so silently breaking here would turn a
                // transient error into a full outage that looks like a live,
                // healthy server from the outside. Log it and keep accepting.
                System.err.println("Accept error, continuing: " + e.getMessage());
                try { Thread.sleep(200); } catch (InterruptedException ignored) { }
            }
        }
    }
}
