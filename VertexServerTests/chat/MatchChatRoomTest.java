package chat;

import games.DiceDuelMatchManager;
import net.Message;
import net.MessageType;
import support.Check;
import support.FakeClientHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * MatchChatRoomTest
 * ------------------
 * The in-match chat room is server-authoritative - a client can ask to send, the
 * room decides. These pin the rules that matter: a LOCKED room (Telephone) delivers
 * nothing until the game unlocks it, non-members and empty/over-long text are
 * handled, closing detaches everyone, and the MatchmakingKernel really opens a room
 * for its games. (Mute and flood limits live in ClientHandler.handleMatchChat and
 * need real managers, so they're not covered here.)
 */
public class MatchChatRoomTest
{
    public static void main(String[] args) throws Exception
    {
        Check check = new Check();

        testPolicies(check);
        testOpenAnnouncesAndAttaches(check);
        testPostRelaysAndValidates(check);
        testLockedRoomBlocksUntilUnlocked(check);
        testLeaveAndClose(check);
        testNewRoomMovesMemberOutOfOldOne(check);
        testCloseAfterEventuallyCloses(check);
        testKernelOpensAndKeepsRoomAfterMatchEnds(check);

        check.finish();
    }

    private static List<net.ClientHandler> pair(FakeClientHandler a, FakeClientHandler b)
    {
        return new ArrayList<net.ClientHandler>(Arrays.<net.ClientHandler>asList(a, b));
    }

    private static void testPolicies(Check check)
    {
        check.check("A kernel game has match chat, starting OPEN",
            GameChatPolicies.hasMatchChat("dice-duel") && GameChatPolicies.startingRestriction("dice-duel") == ChatRestriction.OPEN);
        check.check("Telephone has match chat, starting LOCKED",
            GameChatPolicies.startingRestriction("telephone") == ChatRestriction.LOCKED);
        check.check("A game not in the table has no chat (opt-in, never by accident)",
            !GameChatPolicies.hasMatchChat("chess") && GameChatPolicies.startingRestriction("chess") == null);

        FakeClientHandler a = new FakeClientHandler("Ann", 1);
        FakeClientHandler b = new FakeClientHandler("Ben", 2);
        MatchChatRoom none = GameChatPolicies.openRoom("chess-1", "chess", pair(a, b));
        check.check("openRoom for a game with no chat returns null and tells nobody anything",
            none == null && a.sent.isEmpty() && b.sent.isEmpty() && a.getMatchChatRoom() == null);
    }

    private static void testOpenAnnouncesAndAttaches(Check check)
    {
        FakeClientHandler a = new FakeClientHandler("Ann", 1);
        FakeClientHandler b = new FakeClientHandler("Ben", 2);
        MatchChatRoom room = GameChatPolicies.openRoom("dd-1", "dice-duel", pair(a, b));

        Message state = a.lastOfType(MessageType.MATCH_CHAT_STATE);
        check.check("Opening a room tells every member (MATCH_CHAT_STATE, OPEN, with match and game id)",
            state != null && "OPEN".equals(state.getMatchChatState()) && "dd-1".equals(state.getMatchId())
                && "dice-duel".equals(state.getGameId()) && b.lastOfType(MessageType.MATCH_CHAT_STATE) != null);
        check.check("Each member is attached to the room so ClientHandler can route their messages",
            a.getMatchChatRoom() == room && b.getMatchChatRoom() == room);
        room.close();
    }

    private static void testPostRelaysAndValidates(Check check)
    {
        FakeClientHandler a = new FakeClientHandler("Ann", 1);
        FakeClientHandler b = new FakeClientHandler("Ben", 2);
        FakeClientHandler outsider = new FakeClientHandler("Zed", 3);
        MatchChatRoom room = GameChatPolicies.openRoom("dd-2", "dice-duel", pair(a, b));

        boolean sent = room.post(a, "  hello there  ");
        Message toB = b.lastOfType(MessageType.MATCH_CHAT_MESSAGE);
        check.check("A member's message reaches the other member, from the right sender, trimmed",
            sent && toB != null && "Ann".equals(toB.getUsername()) && "hello there".equals(toB.getChatText())
                && "dd-2".equals(toB.getMatchId()));
        check.check("The sender gets their own message back too (so their dock shows it)",
            a.lastOfType(MessageType.MATCH_CHAT_MESSAGE) != null);

        int before = a.countOfType(MessageType.MATCH_CHAT_MESSAGE);
        check.check("Empty and whitespace-only text is ignored",
            !room.post(a, "") && !room.post(a, "   ") && !room.post(a, null)
                && a.countOfType(MessageType.MATCH_CHAT_MESSAGE) == before);
        check.check("Someone who isn't in the room can't post into it",
            !room.post(outsider, "let me in") && outsider.sent.isEmpty());

        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 800; i++) longText.append('x');
        room.post(b, longText.toString());
        Message longMsg = a.lastOfType(MessageType.MATCH_CHAT_MESSAGE);
        check.check("Over-long text is cut to the same 500-character cap DMs use",
            longMsg != null && longMsg.getChatText().length() == 500);
        room.close();
    }

    private static void testLockedRoomBlocksUntilUnlocked(Check check)
    {
        FakeClientHandler a = new FakeClientHandler("Ann", 1);
        FakeClientHandler b = new FakeClientHandler("Ben", 2);
        MatchChatRoom room = GameChatPolicies.openRoom("tel-1", "telephone", pair(a, b));

        check.check("A restricted game's room announces itself as LOCKED",
            "LOCKED".equals(a.lastOfType(MessageType.MATCH_CHAT_STATE).getMatchChatState())
                && room.getRestriction() == ChatRestriction.LOCKED);
        check.check("A locked room delivers nothing - the server refuses, not just the client's UI",
            !room.post(a, "the answer is banana") && b.lastOfType(MessageType.MATCH_CHAT_MESSAGE) == null);

        room.unlock();
        check.check("unlock() tells every member the room is OPEN",
            "OPEN".equals(a.lastOfType(MessageType.MATCH_CHAT_STATE).getMatchChatState())
                && "OPEN".equals(b.lastOfType(MessageType.MATCH_CHAT_STATE).getMatchChatState()));
        check.check("Once unlocked, messages go through",
            room.post(a, "gg") && b.lastOfType(MessageType.MATCH_CHAT_MESSAGE) != null);

        int states = a.countOfType(MessageType.MATCH_CHAT_STATE);
        room.unlock();
        check.check("Unlocking an already-open room sends nothing new", a.countOfType(MessageType.MATCH_CHAT_STATE) == states);

        room.lock();
        check.check("A room can be locked again", !room.post(a, "still there?"));
        room.close();
    }

    private static void testLeaveAndClose(Check check)
    {
        FakeClientHandler a = new FakeClientHandler("Ann", 1);
        FakeClientHandler b = new FakeClientHandler("Ben", 2);
        MatchChatRoom room = GameChatPolicies.openRoom("dd-3", "dice-duel", pair(a, b));

        room.leave(b);
        int bBefore = b.sent.size();
        room.post(a, "anyone?");
        check.check("A member who left stops receiving, and is detached from the room",
            b.sent.size() == bBefore && b.getMatchChatRoom() == null);
        check.check("The remaining member can still chat", a.lastOfType(MessageType.MATCH_CHAT_MESSAGE) != null);
        check.check("A member who left can't post back in", !room.post(b, "hello?"));

        room.close();
        check.check("close() tells remaining members CLOSED and detaches them",
            "CLOSED".equals(a.lastOfType(MessageType.MATCH_CHAT_STATE).getMatchChatState()) && a.getMatchChatRoom() == null
                && room.isClosed());
        int closedCount = a.countOfType(MessageType.MATCH_CHAT_STATE);
        room.close();
        check.check("Closing twice sends nothing extra", a.countOfType(MessageType.MATCH_CHAT_STATE) == closedCount);
        check.check("A closed room accepts no more messages", !room.post(a, "too late"));
    }

    private static void testNewRoomMovesMemberOutOfOldOne(Check check)
    {
        FakeClientHandler a = new FakeClientHandler("Ann", 1);
        FakeClientHandler b = new FakeClientHandler("Ben", 2);
        FakeClientHandler c = new FakeClientHandler("Cat", 3);
        MatchChatRoom oldRoom = GameChatPolicies.openRoom("dd-4", "dice-duel", pair(a, b));
        MatchChatRoom newRoom = GameChatPolicies.openRoom("dd-5", "dice-duel", pair(a, c));

        check.check("Joining a new match's room moves a player out of their previous one",
            a.getMatchChatRoom() == newRoom && !oldRoom.post(a, "from the old room"));
        check.check("The old room still works for the people left in it",
            oldRoom.post(b, "still here") && newRoom.post(c, "new room"));
        oldRoom.close();
        check.check("Closing the old room doesn't detach a player who has already moved on", a.getMatchChatRoom() == newRoom);
        newRoom.close();
    }

    private static void testCloseAfterEventuallyCloses(Check check) throws Exception
    {
        FakeClientHandler a = new FakeClientHandler("Ann", 1);
        FakeClientHandler b = new FakeClientHandler("Ben", 2);
        MatchChatRoom room = GameChatPolicies.openRoom("dd-6", "dice-duel", pair(a, b));
        room.closeAfter(50);
        check.check("A room scheduled to close is still usable until then", !room.isClosed() && room.post(a, "quick gg"));

        long deadline = System.currentTimeMillis() + 3000;
        while (!room.isClosed() && System.currentTimeMillis() < deadline) Thread.sleep(20);
        check.check("...and closes on its own afterwards", room.isClosed() && a.getMatchChatRoom() == null);
    }

    private static void testKernelOpensAndKeepsRoomAfterMatchEnds(Check check)
    {
        DiceDuelMatchManager manager = new DiceDuelMatchManager(null,
            new economy.GameHistoryManager(), new social.ChatManager(), null);
        FakeClientHandler a = new FakeClientHandler("Ann", 1);
        FakeClientHandler b = new FakeClientHandler("Ben", 2);
        manager.findMatch(a);
        manager.findMatch(b);

        Message found = a.lastOfType(MessageType.DICEDUEL_MATCH_FOUND);
        MatchChatRoom room = a.getMatchChatRoom();
        check.check("Pairing two players on the MatchmakingKernel opens a chat room for both",
            found != null && room != null && room == b.getMatchChatRoom());
        Message state = a.lastOfType(MessageType.MATCH_CHAT_STATE);
        check.check("...announced OPEN, for that match's id and game",
            state != null && "OPEN".equals(state.getMatchChatState())
                && found.getMatchId().equals(state.getMatchId()) && "dice-duel".equals(state.getGameId()));
        check.check("...and the two players can actually chat through it", room.post(a, "good luck") && b.lastOfType(MessageType.MATCH_CHAT_MESSAGE) != null);

        manager.endMatch(found.getMatchId());
        check.check("When the match ends the room stays open for a while (time for a 'gg')",
            !room.isClosed() && room.post(b, "gg"));
        room.close();
    }
}
