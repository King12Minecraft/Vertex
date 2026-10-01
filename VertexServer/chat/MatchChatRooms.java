package chat;

import net.ClientHandler;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * MatchChatRooms
 * --------------
 * The few lines every 1v1 match manager needs to give its matches the in-match chat room: open one when a match is
 * created, close it (after a short grace so players can say "gg") when the match ends. MatchmakingKernel does this
 * inline for the games built on it; the older hand-written managers (Tic-Tac-Toe, Chess, Battleship, Rock Paper
 * Scissors) use this instead of each copying the bookkeeping. Whether a game actually gets a room is still decided
 * by GameChatPolicies - a game not listed there gets no room and these calls do nothing.
 *
 * Callers synchronise as they already do (the managers' methods are synchronized); this class adds no locking.
 */
public class MatchChatRooms
{
    private static final long POST_MATCH_CHAT_MILLIS = 60000;

    private final String gameId;
    private final Map<String, MatchChatRoom> rooms = new HashMap<String, MatchChatRoom>();

    public MatchChatRooms(String gameId)
    {
        this.gameId = gameId;
    }

    /** Opens the room for a freshly started match between two players (no-op if the game has no chat). */
    public void open(String matchId, ClientHandler a, ClientHandler b)
    {
        MatchChatRoom room = GameChatPolicies.openRoom(matchId, gameId, Arrays.asList(a, b));
        if (room != null)
        {
            rooms.put(matchId, room);
        }
    }

    /** Schedules the room to close shortly after the match ends. */
    public void close(String matchId)
    {
        MatchChatRoom room = rooms.remove(matchId);
        if (room != null)
        {
            room.closeAfter(POST_MATCH_CHAT_MILLIS);
        }
    }
}
