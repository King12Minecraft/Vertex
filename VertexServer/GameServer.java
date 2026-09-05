import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class GameServer
{
    /** Null means this server IS the main/canonical server - nothing to delegate to, sync methods simply aren't called. Set via setMainServer() before start(), matching how the port itself gets configured before starting. */
    private MainServerConnection mainServerConnection;

    public void setMainServer(String host, int port)
    {
        this.mainServerConnection = new MainServerConnection(host, port);
        this.syncService.setMainServerConnection(this.mainServerConnection);
    }

    public boolean isSatellite()
    {
        return mainServerConnection != null;
    }

    public MainServerConnection getMainServerConnection()
    {
        return mainServerConnection;
    }

    private final ServerAccountStore accountStore = new ServerAccountStore();
    private final GameRegistry gameRegistry = new GameRegistry();
    private final TransactionManager transactionManager = new TransactionManager();
    private final EconomyManager economyManager = new EconomyManager(accountStore, transactionManager);
    private final GameHistoryManager gameHistoryManager = new GameHistoryManager();
    private final ChatManager chatManager = new ChatManager();
    private final LeaderboardManager leaderboardManager = new LeaderboardManager(accountStore);
    private final AchievementManager achievementManager = new AchievementManager();
    private final SyncService syncService = new SyncService(accountStore, leaderboardManager, achievementManager);
    private final SatelliteRegistry satelliteRegistry = new SatelliteRegistry();
    private final PresenceRegistry presenceRegistry = new PresenceRegistry();
    private final PartyManager partyManager = new PartyManager(chatManager);
    private final MatchManager matchManager = new MatchManager(economyManager, gameHistoryManager, chatManager, leaderboardManager);
    private final GroupChatManager groupChatManager = new GroupChatManager(chatManager);
    private final FriendManager friendManager = new FriendManager(accountStore, chatManager);
    private final ModerationManager moderationManager = new ModerationManager();
    private final FeedbackManager feedbackManager = new FeedbackManager();
    private final RacingMatchManager racingMatchManager = new RacingMatchManager(gameHistoryManager, chatManager, economyManager, achievementManager);
    private final AmongUsMatchManager amongUsMatchManager = new AmongUsMatchManager(gameHistoryManager, chatManager, economyManager);
    private final FightArenaMatchManager fightArenaMatchManager = new FightArenaMatchManager(gameHistoryManager, chatManager, leaderboardManager, partyManager, economyManager);
    private final ReplayManager replayManager = new ReplayManager();
    private final ChessMatchManager chessMatchManager = new ChessMatchManager(gameHistoryManager, chatManager, leaderboardManager, replayManager, economyManager);
    private final BattleshipMatchManager battleshipMatchManager = new BattleshipMatchManager(gameHistoryManager, chatManager, leaderboardManager, replayManager, economyManager);
    private final RockPaperScissorsMatchManager rpsMatchManager = new RockPaperScissorsMatchManager(gameHistoryManager, chatManager, leaderboardManager, replayManager, economyManager);
    private final TournamentManager tournamentManager = new TournamentManager(battleshipMatchManager, rpsMatchManager, leaderboardManager, chatManager, replayManager);
    private final TeamTournamentManager teamTournamentManager = new TeamTournamentManager(fightArenaMatchManager, leaderboardManager, partyManager, chatManager);
    private final CustomGameStore customGameStore = new CustomGameStore();
    private final ZombieSurvivalMatchManager zombieSurvivalMatchManager = new ZombieSurvivalMatchManager(gameHistoryManager, chatManager, economyManager, achievementManager, leaderboardManager);
    private final SpaceBattleMatchManager spaceBattleMatchManager = new SpaceBattleMatchManager(gameHistoryManager, chatManager, economyManager, achievementManager, leaderboardManager);

    {
        // Wires AchievementManager into the managers that trigger its checks -
        // done here rather than via constructor arguments since AchievementManager
        // itself has no dependency on any of these three, only the reverse.
        leaderboardManager.setAchievementManager(achievementManager);
        gameHistoryManager.setAchievementManager(achievementManager);
        economyManager.setAchievementManager(achievementManager);
        achievementManager.setNotificationTargets(accountStore, chatManager);

        // Same reasoning for SyncService - a no-op everywhere until setMainServer() is
        // called (or forever, if this server IS the main server, never a satellite).
        leaderboardManager.setSyncService(syncService);
        achievementManager.setSyncService(syncService);
        economyManager.setSyncService(syncService);
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

        if (mainServerConnection != null && NetworkConfig.SATELLITE_SERVERS_ENABLED)
        {
            final int myPort = NetworkConfig.getServerPort();
            Thread registerThread = new Thread(new Runnable()
            {
                public void run() { mainServerConnection.registerAsSatellite(myPort); }
            });
            registerThread.setDaemon(true);
            registerThread.start();
        }

        return true;
    }

    private void acceptLoop()
    {
        while (true)
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
                    teamTournamentManager, mainServerConnection, satelliteRegistry, presenceRegistry,
                    feedbackManager, customGameStore, zombieSurvivalMatchManager, spaceBattleMatchManager);
                Thread thread = new Thread(handler);
                thread.start();
            }
            catch (IOException e)
            {
                System.err.println("Accept loop stopped: " + e.getMessage());
                break;
            }
        }
    }
}
