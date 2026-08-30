import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * PartyManager
 * ------------
 * Tracks every active party and which one (if any) each player is in.
 * Two ways to join: a direct invite to a specific friend (relayed the
 * same fire-and-forget way GAME_INVITE already works), or sharing a
 * short code anyone can redeem without needing to be friends first.
 * A player can only be in one party at a time.
 */
public class PartyManager
{
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I - easy to misread
    private static final int CODE_LENGTH = 6;

    private final Map<String, Party> partiesById = new HashMap<String, Party>();
    private final Map<ClientHandler, Party> partyByPlayer = new HashMap<ClientHandler, Party>();
    private final Map<String, Party> partiesByCode = new HashMap<String, Party>();
    private int nextPartyId = 1;
    private final Random random = new Random();
    private final ChatManager chatManager;

    public PartyManager(ChatManager chatManager)
    {
        this.chatManager = chatManager;
    }

    public synchronized Party getParty(ClientHandler player)
    {
        return partyByPlayer.get(player);
    }

    public synchronized void createParty(ClientHandler leader)
    {
        if (partyByPlayer.containsKey(leader))
        {
            return;
        }

        String partyId = "party-" + (nextPartyId++);
        String code = generateUniqueCode();
        Party party = new Party(partyId, code, leader);

        partiesById.put(partyId, party);
        partiesByCode.put(code, party);
        partyByPlayer.put(leader, party);

        broadcastUpdate(party);
    }

    private String generateUniqueCode()
    {
        String code;
        do
        {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++)
            {
                sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            }
            code = sb.toString();
        }
        while (partiesByCode.containsKey(code));
        return code;
    }

    /** Fire-and-forget relay, same pattern as GAME_INVITE - the target sees a PARTY_INVITE_RECEIVED push if they're online, nothing persists if they're not. */
    public synchronized void invite(ClientHandler inviter, String targetUsername)
    {
        Party party = partyByPlayer.get(inviter);
        if (party == null || party.isFull())
        {
            return;
        }

        ClientHandler target = chatManager.findByUsername(targetUsername);
        if (target == null || partyByPlayer.containsKey(target))
        {
            return;
        }

        Message msg = new Message();
        msg.setType(MessageType.PARTY_INVITE_RECEIVED);
        msg.setUsername(inviter.getLoggedInUsername());
        msg.setPartyCode(party.getCode());
        target.sendMessage(msg);
    }

    public synchronized void joinByCode(ClientHandler player, String code)
    {
        if (partyByPlayer.containsKey(player) || code == null)
        {
            sendJoinResponse(player, false, "You're already in a party.");
            return;
        }

        Party party = partiesByCode.get(code.toUpperCase());
        if (party == null)
        {
            sendJoinResponse(player, false, "No party found with that code.");
            return;
        }
        if (party.isFull())
        {
            sendJoinResponse(player, false, "That party is full.");
            return;
        }

        party.addMember(player);
        partyByPlayer.put(player, party);
        sendJoinResponse(player, true, null);
        broadcastUpdate(party);
    }

    private void sendJoinResponse(ClientHandler to, boolean success, String errorText)
    {
        Message msg = new Message();
        msg.setType(MessageType.PARTY_JOIN_RESPONSE);
        msg.setSuccess(success);
        msg.setErrorText(errorText);
        to.sendMessage(msg);
    }

    public synchronized void leave(ClientHandler player)
    {
        Party party = partyByPlayer.remove(player);
        if (party == null)
        {
            return;
        }

        boolean shouldDisband = party.removeMember(player);
        if (shouldDisband)
        {
            disband(party);
        }
        else
        {
            broadcastUpdate(party);
        }
    }

    public synchronized void kick(ClientHandler leader, String targetUsername)
    {
        Party party = partyByPlayer.get(leader);
        if (party == null || party.getLeader() != leader)
        {
            return;
        }

        ClientHandler target = findMember(party, targetUsername);
        if (target == null || target == leader)
        {
            return;
        }

        partyByPlayer.remove(target);
        party.removeMember(target);

        Message kicked = new Message();
        kicked.setType(MessageType.PARTY_DISBANDED);
        kicked.setErrorText("You were removed from the party.");
        target.sendMessage(kicked);

        broadcastUpdate(party);
    }

    private ClientHandler findMember(Party party, String username)
    {
        List<ClientHandler> members = party.getMembers();
        for (int i = 0; i < members.size(); i++)
        {
            if (username.equalsIgnoreCase(members.get(i).getLoggedInUsername()))
            {
                return members.get(i);
            }
        }
        return null;
    }

    private void disband(Party party)
    {
        partiesById.remove(party.getPartyId());
        partiesByCode.remove(party.getCode());
    }

    private void broadcastUpdate(Party party)
    {
        List<String> memberUsernames = party.getMemberUsernames();
        String leaderUsername = party.getLeader().getLoggedInUsername();

        List<ClientHandler> members = party.getMembers();
        for (int i = 0; i < members.size(); i++)
        {
            Message msg = new Message();
            msg.setType(MessageType.PARTY_UPDATE);
            msg.setPartyCode(party.getCode());
            msg.setPartyMembers(memberUsernames);
            msg.setPartyLeader(leaderUsername);
            members.get(i).sendMessage(msg);
        }
    }

    /** Called on disconnect - same as leave(), just doesn't assume the handler is still reachable for a response. */
    public synchronized void handleDisconnect(ClientHandler player)
    {
        leave(player);
    }
}
