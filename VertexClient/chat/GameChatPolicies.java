package chat;

import net.ClientHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GameChatPolicies
 * -----------------
 * The one place that decides which games have an in-match chat room, and how
 * restricted it starts. This is the whole "package to restrict easily" surface:
 *
 *   - Give a game chat:          add one line to the table below (OPEN).
 *   - Restrict a game's chat:    make that line LOCKED, then call room.unlock() from
 *                                the game when chatting becomes harmless (Telephone
 *                                does at its reveal).
 *   - Take chat away:            delete the line.
 *
 * Opt-in on purpose: a game not listed has no chat at all, so a game where free
 * chat would leak hidden information never gets one by accident.
 *
 * A listed game only actually gets a room if it calls openRoom(...) - the shared
 * MatchmakingKernel does for every game built on it, TelephoneMatch does directly.
 */
public class GameChatPolicies
{
    private static final Map<String, ChatRestriction> STARTING = new HashMap<String, ChatRestriction>();

    static
    {
        // Every game on the shared MatchmakingKernel (plain 1v1, nothing to hide from your opponent by chatting).
        allow("checkers");
        allow("connect-four");
        allow("reversi");
        allow("dots-and-boxes");
        allow("word-duel");
        allow("dice-duel");
        allow("typing-duel");
        allow("air-hockey");
        allow("memory-match");
        allow("signal-grid");
        allow("fusion-grid");
        allow("card-rush");
        allow("snake-arena");
        allow("tetris-duel");

        // The older hand-written 1v1 managers (they open their room through chat.MatchChatRooms).
        allow("tictactoe-online");
        allow("chess");
        allow("battleship");
        allow("rock-paper-scissors");

        // The group / real-time games (decided 2026-10-01 as reversible defaults, see ROADMAP "Priority queue" item 3): co-op and
        // racing/battle games are fine with open chat; Trivia Blitz starts LOCKED because answers called out would be cheating, and
        // TriviaMatch unlocks the room when the match ends. Among Us is deliberately NOT listed - hidden roles, chat could give them away.
        allow("racing");
        allow("space-battle");
        allow("square-wars");
        allow("zombie-survival");
        allow("fight-arena");
        STARTING.put("trivia-blitz", ChatRestriction.LOCKED);

        // Telephone: free chat during the draw/guess chain would let players say the
        // answer out loud, so it starts locked and TelephoneMatch unlocks it at the reveal.
        STARTING.put("telephone", ChatRestriction.LOCKED);

        // Caption Chaos: answers are anonymous until the reveal, so chat stays locked until the match ends (CaptionChaosMatch unlocks it).
        STARTING.put("caption-chaos", ChatRestriction.LOCKED);
    }

    private GameChatPolicies()
    {
        // Static utility class - never instantiated.
    }

    private static void allow(String gameId)
    {
        STARTING.put(gameId, ChatRestriction.OPEN);
    }

    public static boolean hasMatchChat(String gameId)
    {
        return STARTING.containsKey(gameId);
    }

    /** The restriction a game's room starts with, or null if the game has no chat. */
    public static ChatRestriction startingRestriction(String gameId)
    {
        return STARTING.get(gameId);
    }

    /**
     * Opens the chat room for a starting match and announces it to every member (each
     * gets a MATCH_CHAT_STATE). Returns null - and does nothing - if the game has no
     * chat. The caller owns the room afterwards: close() it when the match ends.
     */
    public static MatchChatRoom openRoom(String matchId, String gameId, List<ClientHandler> members)
    {
        ChatRestriction starting = STARTING.get(gameId);
        if (starting == null)
        {
            return null;
        }
        MatchChatRoom room = new MatchChatRoom(matchId, gameId, members, starting);
        room.open();
        return room;
    }
}
