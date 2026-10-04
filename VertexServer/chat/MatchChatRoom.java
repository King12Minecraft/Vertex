package chat;

import net.ClientHandler;
import net.Message;
import net.MessageType;
import social.ChatManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * MatchChatRoom
 * --------------
 * A small chat room shared by everyone in one live match, separate from DMs/group
 * chats. Server-authoritative: the room decides whether a message is allowed
 * (membership, the current ChatRestriction) and relays it - a client can ask to send,
 * never force a message through a locked room.
 *
 * Rooms are made through GameChatPolicies.openRoom(...), not constructed directly.
 * Lifetime is the caller's: close() when the match ends, or closeAfter(ms) to keep
 * chatting for a bounded while afterwards. Every state change (opened, locked,
 * unlocked, closed) is pushed to members as MATCH_CHAT_STATE so the client dock can
 * mirror it. Mute/flood checks happen in ClientHandler before a message gets here.
 */
public class MatchChatRoom
{
    /** Every open room by match id, so a player who reconnects mid-match can be put back in theirs (see rejoin). */
    private static final java.util.Map<String, MatchChatRoom> ACTIVE =
        new java.util.concurrent.ConcurrentHashMap<String, MatchChatRoom>();

    public static MatchChatRoom find(String matchId)
    {
        return matchId == null ? null : ACTIVE.get(matchId);
    }

    private final String matchId;
    private final String gameId;
    private final List<ClientHandler> members;
    private ChatRestriction restriction;
    private boolean closed = false;
    private Timer closeTimer;

    MatchChatRoom(String matchId, String gameId, List<ClientHandler> members, ChatRestriction startingRestriction)
    {
        this.matchId = matchId;
        this.gameId = gameId;
        this.members = new ArrayList<ClientHandler>(members);
        this.restriction = startingRestriction;
    }

    /** Attaches the room to every member (so ClientHandler can route their messages here) and tells each one it exists. A member still holding a previous room (e.g. Telephone's post-reveal chat) is moved out of it first. */
    synchronized void open()
    {
        ACTIVE.put(matchId, this);
        for (int i = 0; i < members.size(); i++)
        {
            ClientHandler member = members.get(i);
            MatchChatRoom previous = member.getMatchChatRoom();
            if (previous != null && previous != this)
            {
                previous.leave(member);
            }
            member.setMatchChatRoom(this);
            member.sendMessage(stateMessage(restriction.name()));
        }
    }

    /** Relays text to every member if the room is open to it. Returns whether it was sent - false for a closed or locked room, a non-member, or empty text. */
    public synchronized boolean post(ClientHandler sender, String rawText)
    {
        if (closed || restriction == ChatRestriction.LOCKED || !members.contains(sender))
        {
            return false;
        }
        String text = ChatManager.trimText(rawText);
        if (text.isEmpty())
        {
            return false;
        }

        Message message = new Message();
        message.setType(MessageType.MATCH_CHAT_MESSAGE);
        message.setMatchId(matchId);
        message.setUsername(sender.getLoggedInUsername());
        message.setChatText(text);
        for (int i = 0; i < members.size(); i++)
        {
            members.get(i).sendMessage(message);
        }
        return true;
    }

    public synchronized void lock()
    {
        setRestriction(ChatRestriction.LOCKED);
    }

    public synchronized void unlock()
    {
        setRestriction(ChatRestriction.OPEN);
    }

    private void setRestriction(ChatRestriction next)
    {
        if (closed || restriction == next)
        {
            return;
        }
        restriction = next;
        for (int i = 0; i < members.size(); i++)
        {
            members.get(i).sendMessage(stateMessage(next.name()));
        }
    }

    /**
     * A player logged back in mid-match on a new connection: puts the new handler in the room in
     * place of their old one (matched by username - the old handler is either already gone, or a
     * dead connection the server hasn't noticed). Sends nothing: the client asks for the room's
     * state once its window is up (MATCH_CHAT_SYNC_REQUEST -> sendStateTo), because a push sent
     * during login would arrive before the client is ready for it.
     */
    public synchronized void rejoin(ClientHandler newMember)
    {
        if (closed || newMember == null)
        {
            return;
        }
        String username = newMember.getLoggedInUsername();
        for (int i = members.size() - 1; i >= 0; i--)
        {
            ClientHandler old = members.get(i);
            if (old != newMember && username != null && username.equals(old.getLoggedInUsername()))
            {
                members.remove(i);
                if (old.getMatchChatRoom() == this)
                {
                    old.setMatchChatRoom(null);
                }
            }
        }
        if (!members.contains(newMember))
        {
            members.add(newMember);
        }
        newMember.setMatchChatRoom(this);
    }

    /** Tells one member the room's current state (OPEN/LOCKED) - how a reconnected client gets its chat dock back. */
    public synchronized void sendStateTo(ClientHandler member)
    {
        if (!closed && members.contains(member))
        {
            member.sendMessage(stateMessage(restriction.name()));
        }
    }

    /** Drops one member (e.g. they disconnected) without touching anyone else. */
    public synchronized void leave(ClientHandler member)
    {
        if (members.remove(member) && member.getMatchChatRoom() == this)
        {
            member.setMatchChatRoom(null);
        }
    }

    /** Ends the room now: every member is told it's CLOSED and detached. Safe to call more than once. */
    public synchronized void close()
    {
        if (closed)
        {
            return;
        }
        closed = true;
        ACTIVE.remove(matchId, this);
        if (closeTimer != null)
        {
            closeTimer.cancel();
            closeTimer = null;
        }
        for (int i = 0; i < members.size(); i++)
        {
            ClientHandler member = members.get(i);
            member.sendMessage(stateMessage("CLOSED"));
            if (member.getMatchChatRoom() == this)
            {
                member.setMatchChatRoom(null);
            }
        }
        members.clear();
    }

    /** Keeps the room alive for millis more (e.g. so players can chat during a game's post-match reveal), then close()s it. */
    public synchronized void closeAfter(long millis)
    {
        if (closed || closeTimer != null)
        {
            return;
        }
        closeTimer = new Timer("match-chat-close-" + matchId, true);
        closeTimer.schedule(new TimerTask()
        {
            public void run() { close(); }
        }, millis);
    }

    public synchronized ChatRestriction getRestriction() { return restriction; }
    public synchronized boolean isClosed() { return closed; }
    public String getMatchId() { return matchId; }
    public String getGameId() { return gameId; }

    private Message stateMessage(String state)
    {
        Message message = new Message();
        message.setType(MessageType.MATCH_CHAT_STATE);
        message.setMatchId(matchId);
        message.setGameId(gameId);
        message.setMatchChatState(state);
        return message;
    }
}
