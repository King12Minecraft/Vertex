import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * AmongUsMatch
 * ------------
 * The core social-deduction loop - secret roles, tasks, kills,
 * meetings, voting, win conditions. Deliberately NOT a live 2D map
 * with movement and spatial kill detection - that would mean the same
 * kind of continuous position-broadcasting problem already avoided for
 * Racing (many updates per second, per player, to every other player).
 * Instead this runs in discrete rounds, closer to the genre Among Us
 * itself borrowed from (Mafia/Werewolf): Crewmates tick off a personal
 * task list (their real tasks; Impostors get a decoy list so they can
 * blend in), Impostors can kill a living Crewmate, any kill or manual
 * call triggers a meeting where every living player votes to eject
 * someone (or skip). Ejecting all Impostors or finishing every real
 * task wins it for the Crewmates; Impostors win once they equal or
 * outnumber the remaining Crewmates. No in-match chat - players can
 * use Vertex's existing Chat page to discuss during a meeting.
 */
public class AmongUsMatch
{
    private static final String[] TASK_NAMES = {
        "Fix Wiring", "Empty Trash", "Calibrate Distributor", "Fuel Engines",
        "Download Data", "Clear Asteroids", "Align Engine Output", "Stabilize Steering",
        "Chart Course", "Prime Shields", "Divert Power", "Clean O2 Filter"
    };
    private static final int TASKS_PER_PLAYER = 4;

    private final String matchId;
    private final List<ClientHandler> players;
    private final Set<ClientHandler> impostors = new HashSet<ClientHandler>();
    private final Set<ClientHandler> alivePlayers;
    private final Map<ClientHandler, List<String>> tasksByPlayer = new HashMap<ClientHandler, List<String>>();
    private final Map<ClientHandler, Set<Integer>> completedTasks = new HashMap<ClientHandler, Set<Integer>>();
    private final Map<ClientHandler, String> votes = new HashMap<ClientHandler, String>();
    private final AmongUsMatchManager matchManager;
    private final Random random = new Random();

    private int totalCrewTasks = 0;
    private int completedCrewTasks = 0;
    private boolean inMeeting = false;
    private String pendingDeadUsername;
    private boolean over = false;

    public AmongUsMatch(String matchId, List<ClientHandler> players, AmongUsMatchManager matchManager)
    {
        this.matchId = matchId;
        this.players = players;
        this.alivePlayers = new HashSet<ClientHandler>(players);
        this.matchManager = matchManager;

        int impostorCount = players.size() >= 6 ? 2 : 1;
        List<ClientHandler> shuffled = new ArrayList<ClientHandler>(players);
        Collections.shuffle(shuffled, random);
        for (int i = 0; i < impostorCount; i++)
        {
            impostors.add(shuffled.get(i));
        }

        for (int i = 0; i < players.size(); i++)
        {
            ClientHandler p = players.get(i);
            List<String> pool = new ArrayList<String>(Arrays.asList(TASK_NAMES));
            Collections.shuffle(pool, random);
            List<String> tasks = new ArrayList<String>(pool.subList(0, TASKS_PER_PLAYER));

            tasksByPlayer.put(p, tasks);
            completedTasks.put(p, new HashSet<Integer>());

            if (!impostors.contains(p))
            {
                totalCrewTasks += TASKS_PER_PLAYER;
            }
        }
    }

    public void start()
    {
        List<String> roster = rosterUsernames();
        for (int i = 0; i < players.size(); i++)
        {
            ClientHandler p = players.get(i);
            Message msg = new Message();
            msg.setType(MessageType.AMONG_MATCH_FOUND);
            msg.setMatchId(matchId);
            msg.setAmongRole(impostors.contains(p) ? "IMPOSTOR" : "CREWMATE");
            msg.setAmongTasks(tasksByPlayer.get(p));
            msg.setAmongRosterUsernames(roster);
            p.sendMessage(msg);
        }
        broadcastState();
    }

    private List<String> rosterUsernames()
    {
        List<String> result = new ArrayList<String>();
        for (int i = 0; i < players.size(); i++)
        {
            result.add(players.get(i).getLoggedInUsername());
        }
        return result;
    }

    private List<String> aliveUsernames()
    {
        List<String> result = new ArrayList<String>();
        for (int i = 0; i < players.size(); i++)
        {
            ClientHandler p = players.get(i);
            if (alivePlayers.contains(p))
            {
                result.add(p.getLoggedInUsername());
            }
        }
        return result;
    }

    public synchronized void completeTask(ClientHandler who, int taskIndex)
    {
        if (over || inMeeting || !alivePlayers.contains(who))
        {
            return;
        }
        List<String> tasks = tasksByPlayer.get(who);
        if (tasks == null || taskIndex < 0 || taskIndex >= tasks.size())
        {
            return;
        }

        Set<Integer> completed = completedTasks.get(who);
        if (completed.contains(taskIndex))
        {
            return;
        }
        completed.add(taskIndex);

        if (!impostors.contains(who))
        {
            completedCrewTasks++;
            if (completedCrewTasks >= totalCrewTasks)
            {
                endGame("CREWMATES");
                return;
            }
        }
        broadcastState();
    }

    public synchronized void attemptKill(ClientHandler impostor, String targetUsername)
    {
        if (over || inMeeting || !impostors.contains(impostor) || !alivePlayers.contains(impostor))
        {
            return;
        }

        ClientHandler target = findByUsername(targetUsername);
        if (target == null || impostors.contains(target) || !alivePlayers.contains(target))
        {
            return;
        }

        alivePlayers.remove(target);
        pendingDeadUsername = target.getLoggedInUsername();

        if (!checkImpostorWin())
        {
            startMeeting("BODY_FOUND");
        }
    }

    public synchronized void callMeeting(ClientHandler caller)
    {
        if (over || inMeeting || !alivePlayers.contains(caller))
        {
            return;
        }
        pendingDeadUsername = null;
        startMeeting("EMERGENCY");
    }

    private void startMeeting(String reason)
    {
        inMeeting = true;
        votes.clear();

        List<String> alive = aliveUsernames();
        for (int i = 0; i < players.size(); i++)
        {
            ClientHandler p = players.get(i);
            Message msg = new Message();
            msg.setType(MessageType.AMONG_MEETING_START);
            msg.setMatchId(matchId);
            msg.setAmongMeetingReason(reason);
            msg.setAmongDeadUsername(pendingDeadUsername);
            msg.setAmongAliveUsernames(alive);
            p.sendMessage(msg);
        }
    }

    public synchronized void castVote(ClientHandler voter, String targetUsername)
    {
        if (over || !inMeeting || !alivePlayers.contains(voter))
        {
            return;
        }
        votes.put(voter, targetUsername);

        if (votes.size() >= alivePlayers.size())
        {
            resolveMeeting();
        }
    }

    private void resolveMeeting()
    {
        Map<String, Integer> tally = new HashMap<String, Integer>();
        for (String target : votes.values())
        {
            if (target == null || target.isEmpty())
            {
                continue;
            }
            Integer count = tally.get(target);
            tally.put(target, count == null ? 1 : count + 1);
        }

        String ejectedUsername = null;
        int topVotes = 0;
        boolean tie = false;
        for (Map.Entry<String, Integer> entry : tally.entrySet())
        {
            if (entry.getValue() > topVotes)
            {
                topVotes = entry.getValue();
                ejectedUsername = entry.getKey();
                tie = false;
            }
            else if (entry.getValue() == topVotes)
            {
                tie = true;
            }
        }
        if (tie)
        {
            ejectedUsername = null;
        }

        String ejectedRole = null;
        if (ejectedUsername != null)
        {
            ClientHandler ejected = findByUsername(ejectedUsername);
            if (ejected != null)
            {
                alivePlayers.remove(ejected);
                ejectedRole = impostors.contains(ejected) ? "IMPOSTOR" : "CREWMATE";
            }
        }

        List<String> alive = aliveUsernames();
        for (int i = 0; i < players.size(); i++)
        {
            ClientHandler p = players.get(i);
            Message msg = new Message();
            msg.setType(MessageType.AMONG_MEETING_RESULT);
            msg.setMatchId(matchId);
            msg.setAmongEjectedUsername(ejectedUsername);
            msg.setAmongEjectedRole(ejectedRole);
            msg.setAmongAliveUsernames(alive);
            p.sendMessage(msg);
        }

        inMeeting = false;
        votes.clear();

        if (!checkImpostorWin())
        {
            checkCrewWinByEjection();
        }
        if (!over)
        {
            broadcastState();
        }
    }

    private boolean checkImpostorWin()
    {
        int aliveImpostors = 0;
        int aliveCrew = 0;
        for (ClientHandler p : alivePlayers)
        {
            if (impostors.contains(p)) aliveImpostors++;
            else aliveCrew++;
        }
        if (aliveImpostors > 0 && aliveImpostors >= aliveCrew)
        {
            endGame("IMPOSTORS");
            return true;
        }
        return false;
    }

    private void checkCrewWinByEjection()
    {
        boolean anyImpostorAlive = false;
        for (ClientHandler p : alivePlayers)
        {
            if (impostors.contains(p))
            {
                anyImpostorAlive = true;
                break;
            }
        }
        if (!anyImpostorAlive)
        {
            endGame("CREWMATES");
        }
    }

    private void endGame(String winningTeam)
    {
        over = true;
        matchManager.endMatch(matchId);

        for (int i = 0; i < players.size(); i++)
        {
            ClientHandler p = players.get(i);
            Message msg = new Message();
            msg.setType(MessageType.AMONG_GAME_OVER);
            msg.setMatchId(matchId);
            msg.setAmongWinningTeam(winningTeam);
            p.sendMessage(msg);
        }
    }

    private void broadcastState()
    {
        int progress = totalCrewTasks == 0 ? 100 : (completedCrewTasks * 100 / totalCrewTasks);
        List<String> alive = aliveUsernames();
        for (int i = 0; i < players.size(); i++)
        {
            ClientHandler p = players.get(i);
            Message msg = new Message();
            msg.setType(MessageType.AMONG_STATE_UPDATE);
            msg.setMatchId(matchId);
            msg.setAmongTeamTaskProgress(progress);
            msg.setAmongAliveUsernames(alive);
            p.sendMessage(msg);
        }
    }

    private ClientHandler findByUsername(String username)
    {
        if (username == null)
        {
            return null;
        }
        for (int i = 0; i < players.size(); i++)
        {
            if (username.equalsIgnoreCase(players.get(i).getLoggedInUsername()))
            {
                return players.get(i);
            }
        }
        return null;
    }

    public synchronized void handleDisconnect(ClientHandler who)
    {
        if (over || !players.contains(who))
        {
            return;
        }
        alivePlayers.remove(who);
        votes.remove(who);

        if (!checkImpostorWin())
        {
            checkCrewWinByEjection();
        }
        if (!over)
        {
            if (inMeeting && !alivePlayers.isEmpty() && votes.size() >= alivePlayers.size())
            {
                resolveMeeting();
            }
            else if (!inMeeting)
            {
                broadcastState();
            }
        }
    }
}
