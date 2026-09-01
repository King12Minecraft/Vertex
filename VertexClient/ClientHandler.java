import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class ClientHandler implements Runnable
{
    private final Socket socket;
    private final ServerAccountStore accountStore;
    private final GameRegistry gameRegistry;
    private final MatchManager matchManager;
    private final ChatManager chatManager;
    private final GroupChatManager groupChatManager;
    private final EconomyManager economyManager;
    private final GameHistoryManager gameHistoryManager;
    private final FriendManager friendManager;
    private final ModerationManager moderationManager;

    private ObjectOutputStream out;
    private final Object writeLock = new Object();

    private String loggedInUsername;
    private Integer loggedInAccountId;
    private TicTacToeMatch currentMatch;
    private RacingMatch currentRacingMatch;
    private RacingMatchManager racingMatchManager;
    private AmongUsMatch currentAmongMatch;
    private AmongUsMatchManager amongUsMatchManager;
    private FightMatch currentFightMatch;
    private FightArenaMatchManager fightArenaMatchManager;
    private ChessMatch currentChessMatch;
    private ChessMatchManager chessMatchManager;
    private BattleshipMatch currentBattleshipMatch;
    private BattleshipMatchManager battleshipMatchManager;
    private RockPaperScissorsMatch currentRpsMatch;
    private RockPaperScissorsMatchManager rpsMatchManager;
    private LeaderboardManager leaderboardManager;
    private PartyManager partyManager;
    private AchievementManager achievementManager;
    private TournamentManager tournamentManager;
    private ReplayManager replayManager;
    private TeamTournamentManager teamTournamentManager;
    private MainServerConnection mainServerConnection;
    private SatelliteRegistry satelliteRegistry;

    public ClientHandler(Socket socket, ServerAccountStore accountStore, GameRegistry gameRegistry,
                          MatchManager matchManager, ChatManager chatManager,
                          GroupChatManager groupChatManager, EconomyManager economyManager,
                          GameHistoryManager gameHistoryManager, FriendManager friendManager,
                          ModerationManager moderationManager, RacingMatchManager racingMatchManager,
                          AmongUsMatchManager amongUsMatchManager, FightArenaMatchManager fightArenaMatchManager,
                          ChessMatchManager chessMatchManager, BattleshipMatchManager battleshipMatchManager,
                          RockPaperScissorsMatchManager rpsMatchManager, LeaderboardManager leaderboardManager,
                          PartyManager partyManager, AchievementManager achievementManager, TournamentManager tournamentManager,
                          ReplayManager replayManager, TeamTournamentManager teamTournamentManager,
                          MainServerConnection mainServerConnection, SatelliteRegistry satelliteRegistry)
    {
        this.socket = socket;
        this.accountStore = accountStore;
        this.gameRegistry = gameRegistry;
        this.matchManager = matchManager;
        this.chatManager = chatManager;
        this.groupChatManager = groupChatManager;
        this.economyManager = economyManager;
        this.gameHistoryManager = gameHistoryManager;
        this.friendManager = friendManager;
        this.moderationManager = moderationManager;
        this.racingMatchManager = racingMatchManager;
        this.amongUsMatchManager = amongUsMatchManager;
        this.fightArenaMatchManager = fightArenaMatchManager;
        this.chessMatchManager = chessMatchManager;
        this.battleshipMatchManager = battleshipMatchManager;
        this.rpsMatchManager = rpsMatchManager;
        this.leaderboardManager = leaderboardManager;
        this.partyManager = partyManager;
        this.achievementManager = achievementManager;
        this.tournamentManager = tournamentManager;
        this.replayManager = replayManager;
        this.teamTournamentManager = teamTournamentManager;
        this.mainServerConnection = mainServerConnection;
        this.satelliteRegistry = satelliteRegistry;
    }

    public String getLoggedInUsername() { return loggedInUsername; }
    public Integer getAccountId() { return loggedInAccountId; }
    public void setCurrentMatch(TicTacToeMatch match) { this.currentMatch = match; }
    public void setCurrentRacingMatch(RacingMatch match) { this.currentRacingMatch = match; }
    public void setCurrentAmongMatch(AmongUsMatch match) { this.currentAmongMatch = match; }
    public void setCurrentFightMatch(FightMatch match) { this.currentFightMatch = match; }
    public void setCurrentChessMatch(ChessMatch match) { this.currentChessMatch = match; }
    public void setCurrentBattleshipMatch(BattleshipMatch match) { this.currentBattleshipMatch = match; }
    public void setCurrentRpsMatch(RockPaperScissorsMatch match) { this.currentRpsMatch = match; }

    public void sendMessage(Message message)
    {
        synchronized (writeLock)
        {
            try
            {
                if (out != null)
                {
                    out.writeObject(message);
                    out.flush();
                }
            }
            catch (IOException e)
            {
                // Client is probably already gone - read loop's own catch handles cleanup.
            }
        }
    }

    /** Kick support - sends a notice, then closes the socket. The blocked read in run() will throw and fall through to the normal disconnect cleanup in finally. */
    public void forceDisconnect(String reason)
    {
        Message notice = new Message();
        notice.setType(MessageType.FORCE_DISCONNECT_NOTICE);
        notice.setErrorText(reason);
        sendMessage(notice);
        try { socket.close(); } catch (IOException ignored) { }
    }

    public void run()
    {
        try
        {
            out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            while (true)
            {
                Message request = (Message) in.readObject();
                Message response = handle(request);
                if (response != null) sendMessage(response);
            }
        }
        catch (IOException e)
        {
            System.out.println("Client disconnected: " + socket.getInetAddress());
        }
        catch (ClassNotFoundException e)
        {
            System.err.println("Bad message from client: " + e.getMessage());
        }
        finally
        {
            matchManager.cancelWaiting(this);
            racingMatchManager.cancelWaiting(this);
            amongUsMatchManager.cancelWaiting(this);
            fightArenaMatchManager.cancelWaiting(this);
            chessMatchManager.cancelWaiting(this);
            battleshipMatchManager.cancelWaiting(this);
            rpsMatchManager.cancelWaiting(this);
            partyManager.handleDisconnect(this);
            tournamentManager.handleDisconnect(this);
            teamTournamentManager.handleDisconnect(this);
            chatManager.unregister(this, loggedInUsername);
            if (currentMatch != null) currentMatch.handleDisconnect(this);
            if (currentRacingMatch != null) currentRacingMatch.handleDisconnect(this);
            if (currentAmongMatch != null) currentAmongMatch.handleDisconnect(this);
            if (currentFightMatch != null) currentFightMatch.handleDisconnect(this);
            if (currentChessMatch != null) currentChessMatch.handleDisconnect(this);
            if (currentBattleshipMatch != null) currentBattleshipMatch.handleDisconnect(this);
            if (currentRpsMatch != null) currentRpsMatch.handleDisconnect(this);

            if (loggedInUsername != null)
            {
                Account account = accountStore.findByUsername(loggedInUsername);
                if (account != null)
                {
                    friendManager.broadcastPresenceChange(account, false);
                }
            }

            try { socket.close(); } catch (IOException ignored) { }
        }
    }

    private Message handle(Message request)
    {
        if (request.getType() == MessageType.LOGIN_REQUEST) return handleLogin(request);
        if (request.getType() == MessageType.SYNC_AUTH_REQUEST) return handleSyncAuth(request);
        if (request.getType() == MessageType.SYNC_PUSH_REQUEST) return handleSyncPush(request);
        if (request.getType() == MessageType.SATELLITE_REGISTER_REQUEST) return handleSatelliteRegister(request);
        if (request.getType() == MessageType.SATELLITE_LIST_REQUEST) return handleSatelliteList();
        if (request.getType() == MessageType.CREATE_ACCOUNT_REQUEST) return handleCreateAccount(request);
        if (request.getType() == MessageType.GAME_LIST_REQUEST) return handleGameList();
        if (request.getType() == MessageType.CHANGE_USERNAME_REQUEST) return handleChangeUsername(request);
        if (request.getType() == MessageType.CHANGE_PASSWORD_REQUEST) return handleChangePassword(request);
        if (request.getType() == MessageType.FIND_MATCH_REQUEST) return handleFindMatch();
        if (request.getType() == MessageType.MAKE_MOVE_REQUEST) return handleMakeMove(request);
        if (request.getType() == MessageType.LEAVE_MATCH_REQUEST) return handleLeaveMatch();
        if (request.getType() == MessageType.CHAT_MESSAGE) return handleChatMessage(request);
        if (request.getType() == MessageType.PRIVATE_MESSAGE) return handlePrivateMessage(request);
        if (request.getType() == MessageType.GROUP_CREATE_REQUEST) return handleGroupCreate(request);
        if (request.getType() == MessageType.GROUP_MESSAGE) return handleGroupMessage(request);
        if (request.getType() == MessageType.SHOP_ITEMS_REQUEST) return handleShopItems();
        if (request.getType() == MessageType.PURCHASE_REQUEST) return handlePurchase(request);
        if (request.getType() == MessageType.CHALLENGES_REQUEST) return handleChallenges();
        if (request.getType() == MessageType.GAME_PLAYED_REQUEST) return handleGamePlayed(request);
        if (request.getType() == MessageType.GAME_HISTORY_REQUEST) return handleGameHistory();
        if (request.getType() == MessageType.ONLINE_USERS_REQUEST) return handleOnlineUsers();
        if (request.getType() == MessageType.SELECT_COLOR_REQUEST) return handleSelectColor(request);
        if (request.getType() == MessageType.SELECT_BADGE_REQUEST) return handleSelectBadge(request);
        if (request.getType() == MessageType.ADMIN_PLAYER_LIST_REQUEST) return handleAdminPlayerList();
        if (request.getType() == MessageType.FRIEND_REQUEST_SEND) return handleFriendRequestSend(request);
        if (request.getType() == MessageType.FRIEND_ACCEPT_REQUEST) return handleFriendAccept(request);
        if (request.getType() == MessageType.FRIEND_DECLINE_REQUEST) return handleFriendDecline(request);
        if (request.getType() == MessageType.FRIEND_LIST_REQUEST) return handleFriendList();
        if (request.getType() == MessageType.TRANSACTION_HISTORY_REQUEST) return handleTransactionHistory();
        if (request.getType() == MessageType.MOD_MUTE_REQUEST) return handleModMute(request);
        if (request.getType() == MessageType.MOD_UNMUTE_REQUEST) return handleModUnmute(request);
        if (request.getType() == MessageType.MOD_KICK_REQUEST) return handleModKick(request);
        if (request.getType() == MessageType.MOD_BAN_REQUEST) return handleModBan(request);
        if (request.getType() == MessageType.MOD_UNBAN_REQUEST) return handleModUnban(request);
        if (request.getType() == MessageType.REPORT_SUBMIT_REQUEST) return handleReportSubmit(request);
        if (request.getType() == MessageType.REPORT_LIST_REQUEST) return handleReportList();
        if (request.getType() == MessageType.REPORT_RESOLVE_REQUEST) return handleReportResolve(request);
        if (request.getType() == MessageType.RACE_FIND_MATCH_REQUEST) return handleRaceFindMatch();
        if (request.getType() == MessageType.RACE_LEAVE_QUEUE_REQUEST) return handleRaceLeaveQueue();
        if (request.getType() == MessageType.RACE_FINISHED_REQUEST) return handleRaceFinished(request);
        if (request.getType() == MessageType.AMONG_FIND_MATCH_REQUEST) return handleAmongFindMatch();
        if (request.getType() == MessageType.AMONG_LEAVE_QUEUE_REQUEST) return handleAmongLeaveQueue();
        if (request.getType() == MessageType.AMONG_TASK_COMPLETE_REQUEST) return handleAmongTaskComplete(request);
        if (request.getType() == MessageType.AMONG_KILL_REQUEST) return handleAmongKill(request);
        if (request.getType() == MessageType.AMONG_CALL_MEETING_REQUEST) return handleAmongCallMeeting();
        if (request.getType() == MessageType.AMONG_VOTE_REQUEST) return handleAmongVote(request);
        if (request.getType() == MessageType.FIGHT_FIND_MATCH_REQUEST) return handleFightFindMatch(request);
        if (request.getType() == MessageType.FIGHT_LEAVE_QUEUE_REQUEST) return handleFightLeaveQueue();
        if (request.getType() == MessageType.FIGHT_INPUT_UPDATE) return handleFightInputUpdate(request);
        if (request.getType() == MessageType.GAME_INVITE) return handleGameInvite(request);
        if (request.getType() == MessageType.CHESS_FIND_MATCH_REQUEST) return handleChessFindMatch();
        if (request.getType() == MessageType.CHESS_LEAVE_QUEUE_REQUEST) return handleChessLeaveQueue();
        if (request.getType() == MessageType.CHESS_MOVE_REQUEST) return handleChessMove(request);
        if (request.getType() == MessageType.CHESS_RESIGN_REQUEST) return handleChessResign();
        if (request.getType() == MessageType.CHESS_DRAW_OFFER_REQUEST) return handleChessDrawOffer();
        if (request.getType() == MessageType.CHESS_DRAW_RESPONSE_REQUEST) return handleChessDrawResponse(request);
        if (request.getType() == MessageType.REMATCH_REQUEST) return handleRematchRequest(request);
        if (request.getType() == MessageType.REMATCH_RESPONSE) return handleRematchResponse(request);
        if (request.getType() == MessageType.BATTLESHIP_FIND_MATCH_REQUEST) return handleBattleshipFindMatch();
        if (request.getType() == MessageType.BATTLESHIP_LEAVE_QUEUE_REQUEST) return handleBattleshipLeaveQueue();
        if (request.getType() == MessageType.BATTLESHIP_FIRE_REQUEST) return handleBattleshipFire(request);
        if (request.getType() == MessageType.RPS_FIND_MATCH_REQUEST) return handleRpsFindMatch();
        if (request.getType() == MessageType.RPS_LEAVE_QUEUE_REQUEST) return handleRpsLeaveQueue();
        if (request.getType() == MessageType.RPS_MOVE_REQUEST) return handleRpsMove(request);
        if (request.getType() == MessageType.LEADERBOARD_REQUEST) return handleLeaderboardRequest(request);
        if (request.getType() == MessageType.ACHIEVEMENTS_REQUEST) return handleAchievementsRequest();
        if (request.getType() == MessageType.TOURNAMENT_CREATE_REQUEST) return handleTournamentCreate(request);
        if (request.getType() == MessageType.TOURNAMENT_JOIN_REQUEST) return handleTournamentJoin(request);
        if (request.getType() == MessageType.TOURNAMENT_LIST_REQUEST) return handleTournamentList();
        if (request.getType() == MessageType.TEAM_TOURNAMENT_CREATE_REQUEST) return handleTeamTournamentCreate(request);
        if (request.getType() == MessageType.TEAM_TOURNAMENT_JOIN_REQUEST) return handleTeamTournamentJoin(request);
        if (request.getType() == MessageType.TEAM_TOURNAMENT_LIST_REQUEST) return handleTeamTournamentList();
        if (request.getType() == MessageType.SPECTATABLE_MATCHES_REQUEST) return handleSpectatableMatches(request);
        if (request.getType() == MessageType.SPECTATE_REQUEST) return handleSpectate(request);
        if (request.getType() == MessageType.REPLAY_LIST_REQUEST) return handleReplayList();
        if (request.getType() == MessageType.REPLAY_REQUEST) return handleReplayFetch(request);
        if (request.getType() == MessageType.PARTY_CREATE_REQUEST) return handlePartyCreate();
        if (request.getType() == MessageType.PARTY_INVITE_REQUEST) return handlePartyInvite(request);
        if (request.getType() == MessageType.PARTY_JOIN_BY_CODE_REQUEST) return handlePartyJoinByCode(request);
        if (request.getType() == MessageType.PARTY_LEAVE_REQUEST) return handlePartyLeave();
        if (request.getType() == MessageType.PARTY_KICK_REQUEST) return handlePartyKick(request);

        Message response = new Message();
        response.setSuccess(false);
        response.setErrorText("Unknown request type.");
        return response;
    }

    private Message handleLogin(Message request)
    {
        Message response = new Message();
        response.setType(MessageType.LOGIN_RESPONSE);

        if (moderationManager.isBanned(request.getUsername()))
        {
            response.setSuccess(false);
            response.setErrorText("This account has been banned from the server.");
            return response;
        }

        ServerAccountStore.LoginResult result =
            accountStore.attemptLogin(request.getUsername(), request.getPassword());

        if (result != ServerAccountStore.LoginResult.SUCCESS && mainServerConnection != null)
        {
            // Not known locally (or local password mismatch) - if we're a satellite, this
            // might just mean nobody's ever logged into THIS server with this account before,
            // or that main has a newer password than what's cached here. Either way, ask main.
            Message syncResult = mainServerConnection.authenticateAgainstMain(request.getUsername(), request.getPassword());
            if (syncResult != null && syncResult.isSuccess())
            {
                Account localCopy = accountStore.createOrUpdateFromSync(syncResult.getSyncAccount());
                leaderboardManager.applySyncedRatings(localCopy.getAccountId(), syncResult.getSyncRatings());
                achievementManager.applySyncedUnlocks(localCopy.getAccountId(), syncResult.getUnlockedAchievementIds());
                result = ServerAccountStore.LoginResult.SUCCESS;
            }
        }

        if (result == ServerAccountStore.LoginResult.SUCCESS)
        {
            response.setSuccess(true);
            Account account = accountStore.findByUsername(request.getUsername());

            int dailyReward = economyManager.applyDailyLoginReward(account);
            response.setDailyRewardCoins(dailyReward);
            response.setLoginStreak(account.getLoginStreak());

            response.setAccount(account);
            loggedInUsername = account.getUsername();
            loggedInAccountId = account.getAccountId();
            chatManager.register(this, loggedInUsername);
            friendManager.broadcastPresenceChange(account, true);
        }
        else
        {
            response.setSuccess(false);
            response.setErrorText(describeLoginFailure(result));
        }
        return response;
    }

    private String describeLoginFailure(ServerAccountStore.LoginResult result)
    {
        if (result == ServerAccountStore.LoginResult.NO_SUCH_ACCOUNT) return "No account with that username.";
        if (result == ServerAccountStore.LoginResult.LOCKED_OUT) return "Too many failed attempts. This account is temporarily locked.";
        return "Incorrect password.";
    }

    /** A satellite server delegating a login attempt to this server, treating it as the main/canonical account store - deliberately reuses the exact same attemptLogin() verification handleLogin() uses, since the whole point is that a synced account behaves identically whether logged into directly or through a satellite. Doesn't register the requester in chatManager/friendManager the way a real player login does - this connection is another server, not a player, and closes right after this one exchange. */
    private Message handleSyncAuth(Message request)
    {
        Message response = new Message();
        response.setType(MessageType.SYNC_AUTH_RESPONSE);

        ServerAccountStore.LoginResult result =
            accountStore.attemptLogin(request.getUsername(), request.getPassword());

        if (result != ServerAccountStore.LoginResult.SUCCESS)
        {
            response.setSuccess(false);
            response.setErrorText(describeLoginFailure(result));
            return response;
        }

        Account account = accountStore.findByUsername(request.getUsername());
        response.setSuccess(true);
        response.setSyncAccount(account);
        response.setSyncRatings(leaderboardManager.getAllRatingsForAccount(account.getAccountId()));
        response.setUnlockedAchievementIds(new java.util.ArrayList<String>(achievementManager.getUnlocked(account.getAccountId())));
        return response;
    }

    /** A satellite server pushing whatever changed locally (coins, ratings, achievements) back up to this server as the canonical store. Applies the incoming Account snapshot directly (last-write-wins - see MainServerConnection's own notes on this tradeoff) rather than trying to reconcile field-by-field against whatever this server currently has on record. */
    private Message handleSyncPush(Message request)
    {
        Message response = new Message();
        response.setType(MessageType.SYNC_PUSH_RESPONSE);

        Account incoming = request.getSyncAccount();
        if (incoming == null)
        {
            response.setSuccess(false);
            return response;
        }

        boolean applied = accountStore.applySyncedAccount(incoming);
        if (applied)
        {
            leaderboardManager.applySyncedRatings(incoming.getAccountId(), request.getSyncRatings());
            achievementManager.applySyncedUnlocks(incoming.getAccountId(), request.getUnlockedAchievementIds());
        }
        response.setSuccess(applied);
        return response;
    }

    /** No response needed - registering is a courtesy heads-up ("I exist, here's my port"), not a request that expects data back. Still sends an empty acknowledgement Message so registerAsSatellite's readObject() call has something waiting for it rather than blocking indefinitely. */
    private Message handleSatelliteRegister(Message request)
    {
        String host = socket.getInetAddress().getHostAddress();
        satelliteRegistry.register(host, request.getSatellitePort());
        return new Message();
    }

    private Message handleSatelliteList()
    {
        Message response = new Message();
        response.setType(MessageType.SATELLITE_LIST_RESPONSE);
        if (!isAdmin())
        {
            response.setSuccess(false);
            response.setErrorText("Admins only.");
            return response;
        }
        response.setSuccess(true);
        response.setSatelliteList(satelliteRegistry.listAll());
        return response;
    }

    private boolean isAdmin()
    {
        if (loggedInUsername == null)
        {
            return false;
        }
        Account requester = accountStore.findByUsername(loggedInUsername);
        return requester != null && requester.getRole() == Role.ADMIN;
    }

    private Message handleCreateAccount(Message request)
    {
        Message response = new Message();
        response.setType(MessageType.CREATE_ACCOUNT_RESPONSE);

        String username = request.getUsername();
        String password = request.getPassword();

        if (moderationManager.isBanned(username))
        {
            response.setSuccess(false);
            response.setErrorText("This username has been banned from the server.");
            return response;
        }
        if (username == null || username.length() < 3)
        {
            response.setSuccess(false);
            response.setErrorText("Username must be at least 3 characters.");
            return response;
        }
        if (accountStore.usernameExists(username))
        {
            response.setSuccess(false);
            response.setErrorText("That username is already taken.");
            return response;
        }
        if (password == null || password.length() < 6)
        {
            response.setSuccess(false);
            response.setErrorText("Password must be at least 6 characters.");
            return response;
        }

        boolean isLoopback = socket.getInetAddress().isLoopbackAddress();
        boolean grantAdmin = isLoopback && !accountStore.hasAdminAccount();
        Role role = grantAdmin ? Role.ADMIN : Role.PLAYER;

        Account account = accountStore.createAccount(username, password, role);
        loggedInUsername = account.getUsername();
        loggedInAccountId = account.getAccountId();
        chatManager.register(this, loggedInUsername);

        int dailyReward = economyManager.applyDailyLoginReward(account);
        response.setDailyRewardCoins(dailyReward);
        response.setLoginStreak(account.getLoginStreak());

        response.setSuccess(true);
        response.setAccount(account);
        response.setBootstrapAdmin(grantAdmin);
        return response;
    }

    /** Populates each game's live queueCount before returning the list - real value for tictactoe-online, 0 for everything else. */
    private Message handleGameList()
    {
        Message response = new Message();
        response.setType(MessageType.GAME_LIST_RESPONSE);
        response.setSuccess(true);

        List<GameInfo> games = gameRegistry.getAllGames();
        for (int i = 0; i < games.size(); i++)
        {
            GameInfo game = games.get(i);
            if ("tictactoe-online".equals(game.getGameId()))
            {
                game.setQueueCount(matchManager.getQueueCount());
            }
            else if ("racing".equals(game.getGameId()))
            {
                game.setQueueCount(racingMatchManager.getQueueCount());
            }
            else if ("among-us".equals(game.getGameId()))
            {
                game.setQueueCount(amongUsMatchManager.getQueueCount());
            }
            else if ("rock-paper-scissors".equals(game.getGameId()))
            {
                game.setQueueCount(rpsMatchManager.getQueueCount());
            }
        }
        response.setGameList(games);
        return response;
    }

    private Message handleChangeUsername(Message request)
    {
        Message response = new Message();
        response.setType(MessageType.CHANGE_USERNAME_RESPONSE);

        String oldUsername = loggedInUsername;

        ServerAccountStore.ChangeResult result = accountStore.changeUsername(
            request.getUsername(), request.getCurrentPassword(), request.getNewUsername());

        if (result == ServerAccountStore.ChangeResult.SUCCESS)
        {
            response.setSuccess(true);
            Account account = accountStore.findByUsername(request.getNewUsername());
            response.setAccount(account);
            loggedInUsername = account.getUsername();

            chatManager.unregister(this, oldUsername);
            chatManager.register(this, loggedInUsername);
        }
        else
        {
            response.setSuccess(false);
            response.setErrorText(describeChangeFailure(result));
        }
        return response;
    }

    private Message handleChangePassword(Message request)
    {
        Message response = new Message();
        response.setType(MessageType.CHANGE_PASSWORD_RESPONSE);

        ServerAccountStore.ChangeResult result = accountStore.changePassword(
            request.getUsername(), request.getCurrentPassword(), request.getNewPassword());

        response.setSuccess(result == ServerAccountStore.ChangeResult.SUCCESS);
        if (!response.isSuccess())
        {
            response.setErrorText(describeChangeFailure(result));
        }
        return response;
    }

    private String describeChangeFailure(ServerAccountStore.ChangeResult result)
    {
        if (result == ServerAccountStore.ChangeResult.WRONG_PASSWORD) return "Current password is incorrect.";
        if (result == ServerAccountStore.ChangeResult.NO_SUCH_ACCOUNT) return "Account not found.";
        if (result == ServerAccountStore.ChangeResult.USERNAME_TAKEN) return "That username is already taken.";
        if (result == ServerAccountStore.ChangeResult.USERNAME_TOO_SHORT) return "Username must be at least 3 characters.";
        if (result == ServerAccountStore.ChangeResult.PASSWORD_TOO_SHORT) return "New password must be at least 6 characters.";
        return "Something went wrong.";
    }

    private Message handleFindMatch()
    {
        if (loggedInUsername != null) matchManager.findMatch(this);
        return null;
    }

    private Message handleMakeMove(Message request)
    {
        if (currentMatch != null) currentMatch.makeMove(this, request.getCellIndex());
        return null;
    }

    private Message handleLeaveMatch()
    {
        matchManager.cancelWaiting(this);
        if (currentMatch != null)
        {
            currentMatch.handleDisconnect(this);
            currentMatch = null;
        }
        return null;
    }

    private Message handleChatMessage(Message request)
    {
        if (loggedInUsername != null)
        {
            if (moderationManager.isMuted(loggedInUsername))
            {
                sendMuteNotice();
                return null;
            }
            Account account = accountStore.findByUsername(loggedInUsername);
            String colorId = account != null ? account.getPlayerColorName() : null;
            String badgeId = account != null ? account.getEquippedBadgeId() : null;
            chatManager.broadcast(loggedInUsername, colorId, badgeId, request.getChatText(),
                request.getFileName(), request.getFileData());
        }
        return null;
    }

    private Message handlePrivateMessage(Message request)
    {
        if (loggedInUsername == null) return null;
        if (moderationManager.isMuted(loggedInUsername))
        {
            sendMuteNotice();
            return null;
        }

        String toUsername = request.getToUsername();
        if (toUsername == null) return null;

        String trimmedText = ChatManager.trimText(request.getChatText());
        byte[] validFileData = ChatManager.validateFile(request.getFileData());
        String validFileName = validFileData != null ? request.getFileName() : null;

        if (trimmedText.isEmpty() && validFileData == null)
        {
            return null;
        }

        Account account = accountStore.findByUsername(loggedInUsername);
        String colorId = account != null ? account.getPlayerColorName() : null;
        String badgeId = account != null ? account.getEquippedBadgeId() : null;

        Message delivery = new Message();
        delivery.setType(MessageType.PRIVATE_MESSAGE);
        delivery.setUsername(loggedInUsername);
        delivery.setToUsername(toUsername);
        delivery.setSenderColorId(colorId);
        delivery.setSenderBadgeId(badgeId);
        delivery.setChatText(trimmedText);
        delivery.setFileName(validFileName);
        delivery.setFileData(validFileData);

        ClientHandler recipient = chatManager.findByUsername(toUsername);
        if (recipient != null && recipient != this) recipient.sendMessage(delivery);

        sendMessage(delivery);
        return null;
    }

    private Message handleGroupCreate(Message request)
    {
        Message response = new Message();
        response.setType(MessageType.GROUP_CREATE_RESPONSE);

        if (loggedInUsername == null)
        {
            response.setSuccess(false);
            response.setErrorText("Not logged in.");
            return response;
        }

        GroupChatManager.Group group = groupChatManager.createGroup(
            loggedInUsername, request.getGroupName(), request.getMemberUsernames());

        response.setSuccess(true);
        response.setGroupId(group.groupId);
        response.setGroupName(group.name);
        return response;
    }

    private Message handleGroupMessage(Message request)
    {
        if (loggedInUsername != null && request.getGroupId() != null)
        {
            if (moderationManager.isMuted(loggedInUsername))
            {
                sendMuteNotice();
                return null;
            }
            Account account = accountStore.findByUsername(loggedInUsername);
            String colorId = account != null ? account.getPlayerColorName() : null;
            String badgeId = account != null ? account.getEquippedBadgeId() : null;
            groupChatManager.sendGroupMessage(request.getGroupId(), loggedInUsername, colorId, badgeId,
                request.getChatText(), request.getFileName(), request.getFileData());
        }
        return null;
    }

    private void sendMuteNotice()
    {
        Message notice = new Message();
        notice.setType(MessageType.ERROR_NOTICE);
        notice.setErrorText("You're muted and can't send messages right now.");
        sendMessage(notice);
    }

    private Message handleShopItems()
    {
        Message response = new Message();
        response.setType(MessageType.SHOP_ITEMS_RESPONSE);
        response.setSuccess(true);

        List<ShopItemInfo> items = new ArrayList<ShopItemInfo>();
        List<ShopItemDefinition> defs = economyManager.getShopItems();
        for (int i = 0; i < defs.size(); i++)
        {
            ShopItemDefinition d = defs.get(i);
            items.add(new ShopItemInfo(d.id, d.name, d.priceCoins, d.colorHex, d.type));
        }
        response.setShopItems(items);
        return response;
    }

    private Message handlePurchase(Message request)
    {
        Message response = new Message();
        response.setType(MessageType.PURCHASE_RESPONSE);

        int[] newBalance = new int[1];
        EconomyManager.PurchaseResult result = economyManager.purchase(this, request.getItemId(), newBalance);

        if (result == EconomyManager.PurchaseResult.SUCCESS)
        {
            response.setSuccess(true);
            response.setCoins(newBalance[0]);
        }
        else
        {
            response.setSuccess(false);
            response.setErrorText(describePurchaseFailure(result));
        }
        return response;
    }

    private String describePurchaseFailure(EconomyManager.PurchaseResult result)
    {
        if (result == EconomyManager.PurchaseResult.INSUFFICIENT_COINS) return "You don't have enough coins.";
        if (result == EconomyManager.PurchaseResult.ALREADY_OWNED) return "You already own this.";
        if (result == EconomyManager.PurchaseResult.ITEM_NOT_FOUND) return "That item doesn't exist.";
        return "Not logged in.";
    }

    private Message handleChallenges()
    {
        Message response = new Message();
        response.setType(MessageType.CHALLENGES_RESPONSE);
        response.setSuccess(true);

        if (loggedInUsername != null)
        {
            Account account = accountStore.findByUsername(loggedInUsername);
            if (account != null)
            {
                response.setChallenges(economyManager.getChallengeManager().getProgressFor(account));
            }
        }
        return response;
    }

    private Message handleGamePlayed(Message request)
    {
        if (loggedInAccountId != null)
        {
            gameHistoryManager.recordPlay(loggedInAccountId, request.getGameId());

            if ("snake".equals(request.getGameId()))
            {
                economyManager.awardSnakeScore(this, request.getScore());
            }
        }
        return null;
    }

    private Message handleGameHistory()
    {
        Message response = new Message();
        response.setType(MessageType.GAME_HISTORY_RESPONSE);
        response.setSuccess(true);

        if (loggedInAccountId != null)
        {
            response.setRecentGameIds(gameHistoryManager.getRecentGameIds(loggedInAccountId));
        }
        response.setTrendingGameIds(gameHistoryManager.getTrendingGameIds());
        return response;
    }

    private Message handleOnlineUsers()
    {
        Message response = new Message();
        response.setType(MessageType.ONLINE_USERS_RESPONSE);
        response.setSuccess(true);

        List<String> users = chatManager.getOnlineUsernames();
        List<String> others = new ArrayList<String>();
        for (int i = 0; i < users.size(); i++)
        {
            if (!users.get(i).equalsIgnoreCase(loggedInUsername))
            {
                others.add(users.get(i));
            }
        }
        response.setOnlineUsernames(others);
        return response;
    }

    private Message handleSelectColor(Message request)
    {
        Message response = new Message();
        response.setType(MessageType.SELECT_COLOR_RESPONSE);

        if (loggedInUsername == null)
        {
            response.setSuccess(false);
            response.setErrorText("Not logged in.");
            return response;
        }

        Account account = accountStore.findByUsername(loggedInUsername);
        String itemId = request.getItemId();

        if (account == null)
        {
            response.setSuccess(false);
            response.setErrorText("Account not found.");
            return response;
        }
        if (itemId == null || (!"Default".equals(itemId) && !account.getOwnedItemIds().contains(itemId)))
        {
            response.setSuccess(false);
            response.setErrorText("You don't own that color.");
            return response;
        }

        account.setPlayerColorName(itemId);
        accountStore.updateAccount(account);

        response.setSuccess(true);
        response.setItemId(itemId);
        return response;
    }

    private Message handleSelectBadge(Message request)
    {
        Message response = new Message();
        response.setType(MessageType.SELECT_BADGE_RESPONSE);

        if (loggedInUsername == null)
        {
            response.setSuccess(false);
            response.setErrorText("Not logged in.");
            return response;
        }

        Account account = accountStore.findByUsername(loggedInUsername);
        String itemId = request.getItemId();

        if (account == null)
        {
            response.setSuccess(false);
            response.setErrorText("Account not found.");
            return response;
        }
        if (itemId == null || (!"".equals(itemId) && !account.getOwnedItemIds().contains(itemId)))
        {
            response.setSuccess(false);
            response.setErrorText("You don't own that badge.");
            return response;
        }

        account.setEquippedBadgeId(itemId);
        accountStore.updateAccount(account);

        response.setSuccess(true);
        response.setItemId(itemId);
        return response;
    }

    /** Admin/Moderator only - real server-side role check, never trusts the client's own PermissionManager. */
    private Message handleAdminPlayerList()
    {
        Message response = new Message();
        response.setType(MessageType.ADMIN_PLAYER_LIST_RESPONSE);

        if (!isModeratorOrAdmin())
        {
            response.setSuccess(false);
            response.setErrorText("You don't have permission to view this.");
            return response;
        }

        response.setSuccess(true);
        response.setOnlineUsernames(chatManager.getOnlineUsernames());
        response.setAllRegisteredUsernames(accountStore.getAllUsernames());
        return response;
    }

    private boolean isModeratorOrAdmin()
    {
        if (loggedInUsername == null)
        {
            return false;
        }
        Account requester = accountStore.findByUsername(loggedInUsername);
        return requester != null && (requester.getRole() == Role.ADMIN || requester.getRole() == Role.MODERATOR);
    }

    private Message handleFriendRequestSend(Message request)
    {
        Message response = new Message();
        response.setType(MessageType.FRIEND_REQUEST_SEND_RESPONSE);

        if (loggedInUsername == null)
        {
            response.setSuccess(false);
            response.setErrorText("Not logged in.");
            return response;
        }

        FriendManager.SendResult result = friendManager.sendRequest(loggedInUsername, request.getToUsername());

        if (result == FriendManager.SendResult.SENT || result == FriendManager.SendResult.AUTO_ACCEPTED)
        {
            response.setSuccess(true);
        }
        else
        {
            response.setSuccess(false);
            response.setErrorText(describeFriendSendFailure(result));
        }
        return response;
    }

    private String describeFriendSendFailure(FriendManager.SendResult result)
    {
        if (result == FriendManager.SendResult.NO_SUCH_USER) return "No player with that username.";
        if (result == FriendManager.SendResult.SELF) return "You can't friend yourself.";
        if (result == FriendManager.SendResult.ALREADY_FRIENDS) return "You're already friends.";
        if (result == FriendManager.SendResult.ALREADY_PENDING) return "You've already sent a request to this player.";
        return "Something went wrong.";
    }

    private Message handleFriendAccept(Message request)
    {
        if (loggedInUsername != null)
        {
            friendManager.acceptRequest(loggedInUsername, request.getUsername());
        }
        return null;
    }

    private Message handleFriendDecline(Message request)
    {
        if (loggedInUsername != null)
        {
            friendManager.declineRequest(loggedInUsername, request.getUsername());
        }
        return null;
    }

    private Message handleFriendList()
    {
        Message response = new Message();
        response.setType(MessageType.FRIEND_LIST_RESPONSE);
        response.setSuccess(true);

        if (loggedInAccountId != null)
        {
            List<String> friends = friendManager.getFriendUsernames(loggedInAccountId);
            response.setFriendUsernames(friends);
            response.setPendingIncomingUsernames(friendManager.getPendingIncomingUsernames(loggedInAccountId));

            List<String> onlineNow = chatManager.getOnlineUsernames();
            List<String> onlineFriends = new ArrayList<String>();
            for (int i = 0; i < friends.size(); i++)
            {
                if (onlineNow.contains(friends.get(i)))
                {
                    onlineFriends.add(friends.get(i));
                }
            }
            response.setOnlineFriendUsernames(onlineFriends);
        }
        return response;
    }

    private Message handleTransactionHistory()
    {
        Message response = new Message();
        response.setType(MessageType.TRANSACTION_HISTORY_RESPONSE);
        response.setSuccess(true);

        if (loggedInAccountId != null)
        {
            response.setTransactionDescriptions(economyManager.getRecentTransactions(loggedInAccountId));
        }
        return response;
    }

    // ==================== Moderation ====================

    private Message handleModMute(Message request)
    {
        Message response = new Message();
        response.setType(MessageType.MOD_ACTION_RESPONSE);

        if (!isModeratorOrAdmin())
        {
            response.setSuccess(false);
            response.setErrorText("You don't have permission to do that.");
            return response;
        }

        int minutes = request.getMuteDurationMinutes() > 0 ? request.getMuteDurationMinutes() : 10;
        moderationManager.mute(request.getUsername(), minutes);

        ClientHandler target = chatManager.findByUsername(request.getUsername());
        if (target != null)
        {
            Message notice = new Message();
            notice.setType(MessageType.ERROR_NOTICE);
            notice.setErrorText("A moderator has muted you for " + minutes + " minutes.");
            target.sendMessage(notice);
        }

        response.setSuccess(true);
        return response;
    }

    private Message handleModUnmute(Message request)
    {
        Message response = new Message();
        response.setType(MessageType.MOD_ACTION_RESPONSE);

        if (!isModeratorOrAdmin())
        {
            response.setSuccess(false);
            response.setErrorText("You don't have permission to do that.");
            return response;
        }

        moderationManager.unmute(request.getUsername());
        response.setSuccess(true);
        return response;
    }

    private Message handleModKick(Message request)
    {
        Message response = new Message();
        response.setType(MessageType.MOD_ACTION_RESPONSE);

        if (!isModeratorOrAdmin())
        {
            response.setSuccess(false);
            response.setErrorText("You don't have permission to do that.");
            return response;
        }

        ClientHandler target = chatManager.findByUsername(request.getUsername());
        if (target == null)
        {
            response.setSuccess(false);
            response.setErrorText("That player isn't online.");
            return response;
        }

        target.forceDisconnect("You've been kicked by a moderator.");
        response.setSuccess(true);
        return response;
    }

    private Message handleModBan(Message request)
    {
        Message response = new Message();
        response.setType(MessageType.MOD_ACTION_RESPONSE);

        if (!isModeratorOrAdmin())
        {
            response.setSuccess(false);
            response.setErrorText("You don't have permission to do that.");
            return response;
        }

        moderationManager.ban(request.getUsername());

        ClientHandler target = chatManager.findByUsername(request.getUsername());
        if (target != null)
        {
            target.forceDisconnect("You've been banned from this server.");
        }

        response.setSuccess(true);
        return response;
    }

    private Message handleModUnban(Message request)
    {
        Message response = new Message();
        response.setType(MessageType.MOD_ACTION_RESPONSE);

        if (!isModeratorOrAdmin())
        {
            response.setSuccess(false);
            response.setErrorText("You don't have permission to do that.");
            return response;
        }

        moderationManager.unban(request.getUsername());
        response.setSuccess(true);
        return response;
    }

    private Message handleReportSubmit(Message request)
    {
        Message response = new Message();
        response.setType(MessageType.REPORT_SUBMIT_RESPONSE);

        if (loggedInUsername == null)
        {
            response.setSuccess(false);
            response.setErrorText("Not logged in.");
            return response;
        }
        if (request.getUsername() == null || request.getUsername().trim().isEmpty())
        {
            response.setSuccess(false);
            response.setErrorText("Who are you reporting?");
            return response;
        }

        moderationManager.submitReport(loggedInUsername, request.getUsername(), request.getReportReason());
        response.setSuccess(true);
        return response;
    }

    private Message handleReportList()
    {
        Message response = new Message();
        response.setType(MessageType.REPORT_LIST_RESPONSE);

        if (!isModeratorOrAdmin())
        {
            response.setSuccess(false);
            response.setErrorText("You don't have permission to view this.");
            return response;
        }

        response.setSuccess(true);
        response.setReportDescriptions(moderationManager.getUnresolvedReportDescriptions());
        return response;
    }

    private Message handleReportResolve(Message request)
    {
        Message response = new Message();
        response.setType(MessageType.MOD_ACTION_RESPONSE);

        if (!isModeratorOrAdmin())
        {
            response.setSuccess(false);
            response.setErrorText("You don't have permission to do that.");
            return response;
        }

        boolean resolved = moderationManager.resolveReport(request.getUsername());
        response.setSuccess(resolved);
        if (!resolved)
        {
            response.setErrorText("Couldn't find that report.");
        }
        return response;
    }

    // ==================== Racing (online, same-seed) ====================

    private Message handleRaceFindMatch()
    {
        if (loggedInUsername != null) racingMatchManager.findMatch(this);
        return null;
    }

    private Message handleRaceLeaveQueue()
    {
        racingMatchManager.cancelWaiting(this);
        if (currentRacingMatch != null)
        {
            currentRacingMatch.handleDisconnect(this);
            currentRacingMatch = null;
        }
        return null;
    }

    private Message handleRaceFinished(Message request)
    {
        if (currentRacingMatch != null)
        {
            currentRacingMatch.reportFinished(this, request.isRaceFinished(), request.getScore());
        }
        return null;
    }

    // ==================== Among Us ====================

    private Message handleAmongFindMatch()
    {
        if (loggedInUsername != null) amongUsMatchManager.findMatch(this);
        return null;
    }

    private Message handleAmongLeaveQueue()
    {
        amongUsMatchManager.cancelWaiting(this);
        if (currentAmongMatch != null)
        {
            currentAmongMatch.handleDisconnect(this);
            currentAmongMatch = null;
        }
        return null;
    }

    private Message handleAmongTaskComplete(Message request)
    {
        if (currentAmongMatch != null)
        {
            currentAmongMatch.completeTask(this, request.getAmongTaskIndex());
        }
        return null;
    }

    /** Kill target reuses getToUsername() rather than adding a dedicated field. */
    private Message handleAmongKill(Message request)
    {
        if (currentAmongMatch != null)
        {
            currentAmongMatch.attemptKill(this, request.getToUsername());
        }
        return null;
    }

    private Message handleAmongCallMeeting()
    {
        if (currentAmongMatch != null)
        {
            currentAmongMatch.callMeeting(this);
        }
        return null;
    }

    /** Vote target reuses getToUsername() too - null/empty means "skip". */
    private Message handleAmongVote(Message request)
    {
        if (currentAmongMatch != null)
        {
            currentAmongMatch.castVote(this, request.getToUsername());
        }
        return null;
    }

    // ==================== Fight Arena ====================

    private Message handleFightFindMatch(Message request)
    {
        if (loggedInUsername != null)
        {
            fightArenaMatchManager.findMatch(this, request.getFightMode());
        }
        return null;
    }

    private Message handleFightLeaveQueue()
    {
        fightArenaMatchManager.cancelWaiting(this);
        if (currentFightMatch != null)
        {
            currentFightMatch.handleDisconnect(this);
            currentFightMatch = null;
        }
        return null;
    }

    private Message handleFightInputUpdate(Message request)
    {
        if (currentFightMatch != null)
        {
            currentFightMatch.updateInput(this, request.isFightMovingLeft(),
                request.isFightMovingRight(), request.isFightAttacking());
        }
        return null;
    }

    // ==================== Game invites ====================

    /** Fire-and-forget relay, no persistence - matches every other DM-style message in Vertex. Silently drops if the target isn't online. */
    private Message handleGameInvite(Message request)
    {
        if (loggedInUsername == null || request.getToUsername() == null)
        {
            return null;
        }

        ClientHandler target = chatManager.findByUsername(request.getToUsername());
        if (target != null)
        {
            Message notice = new Message();
            notice.setType(MessageType.GAME_INVITE);
            notice.setUsername(loggedInUsername);
            notice.setGameId(request.getGameId());
            target.sendMessage(notice);
        }
        return null;
    }

    // ==================== Chess ====================

    private Message handleChessFindMatch()
    {
        if (loggedInUsername != null) chessMatchManager.findMatch(this);
        return null;
    }

    private Message handleChessLeaveQueue()
    {
        chessMatchManager.cancelWaiting(this);
        if (currentChessMatch != null)
        {
            currentChessMatch.handleDisconnect(this);
            currentChessMatch = null;
        }
        return null;
    }

    private Message handleChessMove(Message request)
    {
        if (currentChessMatch != null)
        {
            currentChessMatch.makeMove(this, request.getCellIndex(), request.getChessToSquare());
        }
        return null;
    }

    private Message handleChessResign()
    {
        if (currentChessMatch != null)
        {
            currentChessMatch.resign(this);
        }
        return null;
    }

    private Message handleChessDrawOffer()
    {
        if (currentChessMatch != null)
        {
            currentChessMatch.offerDraw(this);
        }
        return null;
    }

    private Message handleChessDrawResponse(Message request)
    {
        if (currentChessMatch != null)
        {
            currentChessMatch.respondToDraw(this, request.isSuccess());
        }
        return null;
    }

    // ==================== Battleship ====================

    private Message handleBattleshipFindMatch()
    {
        if (loggedInUsername != null) battleshipMatchManager.findMatch(this);
        return null;
    }

    private Message handleBattleshipLeaveQueue()
    {
        battleshipMatchManager.cancelWaiting(this);
        if (currentBattleshipMatch != null)
        {
            currentBattleshipMatch.handleDisconnect(this);
            currentBattleshipMatch = null;
        }
        return null;
    }

    private Message handleBattleshipFire(Message request)
    {
        if (currentBattleshipMatch != null)
        {
            currentBattleshipMatch.fire(this, request.getCellIndex());
        }
        return null;
    }

    // ==================== Rock Paper Scissors ====================

    private Message handleRpsFindMatch()
    {
        if (loggedInUsername != null) rpsMatchManager.findMatch(this);
        return null;
    }

    private Message handleRpsLeaveQueue()
    {
        rpsMatchManager.cancelWaiting(this);
        if (currentRpsMatch != null)
        {
            currentRpsMatch.handleDisconnect(this);
            currentRpsMatch = null;
        }
        return null;
    }

    private Message handleRpsMove(Message request)
    {
        if (currentRpsMatch != null)
        {
            currentRpsMatch.submitMove(this, request.getRpsMove());
        }
        return null;
    }

    // ==================== Leaderboards ====================

    private static final java.util.Set<String> RATED_GAMES = new java.util.HashSet<String>(java.util.Arrays.asList(
        "tictactoe-online", "chess", "battleship", "rock-paper-scissors", "fight-arena"));

    private Message handleLeaderboardRequest(Message request)
    {
        Message response = new Message();
        response.setType(MessageType.LEADERBOARD_RESPONSE);
        response.setGameId(request.getGameId());

        String gameId = request.getGameId();
        boolean rated = RATED_GAMES.contains(gameId);
        int accountId = loggedInAccountId != null ? loggedInAccountId : -1;

        List<String> entries = rated
            ? leaderboardManager.getRatingLeaderboard(gameId, 20)
            : leaderboardManager.getScoreLeaderboard(gameId, 20);

        response.setLeaderboardEntries(entries);
        response.setMyRating(rated ? leaderboardManager.getRating(gameId, accountId) : leaderboardManager.getBestScore(gameId, accountId));
        response.setMyRank(computeMyRank(entries));
        return response;
    }

    private int computeMyRank(List<String> entries)
    {
        if (loggedInUsername == null)
        {
            return 0;
        }
        for (int i = 0; i < entries.size(); i++)
        {
            String[] parts = entries.get(i).split("\\|", -1);
            if (parts.length >= 2 && parts[1].equalsIgnoreCase(loggedInUsername))
            {
                return Integer.parseInt(parts[0]);
            }
        }
        return 0;
    }

    // ==================== Party system ====================

    private Message handlePartyCreate()
    {
        if (loggedInUsername != null)
        {
            partyManager.createParty(this);
        }
        return null;
    }

    private Message handlePartyInvite(Message request)
    {
        partyManager.invite(this, request.getToUsername());
        return null;
    }

    private Message handlePartyJoinByCode(Message request)
    {
        partyManager.joinByCode(this, request.getPartyCode());
        return null;
    }

    private Message handlePartyLeave()
    {
        partyManager.leave(this);
        return null;
    }

    private Message handlePartyKick(Message request)
    {
        partyManager.kick(this, request.getToUsername());
        return null;
    }

    // ==================== Achievements ====================

    private Message handleAchievementsRequest()
    {
        Message response = new Message();
        response.setType(MessageType.ACHIEVEMENTS_RESPONSE);
        if (loggedInAccountId != null)
        {
            response.setUnlockedAchievementIds(new java.util.ArrayList<String>(achievementManager.getUnlocked(loggedInAccountId)));
        }
        else
        {
            response.setUnlockedAchievementIds(new java.util.ArrayList<String>());
        }
        return response;
    }

    // ==================== Tournaments ====================

    private Message handleTournamentCreate(Message request)
    {
        if (loggedInUsername != null)
        {
            tournamentManager.create(this, request.getGameId());
        }
        return null;
    }

    private Message handleTournamentJoin(Message request)
    {
        if (loggedInUsername != null)
        {
            tournamentManager.join(this, request.getTournamentId());
        }
        return null;
    }

    private Message handleTournamentList()
    {
        Message response = new Message();
        response.setType(MessageType.TOURNAMENT_LIST_RESPONSE);
        response.setTournamentEntries(tournamentManager.listOpen());
        return response;
    }

    private Message handleTeamTournamentCreate(Message request)
    {
        if (loggedInUsername != null)
        {
            teamTournamentManager.create(this, request.getGameId());
        }
        return null;
    }

    private Message handleTeamTournamentJoin(Message request)
    {
        if (loggedInUsername != null)
        {
            teamTournamentManager.join(this, request.getTournamentId());
        }
        return null;
    }

    private Message handleTeamTournamentList()
    {
        Message response = new Message();
        response.setType(MessageType.TEAM_TOURNAMENT_LIST_RESPONSE);
        response.setTournamentEntries(teamTournamentManager.listOpen());
        return response;
    }

    // ==================== Spectator mode ====================

    /** Chess only for now - same pattern would extend to other turn-based games later. */
    private Message handleSpectatableMatches(Message request)
    {
        Message response = new Message();
        response.setType(MessageType.SPECTATABLE_MATCHES_RESPONSE);
        response.setGameId(request.getGameId());

        if ("chess".equals(request.getGameId()))
        {
            response.setSpectatableMatches(chessMatchManager.listSpectatableMatches());
        }
        else if ("rock-paper-scissors".equals(request.getGameId()))
        {
            response.setSpectatableMatches(rpsMatchManager.listSpectatableMatches());
        }
        else if ("battleship".equals(request.getGameId()))
        {
            response.setSpectatableMatches(battleshipMatchManager.listSpectatableMatches());
        }
        else
        {
            response.setSpectatableMatches(new java.util.ArrayList<String>());
        }
        return response;
    }

    private Message handleSpectate(Message request)
    {
        if ("chess".equals(request.getGameId()))
        {
            chessMatchManager.addSpectator(this, request.getMatchId());
        }
        else if ("rock-paper-scissors".equals(request.getGameId()))
        {
            rpsMatchManager.addSpectator(this, request.getMatchId());
        }
        else if ("battleship".equals(request.getGameId()))
        {
            battleshipMatchManager.addSpectator(this, request.getMatchId());
        }
        return null;
    }

    // ==================== Match replay ====================

    private Message handleReplayList()
    {
        Message response = new Message();
        response.setType(MessageType.REPLAY_LIST_RESPONSE);
        response.setReplayEntries(loggedInUsername != null
            ? replayManager.listForPlayer(loggedInUsername)
            : new java.util.ArrayList<String>());
        return response;
    }

    private Message handleReplayFetch(Message request)
    {
        Message response = new Message();
        response.setType(MessageType.REPLAY_RESPONSE);
        response.setReplayId(request.getReplayId());

        ReplayManager.Replay replay = replayManager.get(request.getReplayId());
        response.setReplaySnapshots(replay != null ? replay.snapshots : new java.util.ArrayList<String>());
        return response;
    }

    // ==================== Rematch ====================

    /** Chess only for now, matching the current scope of direct-pairing (createDirectMatch). Fire-and-forget relay, same pattern as GAME_INVITE - if the opponent isn't online, nothing happens, no error surfaced. */
    private Message handleRematchRequest(Message request)
    {
        if (loggedInUsername == null || !isRematchableGame(request.getGameId()))
        {
            return null;
        }
        ClientHandler opponent = chatManager.findByUsername(request.getToUsername());
        if (opponent == null)
        {
            return null;
        }

        Message offer = new Message();
        offer.setType(MessageType.REMATCH_OFFERED);
        offer.setUsername(loggedInUsername);
        offer.setGameId(request.getGameId());
        opponent.sendMessage(offer);
        return null;
    }

    private Message handleRematchResponse(Message request)
    {
        if (!request.isSuccess() || !isRematchableGame(request.getGameId()))
        {
            return null;
        }
        ClientHandler requester = chatManager.findByUsername(request.getToUsername());
        if (requester == null || requester.getLoggedInUsername() == null || loggedInUsername == null)
        {
            return null;
        }

        String gameId = request.getGameId();
        if ("chess".equals(gameId))
        {
            chessMatchManager.createDirectMatch(requester, this);
        }
        else if ("rock-paper-scissors".equals(gameId))
        {
            rpsMatchManager.createDirectMatch(requester, this);
        }
        else if ("battleship".equals(gameId))
        {
            battleshipMatchManager.createDirectMatch(requester, this);
        }
        return null;
    }

    private boolean isRematchableGame(String gameId)
    {
        return "chess".equals(gameId) || "rock-paper-scissors".equals(gameId) || "battleship".equals(gameId);
    }
}
