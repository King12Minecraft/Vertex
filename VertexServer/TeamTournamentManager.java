import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TeamTournamentManager
 * ----------------------
 * Team tournaments for Fight Arena's 2V2/3V3 modes, using the party
 * system as the registration unit - your whole party registers
 * together as one team. Deliberately a single decisive match between
 * two teams rather than a full multi-round bracket (which the solo
 * TournamentManager uses for Battleship/RPS): a real bracket here
 * would need 4 full teams to fill (8 players for 2V2, 12 for 3V3),
 * which is a lot of people to realistically assemble for a tournament
 * on a single server. A 2-team decider still delivers genuine
 * organized team competition (register, wait for an opponent team,
 * play, champions announced) without that scaling problem.
 *
 * Kept as its own class entirely separate from TournamentManager
 * rather than unifying the two - forcing solo (list of players) and
 * team (list of teams of players) registration into one shared data
 * shape would have meant touching the already-working, already-tested
 * solo bracket code, for no benefit beyond code reuse.
 */
public class TeamTournamentManager
{
    public static class TeamTournament
    {
        final String id;
        final String mode; // "2V2" or "3V3"
        final List<List<ClientHandler>> registeredTeams = new ArrayList<List<ClientHandler>>();
        String status = "REGISTRATION"; // REGISTRATION, IN_PROGRESS, COMPLETE
        List<ClientHandler> championTeam;

        TeamTournament(String id, String mode)
        {
            this.id = id;
            this.mode = mode;
        }

        int teamSize() { return "2V2".equals(mode) ? 2 : 3; }
    }

    private final Map<String, TeamTournament> tournaments = new HashMap<String, TeamTournament>();
    private int nextId = 1;
    private final FightArenaMatchManager fightArenaMatchManager;
    private final LeaderboardManager leaderboardManager;
    private final PartyManager partyManager;
    private final ChatManager chatManager;

    public TeamTournamentManager(FightArenaMatchManager fightArenaMatchManager, LeaderboardManager leaderboardManager,
                                  PartyManager partyManager, ChatManager chatManager)
    {
        this.fightArenaMatchManager = fightArenaMatchManager;
        this.leaderboardManager = leaderboardManager;
        this.partyManager = partyManager;
        this.chatManager = chatManager;
    }

    /** mode must be "2V2" or "3V3" - creator's current party becomes the first registered team. Rejects (silently, mirroring the solo tournament's own quiet rejections) if the creator isn't in a party, or their party is the wrong size for the mode. */
    public synchronized void create(ClientHandler creator, String mode)
    {
        if (!"2V2".equals(mode) && !"3V3".equals(mode))
        {
            return;
        }

        Party party = partyManager.getParty(creator);
        if (party == null)
        {
            return;
        }

        String id = "team-tournament-" + (nextId++);
        TeamTournament tournament = new TeamTournament(id, mode);
        if (party.getMembers().size() != tournament.teamSize())
        {
            return;
        }

        tournament.registeredTeams.add(new ArrayList<ClientHandler>(party.getMembers()));
        tournaments.put(id, tournament);
        broadcastList();
    }

    public synchronized void join(ClientHandler player, String tournamentId)
    {
        TeamTournament tournament = tournaments.get(tournamentId);
        if (tournament == null || !"REGISTRATION".equals(tournament.status))
        {
            return;
        }

        Party party = partyManager.getParty(player);
        if (party == null || party.getMembers().size() != tournament.teamSize())
        {
            return;
        }

        for (int i = 0; i < tournament.registeredTeams.size(); i++)
        {
            if (tournament.registeredTeams.get(i).contains(player))
            {
                return;
            }
        }

        tournament.registeredTeams.add(new ArrayList<ClientHandler>(party.getMembers()));

        if (tournament.registeredTeams.size() >= 2)
        {
            startDecider(tournament);
        }

        broadcastList();
    }

    private void startDecider(final TeamTournament tournament)
    {
        tournament.status = "IN_PROGRESS";

        List<ClientHandler> teamA = tournament.registeredTeams.get(0);
        List<ClientHandler> teamB = tournament.registeredTeams.get(1);

        List<ClientHandler> roster = new ArrayList<ClientHandler>();
        roster.addAll(teamA);
        roster.addAll(teamB);

        List<Integer> teamAssignments = new ArrayList<Integer>();
        for (int i = 0; i < teamA.size(); i++) teamAssignments.add(0);
        for (int i = 0; i < teamB.size(); i++) teamAssignments.add(1);

        String matchId = tournament.id + "-decider-" + System.nanoTime();
        FightMatch match = new FightMatch(matchId, tournament.mode, roster, teamAssignments, fightArenaMatchManager, leaderboardManager, null);

        match.setTournamentListener(new FightTournamentListener()
        {
            public void onMatchComplete(int winningTeam)
            {
                completeTournament(tournament, winningTeam == 0 ? teamA : teamB);
            }
        });

        for (int i = 0; i < roster.size(); i++)
        {
            roster.get(i).setCurrentFightMatch(match);
        }

        match.start();
        broadcastList();
    }

    private synchronized void completeTournament(TeamTournament tournament, List<ClientHandler> championTeam)
    {
        tournament.status = "COMPLETE";
        tournament.championTeam = championTeam;

        StringBuilder names = new StringBuilder();
        for (int i = 0; i < championTeam.size(); i++)
        {
            if (i > 0) names.append(" & ");
            names.append(championTeam.get(i).getLoggedInUsername());
        }

        List<ClientHandler> allPlayers = new ArrayList<ClientHandler>();
        for (int i = 0; i < tournament.registeredTeams.size(); i++)
        {
            allPlayers.addAll(tournament.registeredTeams.get(i));
        }

        for (int i = 0; i < allPlayers.size(); i++)
        {
            Message msg = new Message();
            msg.setType(MessageType.TOURNAMENT_COMPLETE);
            msg.setTournamentId(tournament.id);
            msg.setUsername(names.toString());
            allPlayers.get(i).sendMessage(msg);
        }

        broadcastList();
    }

    /** Every open/in-progress team tournament, "id|mode|status|teamsRegistered|championNames" per entry. */
    public synchronized List<String> listOpen()
    {
        List<String> result = new ArrayList<String>();
        for (TeamTournament t : tournaments.values())
        {
            String championNames = "";
            if (t.championTeam != null)
            {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < t.championTeam.size(); i++)
                {
                    if (i > 0) sb.append(" & ");
                    sb.append(t.championTeam.get(i).getLoggedInUsername());
                }
                championNames = sb.toString();
            }
            result.add(t.id + "|" + t.mode + "|" + t.status + "|" + t.registeredTeams.size() + "|" + championNames);
        }
        return result;
    }

    private void broadcastList()
    {
        Message msg = new Message();
        msg.setType(MessageType.TEAM_TOURNAMENT_LIST_RESPONSE);
        msg.setTournamentEntries(listOpen());
        chatManager.broadcastToAll(msg);
    }

    public synchronized void handleDisconnect(ClientHandler player)
    {
        // Same reasoning as TournamentManager's own handleDisconnect: a
        // registration during REGISTRATION just stays in the list if the
        // player never comes back, and once a match is underway,
        // FightMatch's own disconnect handling covers awarding the win,
        // which still reaches completeTournament via the same listener.
    }
}
