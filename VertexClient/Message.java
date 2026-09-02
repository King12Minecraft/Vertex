import java.io.Serializable;
import java.util.List;

/**
 * Message
 * -------
 * SHARED (Common) class - identical copy lives in both VertexClient
 * and VertexServer. A single request or response sent over the socket
 * between them. Kept as one general-purpose class with a few optional
 * fields (rather than one class per message type) to keep the protocol
 * simple while it only covers login/create-account/game-list - revisit
 * if it grows unwieldy once chat/economy/etc. add their own message
 * types.
 */
public class Message implements Serializable
{
    private static final long serialVersionUID = 1L;

    private MessageType type;
    private String username;
    private String password;
    private boolean success;
    private String errorText;
    private Account account;
    private boolean bootstrapAdmin;
    private List<GameInfo> gameList;
    private String newUsername;
    private String currentPassword;
    private String newPassword;

    // --- Phase 8: Multiplayer match fields ---
    private String matchId;
    private int cellIndex;
    private String boardState;
    private String symbol;
    private String opponentUsername;
    private String matchResult;

    // --- Phase 9: General Chat field. Sender identity reuses "username". ---
    private String chatText;

    // --- Phase 9 continued: Private messages & group chats ---
    private String toUsername;
    private String groupId;
    private String groupName;
    private java.util.List<String> memberUsernames;

    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getErrorText() { return errorText; }
    public void setErrorText(String errorText) { this.errorText = errorText; }

    public Account getAccount() { return account; }
    public void setAccount(Account account) { this.account = account; }

    /** True if this CREATE_ACCOUNT_RESPONSE granted ADMIN via the first-account bootstrap. */
    public boolean isBootstrapAdmin() { return bootstrapAdmin; }
    public void setBootstrapAdmin(boolean bootstrapAdmin) { this.bootstrapAdmin = bootstrapAdmin; }

    public List<GameInfo> getGameList() { return gameList; }
    public void setGameList(List<GameInfo> gameList) { this.gameList = gameList; }

    public String getNewUsername() { return newUsername; }
    public void setNewUsername(String newUsername) { this.newUsername = newUsername; }

    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }

    public String getMatchId() { return matchId; }
    public void setMatchId(String matchId) { this.matchId = matchId; }

    public int getCellIndex() { return cellIndex; }
    public void setCellIndex(int cellIndex) { this.cellIndex = cellIndex; }

    public String getBoardState() { return boardState; }
    public void setBoardState(String boardState) { this.boardState = boardState; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getOpponentUsername() { return opponentUsername; }
    public void setOpponentUsername(String opponentUsername) { this.opponentUsername = opponentUsername; }

    /** "WIN", "LOSE", "DRAW", or "OPPONENT_LEFT" - set on MATCH_OVER. */
    public String getMatchResult() { return matchResult; }
    public void setMatchResult(String matchResult) { this.matchResult = matchResult; }

    /** Chat message body. Sender's username is carried in the "username" field. */
    public String getChatText() { return chatText; }
    public void setChatText(String chatText) { this.chatText = chatText; }

    /** DM recipient. On delivery, "username" is the sender - this stays the intended recipient either way. */
    public String getToUsername() { return toUsername; }
    public void setToUsername(String toUsername) { this.toUsername = toUsername; }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    /** Requested usernames when creating a group - only those currently online actually get added. */
    public java.util.List<String> getMemberUsernames() { return memberUsernames; }
    public void setMemberUsernames(java.util.List<String> memberUsernames) { this.memberUsernames = memberUsernames; }

    // ---- Economy: rewards, challenges, shop ----
    private int coins;
    private java.util.List<ShopItemInfo> shopItems;
    private String itemId;
    private java.util.List<ChallengeProgressInfo> challenges;

    public int getCoins() { return coins; }
    public void setCoins(int coins) { this.coins = coins; }

    public java.util.List<ShopItemInfo> getShopItems() { return shopItems; }
    public void setShopItems(java.util.List<ShopItemInfo> shopItems) { this.shopItems = shopItems; }

    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }

    public java.util.List<ChallengeProgressInfo> getChallenges() { return challenges; }
    public void setChallenges(java.util.List<ChallengeProgressInfo> challenges) { this.challenges = challenges; }

    // ---- Game history: recently played + trending ----
    private String gameId;
    private int score;
    private java.util.List<String> recentGameIds;
    private java.util.List<String> trendingGameIds;

    /** Which game was just played - used by GAME_PLAYED_REQUEST. */
    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }

    /** Final score achieved - used by GAME_PLAYED_REQUEST for score-based rewards (e.g. Snake). */
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public java.util.List<String> getRecentGameIds() { return recentGameIds; }
    public void setRecentGameIds(java.util.List<String> recentGameIds) { this.recentGameIds = recentGameIds; }

    public java.util.List<String> getTrendingGameIds() { return trendingGameIds; }
    public void setTrendingGameIds(java.util.List<String> trendingGameIds) { this.trendingGameIds = trendingGameIds; }

    // ---- Online users (group member picker) ----
    private java.util.List<String> onlineUsernames;

    public java.util.List<String> getOnlineUsernames() { return onlineUsernames; }
    public void setOnlineUsernames(java.util.List<String> onlineUsernames) { this.onlineUsernames = onlineUsernames; }

    // ---- Chat sender color/badge (for colored, badged usernames in chat) ----
    private String senderColorId;
    private String senderBadgeId;

    public String getSenderColorId() { return senderColorId; }
    public void setSenderColorId(String senderColorId) { this.senderColorId = senderColorId; }

    public String getSenderBadgeId() { return senderBadgeId; }
    public void setSenderBadgeId(String senderBadgeId) { this.senderBadgeId = senderBadgeId; }

    // ---- Chat file attachments - never persisted server-side, just relayed like any other message ----
    private byte[] fileData;
    private String fileName;

    public byte[] getFileData() { return fileData; }
    public void setFileData(byte[] fileData) { this.fileData = fileData; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    // ---- Tic-Tac-Toe: winning line for the strike-through overlay ----
    private int[] winningLine;

    /** The 3 winning cell indices (0-8), for drawing the actual strike-through line. Null on a draw. */
    public int[] getWinningLine() { return winningLine; }
    public void setWinningLine(int[] winningLine) { this.winningLine = winningLine; }

    // ---- Live matchmaking queue counts ----
    private String queueGameId;
    private int queueCount;

    public String getQueueGameId() { return queueGameId; }
    public void setQueueGameId(String queueGameId) { this.queueGameId = queueGameId; }

    public int getQueueCount() { return queueCount; }
    public void setQueueCount(int queueCount) { this.queueCount = queueCount; }

    // ---- Admin: online players + all-time registered players ----
    private java.util.List<String> allRegisteredUsernames;

    /** Every registered username, server-validated as moderator/admin-only before being sent. */
    public java.util.List<String> getAllRegisteredUsernames() { return allRegisteredUsernames; }
    public void setAllRegisteredUsernames(java.util.List<String> allRegisteredUsernames) { this.allRegisteredUsernames = allRegisteredUsernames; }

    // ---- Friends (Phase 10) ----
    private java.util.List<String> friendUsernames;
    private java.util.List<String> pendingIncomingUsernames;
    private java.util.List<String> onlineFriendUsernames;
    private boolean online;

    /** Accepted friends - FRIEND_LIST_RESPONSE. */
    public java.util.List<String> getFriendUsernames() { return friendUsernames; }
    public void setFriendUsernames(java.util.List<String> friendUsernames) { this.friendUsernames = friendUsernames; }

    /** People who've sent the requester a friend request, not yet accepted/declined. */
    public java.util.List<String> getPendingIncomingUsernames() { return pendingIncomingUsernames; }
    public void setPendingIncomingUsernames(java.util.List<String> pendingIncomingUsernames) { this.pendingIncomingUsernames = pendingIncomingUsernames; }

    /** Subset of friendUsernames who are currently online, as of FRIEND_LIST_RESPONSE. */
    public java.util.List<String> getOnlineFriendUsernames() { return onlineFriendUsernames; }
    public void setOnlineFriendUsernames(java.util.List<String> onlineFriendUsernames) { this.onlineFriendUsernames = onlineFriendUsernames; }

    /** Used on FRIEND_STATUS_UPDATE - whether the named friend (see username) just came online or went offline. */
    public boolean isOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }

    // ---- Daily login rewards (Phase 11) ----
    private int dailyRewardCoins;
    private int loginStreak;

    /** Coins awarded for today's login - 0 if already claimed today. Carried on LOGIN_RESPONSE/CREATE_ACCOUNT_RESPONSE. */
    public int getDailyRewardCoins() { return dailyRewardCoins; }
    public void setDailyRewardCoins(int dailyRewardCoins) { this.dailyRewardCoins = dailyRewardCoins; }

    /** Current consecutive-day login streak, alongside the reward. */
    public int getLoginStreak() { return loginStreak; }
    public void setLoginStreak(int loginStreak) { this.loginStreak = loginStreak; }

    // ---- Coin transaction history (Phase 11) ----
    private java.util.List<String> transactionDescriptions;

    /** Pre-formatted recent transaction lines, most recent first - TRANSACTION_HISTORY_RESPONSE. */
    public java.util.List<String> getTransactionDescriptions() { return transactionDescriptions; }
    public void setTransactionDescriptions(java.util.List<String> transactionDescriptions) { this.transactionDescriptions = transactionDescriptions; }

    // ---- Moderation (Phase 9/14) ----
    private int muteDurationMinutes;
    private String reportReason;
    private java.util.List<String> reportDescriptions;

    /** Minutes to mute for - MOD_MUTE_REQUEST. 0 or negative is rejected server-side. */
    public int getMuteDurationMinutes() { return muteDurationMinutes; }
    public void setMuteDurationMinutes(int muteDurationMinutes) { this.muteDurationMinutes = muteDurationMinutes; }

    /** Why a player is being reported, or the reason shown on a moderation error/notice. */
    public String getReportReason() { return reportReason; }
    public void setReportReason(String reportReason) { this.reportReason = reportReason; }

    /** Pre-formatted pending-report lines for the moderator queue - REPORT_LIST_RESPONSE. */
    public java.util.List<String> getReportDescriptions() { return reportDescriptions; }
    public void setReportDescriptions(java.util.List<String> reportDescriptions) { this.reportDescriptions = reportDescriptions; }

    // ---- Racing online (same-seed race, not live position sync - see RacingMatch) ----
    private long raceSeed;
    private java.util.List<String> raceRosterUsernames;
    private boolean raceFinished;
    private int racePlace;
    private int raceReward;

    /** Shared RNG seed so every racer gets the identical obstacle sequence - RACE_MATCH_FOUND. Comparing final results this way avoids needing to broadcast live positions between clients every frame. */
    public long getRaceSeed() { return raceSeed; }
    public void setRaceSeed(long raceSeed) { this.raceSeed = raceSeed; }

    /** Every racer's username in this match, including the recipient's own - RACE_MATCH_FOUND. */
    public java.util.List<String> getRaceRosterUsernames() { return raceRosterUsernames; }
    public void setRaceRosterUsernames(java.util.List<String> raceRosterUsernames) { this.raceRosterUsernames = raceRosterUsernames; }

    /** Did the sender cross the finish line, or crash first - RACE_FINISHED_REQUEST. Use getScore() alongside this: frame count reached (finish time) if finished, or frames survived if not. */
    public boolean isRaceFinished() { return raceFinished; }
    public void setRaceFinished(boolean raceFinished) { this.raceFinished = raceFinished; }

    /** 1-based final placement in the race - RACE_RESULT. */
    public int getRacePlace() { return racePlace; }
    public void setRacePlace(int racePlace) { this.racePlace = racePlace; }

    /** Coins awarded for this race's placement (0 outside the top 3) - RACE_RESULT. */
    public int getRaceReward() { return raceReward; }
    public void setRaceReward(int raceReward) { this.raceReward = raceReward; }

    // ---- Among Us (round-based social deduction - see AmongUsMatch for why this isn't live movement) ----
    private String amongRole;
    private java.util.List<String> amongTasks;
    private int amongTeamTaskProgress;
    private java.util.List<String> amongRosterUsernames;
    private java.util.List<String> amongAliveUsernames;
    private String amongMeetingReason;
    private String amongDeadUsername;
    private String amongEjectedUsername;
    private String amongEjectedRole;
    private String amongWinningTeam;
    private int amongTaskIndex;

    /** "IMPOSTOR" or "CREWMATE" - AMONG_MATCH_FOUND. Never sent to anyone but the player it belongs to. */
    public String getAmongRole() { return amongRole; }
    public void setAmongRole(String amongRole) { this.amongRole = amongRole; }

    /** This player's own task list - real tasks for a Crewmate, a decoy list for an Impostor (so they can blend in) - AMONG_MATCH_FOUND. */
    public java.util.List<String> getAmongTasks() { return amongTasks; }
    public void setAmongTasks(java.util.List<String> amongTasks) { this.amongTasks = amongTasks; }

    /** Team-wide Crewmate task completion, 0-100 - AMONG_STATE_UPDATE. */
    public int getAmongTeamTaskProgress() { return amongTeamTaskProgress; }
    public void setAmongTeamTaskProgress(int amongTeamTaskProgress) { this.amongTeamTaskProgress = amongTeamTaskProgress; }

    /** Every player in the match, including the recipient - AMONG_MATCH_FOUND. */
    public java.util.List<String> getAmongRosterUsernames() { return amongRosterUsernames; }
    public void setAmongRosterUsernames(java.util.List<String> amongRosterUsernames) { this.amongRosterUsernames = amongRosterUsernames; }

    /** Currently-alive players - AMONG_STATE_UPDATE, AMONG_MEETING_START, AMONG_MEETING_RESULT. */
    public java.util.List<String> getAmongAliveUsernames() { return amongAliveUsernames; }
    public void setAmongAliveUsernames(java.util.List<String> amongAliveUsernames) { this.amongAliveUsernames = amongAliveUsernames; }

    /** "BODY_FOUND" or "EMERGENCY" - AMONG_MEETING_START. */
    public String getAmongMeetingReason() { return amongMeetingReason; }
    public void setAmongMeetingReason(String amongMeetingReason) { this.amongMeetingReason = amongMeetingReason; }

    /** Who died, if this meeting was triggered by a kill (null for an emergency meeting) - AMONG_MEETING_START. */
    public String getAmongDeadUsername() { return amongDeadUsername; }
    public void setAmongDeadUsername(String amongDeadUsername) { this.amongDeadUsername = amongDeadUsername; }

    /** Who got voted out this meeting, or null if it was a tie/skip - AMONG_MEETING_RESULT. */
    public String getAmongEjectedUsername() { return amongEjectedUsername; }
    public void setAmongEjectedUsername(String amongEjectedUsername) { this.amongEjectedUsername = amongEjectedUsername; }

    /** The ejected player's revealed role - AMONG_MEETING_RESULT. */
    public String getAmongEjectedRole() { return amongEjectedRole; }
    public void setAmongEjectedRole(String amongEjectedRole) { this.amongEjectedRole = amongEjectedRole; }

    /** "CREWMATES" or "IMPOSTORS" - AMONG_GAME_OVER. */
    public String getAmongWinningTeam() { return amongWinningTeam; }
    public void setAmongWinningTeam(String amongWinningTeam) { this.amongWinningTeam = amongWinningTeam; }

    /** Which of the sender's own tasks they're marking complete - AMONG_TASK_COMPLETE_REQUEST. Reuses getUsername()/getToUsername() for kill/vote targets rather than adding yet more fields. */
    public int getAmongTaskIndex() { return amongTaskIndex; }
    public void setAmongTaskIndex(int amongTaskIndex) { this.amongTaskIndex = amongTaskIndex; }

    // ---- Fight Arena (real-time synced brawler - see FightMatch for the tick loop) ----
    private String fightMode;
    private java.util.List<String> fightRosterUsernames;
    private java.util.List<String> fightTeamAssignments;
    private boolean fightMovingLeft;
    private boolean fightMovingRight;
    private boolean fightAttacking;
    private java.util.List<String> fightTickData;
    private java.util.List<String> fightScores;
    private String fightResultText;

    /** "1V1"/"2V2"/"3V3"/"FFA" - which queue to join (FIGHT_FIND_MATCH_REQUEST) or which mode this match is (FIGHT_MATCH_FOUND). Each mode has its own separate matchmaking queue. */
    public String getFightMode() { return fightMode; }
    public void setFightMode(String fightMode) { this.fightMode = fightMode; }

    /** Every player in the match - FIGHT_MATCH_FOUND. */
    public java.util.List<String> getFightRosterUsernames() { return fightRosterUsernames; }
    public void setFightRosterUsernames(java.util.List<String> fightRosterUsernames) { this.fightRosterUsernames = fightRosterUsernames; }

    /** "username|teamIndex" pairs, assigned once at match start - FIGHT_MATCH_FOUND. FFA gives every player a unique team index (no allies); team modes use 0/1. */
    public java.util.List<String> getFightTeamAssignments() { return fightTeamAssignments; }
    public void setFightTeamAssignments(java.util.List<String> fightTeamAssignments) { this.fightTeamAssignments = fightTeamAssignments; }

    /** Current held-key state, sent whenever it changes (not every frame) - FIGHT_INPUT_UPDATE. */
    public boolean isFightMovingLeft() { return fightMovingLeft; }
    public void setFightMovingLeft(boolean fightMovingLeft) { this.fightMovingLeft = fightMovingLeft; }
    public boolean isFightMovingRight() { return fightMovingRight; }
    public void setFightMovingRight(boolean fightMovingRight) { this.fightMovingRight = fightMovingRight; }
    public boolean isFightAttacking() { return fightAttacking; }
    public void setFightAttacking(boolean fightAttacking) { this.fightAttacking = fightAttacking; }

    /** One entry per player each server tick, "username|x|health|facingRight|attackFlash|alive" - FIGHT_TICK_UPDATE. Client renders directly from this, no local physics prediction. */
    public java.util.List<String> getFightTickData() { return fightTickData; }
    public void setFightTickData(java.util.List<String> fightTickData) { this.fightTickData = fightTickData; }

    /** "username|koCount" pairs - FIGHT_TICK_UPDATE. */
    public java.util.List<String> getFightScores() { return fightScores; }
    public void setFightScores(java.util.List<String> fightScores) { this.fightScores = fightScores; }

    /** Pre-formatted final outcome - FIGHT_MATCH_OVER. */
    public String getFightResultText() { return fightResultText; }
    public void setFightResultText(String fightResultText) { this.fightResultText = fightResultText; }

    // ---- Chess (1v1 turn-based) ----
    private int chessToSquare;

    /** The move's destination square (0-63, row*8+col) - CHESS_MOVE_REQUEST. Reuses getCellIndex() for the "from" square, boardState/symbol/matchResult/opponentUsername/matchId for everything else, same as Tic-Tac-Toe. */
    public int getChessToSquare() { return chessToSquare; }
    public void setChessToSquare(int chessToSquare) { this.chessToSquare = chessToSquare; }

    // ---- Battleship (1v1, auto-placed fleets - see BattleshipMatch) ----
    private String battleshipResult;
    private String battleshipSunkShip;

    /** "HIT", "MISS", or "SUNK" - BATTLESHIP_FIRE_RESULT. Reuses getCellIndex() for which cell was fired at, getUsername() for who fired, boardState for the sender's own 100-char fleet layout at match start ('.'=water, a letter per ship type), and symbol for whose turn is next. */
    public String getBattleshipResult() { return battleshipResult; }
    public void setBattleshipResult(String battleshipResult) { this.battleshipResult = battleshipResult; }

    /** Which ship name was just sunk, if battleshipResult is "SUNK" - BATTLESHIP_FIRE_RESULT. */
    public String getBattleshipSunkShip() { return battleshipSunkShip; }
    public void setBattleshipSunkShip(String battleshipSunkShip) { this.battleshipSunkShip = battleshipSunkShip; }

    // ---- Rock Paper Scissors (1v1, simultaneous moves, best-of-5) ----
    private String rpsMove;
    private String rpsOpponentMove;
    private int rpsMyScore;
    private int rpsOpponentScore;

    /** The sender's chosen move ("Rock"/"Paper"/"Scissors") - RPS_MOVE_REQUEST when sent by the client. Reused as "what I actually picked" when echoed back in RPS_ROUND_RESULT. */
    public String getRpsMove() { return rpsMove; }
    public void setRpsMove(String rpsMove) { this.rpsMove = rpsMove; }

    /** The opponent's move, revealed once both sides have submitted - RPS_ROUND_RESULT. */
    public String getRpsOpponentMove() { return rpsOpponentMove; }
    public void setRpsOpponentMove(String rpsOpponentMove) { this.rpsOpponentMove = rpsOpponentMove; }

    /** Running series score after this round - RPS_ROUND_RESULT. Reuses getMatchResult() for this round's own WIN/LOSE/DRAW outcome, and again for the final series result in RPS_MATCH_OVER. */
    public int getRpsMyScore() { return rpsMyScore; }
    public void setRpsMyScore(int rpsMyScore) { this.rpsMyScore = rpsMyScore; }
    public int getRpsOpponentScore() { return rpsOpponentScore; }
    public void setRpsOpponentScore(int rpsOpponentScore) { this.rpsOpponentScore = rpsOpponentScore; }

    // ---- Leaderboards (ELO for 1v1/Fight Arena, best-score for score-based games) ----
    private java.util.List<String> leaderboardEntries;
    private int myRank;
    private int myRating;

    /** Which game's leaderboard to fetch (reuses getGameId()) - LEADERBOARD_REQUEST. */
    public java.util.List<String> getLeaderboardEntries() { return leaderboardEntries; }
    public void setLeaderboardEntries(java.util.List<String> leaderboardEntries) { this.leaderboardEntries = leaderboardEntries; }

    /** The requester's own rank on this leaderboard, 0 if unranked (never played) - LEADERBOARD_RESPONSE. */
    public int getMyRank() { return myRank; }
    public void setMyRank(int myRank) { this.myRank = myRank; }

    /** The requester's own current rating (for ELO games) or best score (for score-based games) - LEADERBOARD_RESPONSE. */
    public int getMyRating() { return myRating; }
    public void setMyRating(int myRating) { this.myRating = myRating; }

    // ---- Party system (invite + shareable code) ----
    private String partyCode;
    private java.util.List<String> partyMembers;
    private String partyLeader;

    /** The party's shareable join code - PARTY_CREATED, PARTY_JOIN_BY_CODE_REQUEST. */
    public String getPartyCode() { return partyCode; }
    public void setPartyCode(String partyCode) { this.partyCode = partyCode; }

    /** Every current member's username - PARTY_UPDATE. Reuses getToUsername() for invite/kick targets and getUsername() for "who invited you" rather than adding more fields. */
    public java.util.List<String> getPartyMembers() { return partyMembers; }
    public void setPartyMembers(java.util.List<String> partyMembers) { this.partyMembers = partyMembers; }

    /** Who's currently the party leader - PARTY_UPDATE. */
    public String getPartyLeader() { return partyLeader; }
    public void setPartyLeader(String partyLeader) { this.partyLeader = partyLeader; }

    // ---- Achievements ----
    private String achievementId;

    /** Which achievement was just unlocked - ACHIEVEMENT_UNLOCKED. Reuses getUsername() for the achievement's display name and getErrorText() for its description, rather than adding two more fields for a message that's otherwise this simple. */
    public String getAchievementId() { return achievementId; }
    public void setAchievementId(String achievementId) { this.achievementId = achievementId; }

    /** Every achievement ID the requester has unlocked - ACHIEVEMENTS_RESPONSE. The client already has the full definitions list built in, so only the unlocked set needs to travel over the wire. */
    private java.util.List<String> unlockedAchievementIds;
    public java.util.List<String> getUnlockedAchievementIds() { return unlockedAchievementIds; }
    public void setUnlockedAchievementIds(java.util.List<String> unlockedAchievementIds) { this.unlockedAchievementIds = unlockedAchievementIds; }

    // ---- Tournaments (4-player single-elimination brackets) ----
    private String tournamentId;
    private java.util.List<String> tournamentEntries;

    /** Which tournament - TOURNAMENT_JOIN_REQUEST. Reuses getGameId() for which game to create a tournament for, and getUsername() for the champion's name in TOURNAMENT_COMPLETE. */
    public String getTournamentId() { return tournamentId; }
    public void setTournamentId(String tournamentId) { this.tournamentId = tournamentId; }

    /** Every open/in-progress tournament, "id|gameId|status|playerCount|championName" per entry - TOURNAMENT_LIST_RESPONSE. */
    public java.util.List<String> getTournamentEntries() { return tournamentEntries; }
    public void setTournamentEntries(java.util.List<String> tournamentEntries) { this.tournamentEntries = tournamentEntries; }

    // ---- Spectator mode ----
    private java.util.List<String> spectatableMatches;

    /** Every currently-live match a spectator could join, "matchId|player1|player2" per entry - SPECTATABLE_MATCHES_RESPONSE. Reuses getMatchId() for which match to actually join in SPECTATE_REQUEST. */
    public java.util.List<String> getSpectatableMatches() { return spectatableMatches; }
    public void setSpectatableMatches(java.util.List<String> spectatableMatches) { this.spectatableMatches = spectatableMatches; }

    // ---- Match replay ----
    private java.util.List<String> replayEntries;
    private java.util.List<String> replaySnapshots;

    /** The requester's own past matches, "replayId|gameId|opponent|result|timestamp" per entry - REPLAY_LIST_RESPONSE. Reuses getReplayId() (below) for which one to actually open in REPLAY_REQUEST. */
    public java.util.List<String> getReplayEntries() { return replayEntries; }
    public void setReplayEntries(java.util.List<String> replayEntries) { this.replayEntries = replayEntries; }

    /** Every board snapshot in order, starting position first - REPLAY_RESPONSE. */
    public java.util.List<String> getReplaySnapshots() { return replaySnapshots; }
    public void setReplaySnapshots(java.util.List<String> replaySnapshots) { this.replaySnapshots = replaySnapshots; }

    private String replayId;
    public String getReplayId() { return replayId; }
    public void setReplayId(String replayId) { this.replayId = replayId; }

    // ---- Main-server sync (satellite server <-> main server, not client-facing) ----
    private Account syncAccount;
    private java.util.List<String> syncRatings;

    /** The canonical Account, as known by whichever end sent this - SYNC_AUTH_RESPONSE (main -> satellite, on successful delegated login) and SYNC_PUSH_REQUEST (satellite -> main, pushing whatever changed locally). */
    public Account getSyncAccount() { return syncAccount; }
    public void setSyncAccount(Account syncAccount) { this.syncAccount = syncAccount; }

    /** That account's rating in every game it's rated in, "gameId:rating" per entry - carried alongside syncAccount in both directions, since ELO lives in LeaderboardManager separately from the Account object itself. */
    public java.util.List<String> getSyncRatings() { return syncRatings; }
    public void setSyncRatings(java.util.List<String> syncRatings) { this.syncRatings = syncRatings; }

    // ---- Satellite registry (admin-visible list of known satellite servers) ----
    private int satellitePort;
    private java.util.List<String> satelliteList;

    /** The satellite's own listening port, self-reported on SATELLITE_REGISTER_REQUEST - main captures the satellite's IP automatically from the socket, but has no way to know which port that satellite's OWN players actually connect to without being told directly. */
    public int getSatellitePort() { return satellitePort; }
    public void setSatellitePort(int satellitePort) { this.satellitePort = satellitePort; }

    /** Every known satellite, "host:port|lastSeenEpochMillis" per entry - SATELLITE_LIST_RESPONSE, admin-only. */
    public java.util.List<String> getSatelliteList() { return satelliteList; }
    public void setSatelliteList(java.util.List<String> satelliteList) { this.satelliteList = satelliteList; }

    // ---- Cross-server friend presence (PRESENCE_UPDATE, FRIEND_LOCATION_REQUEST/RESPONSE) ----
    private String presenceAddress;

    /** On PRESENCE_UPDATE (satellite -> main): the reporting server's own "host:port", paired with username+isOnline to say "this account just came online/offline HERE". On FRIEND_LOCATION_RESPONSE (main -> whoever asked): the friend's current server address, or null if they're not online anywhere main knows about. */
    public String getPresenceAddress() { return presenceAddress; }
    public void setPresenceAddress(String presenceAddress) { this.presenceAddress = presenceAddress; }
}
