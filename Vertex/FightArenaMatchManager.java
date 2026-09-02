import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FightArenaMatchManager
 * -----------------------
 * Matchmaking for the real-time brawler - four separate queues (1v1,
 * 2v2, 3v3, Chaos/FFA), since a player picks which mode they want
 * rather than being pooled with everyone. Team modes start the moment
 * enough players are waiting for an even split; FFA uses the same
 * immediate-start (min 3, max 8) pattern as Racing/Among Us. Multiple
 * matches of any mode run concurrently, same as every other
 * match-based game here - each gets its own entry in activeMatches
 * and (for this game specifically) its own ticking thread.
 */
public class FightArenaMatchManager
{
    private static final String GAME_ID = "fight-arena";
    private static final int FFA_MIN = 3;
    private static final int FFA_MAX = 8;

    private final Map<String, List<ClientHandler>> waitingByMode = new HashMap<String, List<ClientHandler>>();
    private final Map<String, FightMatch> activeMatches = new HashMap<String, FightMatch>();
    private int nextMatchId = 1;
    private final GameHistoryManager gameHistoryManager;
    private final ChatManager chatManager;
    private final LeaderboardManager leaderboardManager;
    private final PartyManager partyManager;

    public FightArenaMatchManager(GameHistoryManager gameHistoryManager, ChatManager chatManager,
                                   LeaderboardManager leaderboardManager, PartyManager partyManager)
    {
        this.gameHistoryManager = gameHistoryManager;
        this.chatManager = chatManager;
        this.leaderboardManager = leaderboardManager;
        this.partyManager = partyManager;
        waitingByMode.put("1V1", new ArrayList<ClientHandler>());
        waitingByMode.put("2V2", new ArrayList<ClientHandler>());
        waitingByMode.put("3V3", new ArrayList<ClientHandler>());
        waitingByMode.put("FFA", new ArrayList<ClientHandler>());
    }

    /** Queueing while in a party queues every party member together, not just the caller - so the greedy team-assignment below can keep them as one unit. Rejects team modes (not FFA) when the party is bigger than one side can hold. */
    public synchronized void findMatch(ClientHandler player, String mode)
    {
        List<ClientHandler> waiting = waitingByMode.get(mode);
        if (waiting == null)
        {
            return;
        }

        Party party = partyManager.getParty(player);
        List<ClientHandler> toQueue = party != null ? party.getMembers() : java.util.Collections.singletonList(player);

        if (party != null && !"FFA".equals(mode))
        {
            int perTeam = "1V1".equals(mode) ? 1 : "2V2".equals(mode) ? 2 : 3;
            if (party.getMembers().size() > perTeam)
            {
                sendPartyTooLarge(player, mode, perTeam);
                return;
            }
        }

        for (int i = 0; i < toQueue.size(); i++)
        {
            ClientHandler member = toQueue.get(i);
            if (!waiting.contains(member))
            {
                waiting.add(member);
            }
        }

        if ("FFA".equals(mode))
        {
            if (waiting.size() >= FFA_MIN)
            {
                int takeCount = Math.min(waiting.size(), FFA_MAX);
                startFfaMatch(waiting, takeCount);
            }
        }
        else
        {
            int perTeam = "1V1".equals(mode) ? 1 : "2V2".equals(mode) ? 2 : 3;
            tryStartTeamMatch(mode, waiting, perTeam);
        }

        broadcastQueueCount(mode);
    }

    private void sendPartyTooLarge(ClientHandler to, String mode, int perTeam)
    {
        Message msg = new Message();
        msg.setType(MessageType.ERROR_NOTICE);
        msg.setErrorText("Your party has more than " + perTeam + " - too big for " + mode + ".");
        to.sendMessage(msg);
    }

    private void startFfaMatch(List<ClientHandler> waiting, int takeCount)
    {
        List<ClientHandler> matched = new ArrayList<ClientHandler>(waiting.subList(0, takeCount));
        for (int i = 0; i < takeCount; i++)
        {
            waiting.remove(0);
        }

        List<Integer> teams = new ArrayList<Integer>();
        for (int i = 0; i < matched.size(); i++)
        {
            teams.add(i);
        }

        createMatch("FFA", matched, teams);
    }

    /** Groups the waiting list into units (a whole party, or a solo player) first, then greedily fills the two teams without ever splitting a unit across sides - this is what actually keeps a party together. */
    private void tryStartTeamMatch(String mode, List<ClientHandler> waiting, int perTeam)
    {
        List<List<ClientHandler>> units = groupIntoUnits(waiting);

        List<ClientHandler> team0 = new ArrayList<ClientHandler>();
        List<ClientHandler> team1 = new ArrayList<ClientHandler>();
        List<List<ClientHandler>> usedUnits = new ArrayList<List<ClientHandler>>();

        for (int i = 0; i < units.size(); i++)
        {
            List<ClientHandler> unit = units.get(i);
            if (team0.size() + unit.size() <= perTeam)
            {
                team0.addAll(unit);
                usedUnits.add(unit);
            }
            else if (team1.size() + unit.size() <= perTeam)
            {
                team1.addAll(unit);
                usedUnits.add(unit);
            }
            if (team0.size() == perTeam && team1.size() == perTeam)
            {
                break;
            }
        }

        if (team0.size() != perTeam || team1.size() != perTeam)
        {
            return;
        }

        for (int i = 0; i < usedUnits.size(); i++)
        {
            waiting.removeAll(usedUnits.get(i));
        }

        List<ClientHandler> matched = new ArrayList<ClientHandler>();
        matched.addAll(team0);
        matched.addAll(team1);

        List<Integer> teams = new ArrayList<Integer>();
        for (int i = 0; i < team0.size(); i++) teams.add(0);
        for (int i = 0; i < team1.size(); i++) teams.add(1);

        createMatch(mode, matched, teams);
    }

    /** A player with no party is their own unit of one; a party's members (that are actually in this queue) form a single unit together. */
    private List<List<ClientHandler>> groupIntoUnits(List<ClientHandler> waiting)
    {
        List<List<ClientHandler>> units = new ArrayList<List<ClientHandler>>();
        java.util.Set<ClientHandler> seen = new java.util.HashSet<ClientHandler>();

        for (int i = 0; i < waiting.size(); i++)
        {
            ClientHandler player = waiting.get(i);
            if (seen.contains(player))
            {
                continue;
            }

            Party party = partyManager.getParty(player);
            if (party == null)
            {
                units.add(java.util.Collections.singletonList(player));
                seen.add(player);
                continue;
            }

            List<ClientHandler> unit = new ArrayList<ClientHandler>();
            List<ClientHandler> partyMembers = party.getMembers();
            for (int j = 0; j < partyMembers.size(); j++)
            {
                ClientHandler member = partyMembers.get(j);
                if (waiting.contains(member) && !seen.contains(member))
                {
                    unit.add(member);
                    seen.add(member);
                }
            }
            units.add(unit);
        }
        return units;
    }

    private void createMatch(String mode, List<ClientHandler> matched, List<Integer> teams)
    {
        String matchId = "fight-" + (nextMatchId++);
        FightMatch match = new FightMatch(matchId, mode, matched, teams, this, leaderboardManager);
        activeMatches.put(matchId, match);

        for (int i = 0; i < matched.size(); i++)
        {
            matched.get(i).setCurrentFightMatch(match);
            recordPlay(matched.get(i));
        }
        match.start();
    }

    private void recordPlay(ClientHandler handler)
    {
        if (handler.getLoggedInUsername() != null && handler.getAccountId() != null)
        {
            gameHistoryManager.recordPlay(handler.getAccountId(), GAME_ID);
        }
    }

    public synchronized void cancelWaiting(ClientHandler player)
    {
        for (Map.Entry<String, List<ClientHandler>> entry : waitingByMode.entrySet())
        {
            if (entry.getValue().remove(player))
            {
                broadcastQueueCount(entry.getKey());
            }
        }
    }

    public synchronized void endMatch(String matchId)
    {
        activeMatches.remove(matchId);
    }

    public synchronized int getQueueCount(String mode)
    {
        List<ClientHandler> waiting = waitingByMode.get(mode);
        return waiting == null ? 0 : waiting.size();
    }

    /** Reuses queueGameId with a ":mode" suffix so the four separate queues can share the existing QUEUE_UPDATE broadcast rather than needing their own message type. */
    private void broadcastQueueCount(String mode)
    {
        Message msg = new Message();
        msg.setType(MessageType.QUEUE_UPDATE);
        msg.setQueueGameId(GAME_ID + ":" + mode);
        msg.setQueueCount(getQueueCount(mode));
        chatManager.broadcastToAll(msg);
    }
}
