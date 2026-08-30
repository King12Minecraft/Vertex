import java.util.ArrayList;
import java.util.List;

/**
 * Party
 * -----
 * A small group of players who want to play together - created by one
 * leader, joined by others via direct invite or a shareable code.
 * Deliberately just a data holder; PartyManager owns all the actual
 * logic (creation, invites, code lookup, membership changes).
 */
public class Party
{
    private static final int MAX_MEMBERS = 8;

    private final String partyId;
    private final String code;
    private ClientHandler leader;
    private final List<ClientHandler> members = new ArrayList<ClientHandler>();

    public Party(String partyId, String code, ClientHandler leader)
    {
        this.partyId = partyId;
        this.code = code;
        this.leader = leader;
        members.add(leader);
    }

    public String getPartyId() { return partyId; }
    public String getCode() { return code; }
    public ClientHandler getLeader() { return leader; }
    public List<ClientHandler> getMembers() { return members; }

    public boolean isFull()
    {
        return members.size() >= MAX_MEMBERS;
    }

    public boolean contains(ClientHandler player)
    {
        return members.contains(player);
    }

    public void addMember(ClientHandler player)
    {
        if (!members.contains(player))
        {
            members.add(player);
        }
    }

    /** Removes the player; if they were the leader, promotes the next-longest member instead. Returns true if the party is now empty and should be disbanded. */
    public boolean removeMember(ClientHandler player)
    {
        members.remove(player);
        if (members.isEmpty())
        {
            return true;
        }
        if (leader == player)
        {
            leader = members.get(0);
        }
        return false;
    }

    public List<String> getMemberUsernames()
    {
        List<String> result = new ArrayList<String>();
        for (int i = 0; i < members.size(); i++)
        {
            result.add(members.get(i).getLoggedInUsername());
        }
        return result;
    }
}
