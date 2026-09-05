/**
 * MessageType
 * -----------
 * SHARED (Common) class - identical copy lives in both VertexClient
 * and VertexServer. The type of a Message being sent over the socket.
 */
public enum MessageType
{
    LOGIN_REQUEST,
    LOGIN_RESPONSE,
    CREATE_ACCOUNT_REQUEST,
    CREATE_ACCOUNT_RESPONSE,
    GAME_LIST_REQUEST,
    GAME_LIST_RESPONSE,
    CHANGE_USERNAME_REQUEST,
    CHANGE_USERNAME_RESPONSE,
    CHANGE_PASSWORD_REQUEST,
    CHANGE_PASSWORD_RESPONSE,

    // --- Phase 8: Multiplayer. These are PUSH-capable - the server can
    // send MATCH_FOUND/MATCH_UPDATE/MATCH_OVER at any time, not just as
    // a direct reply to a request. See NetworkManager's listener thread. ---
    FIND_MATCH_REQUEST,
    MATCH_FOUND,
    MAKE_MOVE_REQUEST,
    MOVE_REJECTED,
    MATCH_UPDATE,
    MATCH_OVER,
    LEAVE_MATCH_REQUEST,

    // --- Phase 9: General Chat. Also push-capable - broadcast to every
    // logged-in client, not just a direct reply. ---
    CHAT_MESSAGE,

    // --- Phase 9 continued: Private messages & group chats ---
    PRIVATE_MESSAGE,
    GROUP_CREATE_REQUEST,
    GROUP_CREATE_RESPONSE,
    GROUP_MESSAGE,
    GROUP_ADDED,

    // --- Economy: rewards, challenges, shop ---
    WALLET_UPDATE,
    SHOP_ITEMS_REQUEST,
    SHOP_ITEMS_RESPONSE,
    PURCHASE_REQUEST,
    PURCHASE_RESPONSE,
    CHALLENGES_REQUEST,
    CHALLENGES_RESPONSE,
    CHALLENGE_UPDATE,

    // --- Game history: recently played + trending, for the Games page ---
    GAME_PLAYED_REQUEST,
    GAME_HISTORY_REQUEST,
    GAME_HISTORY_RESPONSE,

    // --- Online user list (for the group member checkbox picker) ---
    ONLINE_USERS_REQUEST,
    ONLINE_USERS_RESPONSE,

    // --- Player color selection ---
    SELECT_COLOR_REQUEST,
    SELECT_COLOR_RESPONSE,
    SELECT_BADGE_REQUEST,
    SELECT_BADGE_RESPONSE,

    // --- Live matchmaking queue counts ---
    QUEUE_UPDATE,

    // --- Admin: online players + all-time registered players ---
    ADMIN_PLAYER_LIST_REQUEST,
    ADMIN_PLAYER_LIST_RESPONSE,

    // --- Friends (Phase 10) ---
    FRIEND_REQUEST_SEND,
    FRIEND_REQUEST_SEND_RESPONSE,
    FRIEND_REQUEST_RECEIVED,
    FRIEND_ACCEPT_REQUEST,
    FRIEND_DECLINE_REQUEST,
    FRIEND_ACCEPTED_NOTICE,
    FRIEND_LIST_REQUEST,
    FRIEND_LIST_RESPONSE,
    FRIEND_STATUS_UPDATE,

    // --- Coin transaction history (Phase 11) ---
    TRANSACTION_HISTORY_REQUEST,
    TRANSACTION_HISTORY_RESPONSE,

    // --- Moderation (Phase 9/14) ---
    MOD_MUTE_REQUEST,
    MOD_UNMUTE_REQUEST,
    MOD_KICK_REQUEST,
    MOD_BAN_REQUEST,
    MOD_UNBAN_REQUEST,
    MOD_ACTION_RESPONSE,
    REPORT_SUBMIT_REQUEST,
    REPORT_SUBMIT_RESPONSE,
    REPORT_LIST_REQUEST,
    REPORT_LIST_RESPONSE,
    REPORT_RESOLVE_REQUEST,
    ERROR_NOTICE,
    FORCE_DISCONNECT_NOTICE,

    // --- Bug reports & suggestions (see FeedbackManager) - a txt file admins
    // (every submission) and the person who sent it (just their own) can
    // both view in-app, not just player-conduct reports. ---
    FEEDBACK_SUBMIT_REQUEST,
    FEEDBACK_SUBMIT_RESPONSE,
    FEEDBACK_LIST_REQUEST,
    FEEDBACK_LIST_RESPONSE,

    // --- Racing online (1v1, same-seed race - see RacingMatch) ---
    RACE_FIND_MATCH_REQUEST,
    RACE_LEAVE_QUEUE_REQUEST,
    RACE_MATCH_FOUND,
    RACE_FINISHED_REQUEST,
    RACE_RESULT,

    // --- Zombie Survival (same-seed wave shooter - see ZombieSurvivalMatch) ---
    ZOMBIE_FIND_MATCH_REQUEST,
    ZOMBIE_LEAVE_QUEUE_REQUEST,
    ZOMBIE_MATCH_FOUND,
    ZOMBIE_FINISHED_REQUEST,
    ZOMBIE_RESULT,

    // --- Space Battle (same-seed arcade dogfight, ranked by score - see SpaceBattleMatch) ---
    SPACE_FIND_MATCH_REQUEST,
    SPACE_LEAVE_QUEUE_REQUEST,
    SPACE_MATCH_FOUND,
    SPACE_FINISHED_REQUEST,
    SPACE_RESULT,

    // --- Among Us (round-based social deduction, not live movement - see AmongUsMatch) ---
    AMONG_FIND_MATCH_REQUEST,
    AMONG_LEAVE_QUEUE_REQUEST,
    AMONG_MATCH_FOUND,
    AMONG_TASK_COMPLETE_REQUEST,
    AMONG_KILL_REQUEST,
    AMONG_CALL_MEETING_REQUEST,
    AMONG_MEETING_START,
    AMONG_VOTE_REQUEST,
    AMONG_MEETING_RESULT,
    AMONG_GAME_OVER,
    AMONG_STATE_UPDATE,

    // --- Fight Arena (real-time synced brawler - 1v1/2v2/3v3/Chaos FFA) ---
    FIGHT_FIND_MATCH_REQUEST,
    FIGHT_LEAVE_QUEUE_REQUEST,
    FIGHT_MATCH_FOUND,
    FIGHT_INPUT_UPDATE,
    FIGHT_TICK_UPDATE,
    FIGHT_MATCH_OVER,

    // --- Game invites (Friends/Group Chat) ---
    GAME_INVITE,

    // --- Chess (1v1 turn-based, standard rules minus castling/en passant) ---
    CHESS_FIND_MATCH_REQUEST,
    CHESS_LEAVE_QUEUE_REQUEST,
    CHESS_MATCH_FOUND,
    CHESS_MOVE_REQUEST,
    CHESS_MOVE_REJECTED,
    CHESS_UPDATE,
    CHESS_MATCH_OVER,
    CHESS_RESIGN_REQUEST,
    CHESS_DRAW_OFFER_REQUEST,
    CHESS_DRAW_OFFERED,
    CHESS_DRAW_RESPONSE_REQUEST,
    CHESS_DRAW_DECLINED,
    REMATCH_REQUEST,
    REMATCH_OFFERED,
    REMATCH_RESPONSE,

    // --- Battleship (1v1, auto-placed fleets) ---
    BATTLESHIP_FIND_MATCH_REQUEST,
    BATTLESHIP_LEAVE_QUEUE_REQUEST,
    BATTLESHIP_MATCH_FOUND,
    BATTLESHIP_FIRE_REQUEST,
    BATTLESHIP_FIRE_RESULT,
    BATTLESHIP_MATCH_OVER,

    // --- Rock Paper Scissors (1v1, simultaneous moves, best-of-5) ---
    RPS_FIND_MATCH_REQUEST,
    RPS_LEAVE_QUEUE_REQUEST,
    RPS_MATCH_FOUND,
    RPS_MOVE_REQUEST,
    RPS_ROUND_RESULT,
    RPS_MATCH_OVER,

    // --- Leaderboards ---
    LEADERBOARD_REQUEST,
    LEADERBOARD_RESPONSE,

    // --- Party system (invite + shareable code) ---
    PARTY_CREATE_REQUEST,
    PARTY_CREATED,
    PARTY_INVITE_REQUEST,
    PARTY_INVITE_RECEIVED,
    PARTY_JOIN_BY_CODE_REQUEST,
    PARTY_JOIN_RESPONSE,
    PARTY_UPDATE,
    PARTY_LEAVE_REQUEST,
    PARTY_KICK_REQUEST,
    PARTY_DISBANDED,

    // --- Achievements ---
    ACHIEVEMENT_UNLOCKED,
    ACHIEVEMENTS_REQUEST,
    ACHIEVEMENTS_RESPONSE,

    // --- Tournaments (4-player single-elimination) ---
    TOURNAMENT_CREATE_REQUEST,
    TOURNAMENT_JOIN_REQUEST,
    TOURNAMENT_LIST_REQUEST,
    TOURNAMENT_LIST_RESPONSE,
    TOURNAMENT_COMPLETE,
    TEAM_TOURNAMENT_CREATE_REQUEST,
    TEAM_TOURNAMENT_JOIN_REQUEST,
    TEAM_TOURNAMENT_LIST_REQUEST,
    TEAM_TOURNAMENT_LIST_RESPONSE,
    SYNC_AUTH_REQUEST,
    SYNC_AUTH_RESPONSE,
    SYNC_PUSH_REQUEST,
    SYNC_PUSH_RESPONSE,
    SATELLITE_REGISTER_REQUEST,
    SATELLITE_LIST_REQUEST,
    SATELLITE_LIST_RESPONSE,
    PRESENCE_UPDATE,
    FRIEND_LOCATION_REQUEST,
    FRIEND_LOCATION_RESPONSE,

    // --- Spectator mode ---
    SPECTATABLE_MATCHES_REQUEST,
    SPECTATABLE_MATCHES_RESPONSE,
    SPECTATE_REQUEST,
    SPECTATE_ENDED,

    // --- Match replay ---
    REPLAY_LIST_REQUEST,
    REPLAY_LIST_RESPONSE,
    REPLAY_REQUEST,
    REPLAY_RESPONSE,

    // --- Client auto-update (see ClientUpdateChecker/ClientUpdatePackage) ---
    CLIENT_VERSION_CHECK_REQUEST,
    CLIENT_VERSION_CHECK_RESPONSE,
    CLIENT_UPDATE_DOWNLOAD_REQUEST,
    CLIENT_UPDATE_DOWNLOAD_RESPONSE,

    // --- Custom (user-uploaded) games - see CustomGameStore/CustomGamesPanel/CodeEditorWindow ----
    CUSTOM_GAME_UPLOAD_REQUEST,
    CUSTOM_GAME_UPLOAD_RESPONSE,
    CUSTOM_GAME_LIST_REQUEST,
    CUSTOM_GAME_LIST_RESPONSE,
    CUSTOM_GAME_DOWNLOAD_REQUEST,
    CUSTOM_GAME_DOWNLOAD_RESPONSE,
    CUSTOM_GAME_DELETE_REQUEST,
    CUSTOM_GAME_DELETE_RESPONSE,
    CUSTOM_GAME_APPROVE_REQUEST,
    CUSTOM_GAME_APPROVE_RESPONSE
}
