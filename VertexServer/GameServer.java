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
    private final RacingMatchManager racingMatchManager = new RacingMatchManager(gameHistoryManager, chatManager, economyManager, achievementManager);
    private final AmongUsMatchManager amongUsMatchManager = new AmongUsMatchManager(gameHistoryManager, chatManager);
    private final FightArenaMatchManager fightArenaMatchManager = new FightArenaMatchManager(gameHistoryManager, chatManager, leaderboardManager, partyManager);
    private final ReplayManager replayManager = new ReplayManager();
    private final ChessMatchManager chessMatchManager = new ChessMatchManager(gameHistoryManager, chatManager, leaderboardManager, replayManager);
    private final BattleshipMatchManager battleshipMatchManager = new BattleshipMatchManager(gameHistoryManager, chatManager, leaderboardManager, replayManager);
    private final RockPaperScissorsMatchManager rpsMatchManager = new RockPaperScissorsMatchManager(gameHistoryManager, chatManager, leaderboardManager, replayManager);
    private final TournamentManager tournamentManager = new TournamentManager(battleshipMatchManager, rpsMatchManager, leaderboardManager, chatManager, replayManager);
    private final TeamTournamentManager teamTournamentManager = new TeamTournamentManager(fightArenaMatchManager, leaderboardManager, partyManager, chatManager);

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
            serverSocket = new ServerSocket(NetworkConfig.SERVER_PORT);
            System.out.println("Vertex server listening on port " + NetworkConfig.SERVER_PORT);
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
                    teamTournamentManager);
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
