import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FightMatch
 * ----------
 * The real-time synced brawler - the one game in Vertex with a
 * genuinely continuous server tick loop (a daemon Thread ticking
 * ~15/sec) rather than the event-driven request/response pattern
 * everything else uses. Deliberately kept mechanically simple to keep
 * this achievable: single flat arena, no jumping, no platforms, no
 * weapon pickups - movement and one melee attack. Server is fully
 * authoritative: it owns every player's position/health, applies
 * their latest known input each tick, resolves hits, and broadcasts a
 * full snapshot every tick. Clients do no local physics prediction -
 * they render exactly what the last tick said, which keeps client
 * code (and this whole feature) far simpler at the cost of the ~66ms
 * tick latency being slightly visible.
 */
public class FightMatch
{
    private static final double ARENA_WIDTH = 800.0;
    private static final double MOVE_SPEED = 6.0;
    private static final double ATTACK_RANGE = 55.0;
    private static final int ATTACK_COOLDOWN_TICKS = 8;
    private static final int ATTACK_DAMAGE = 20;
    private static final int MAX_HEALTH = 100;
    private static final int RESPAWN_DELAY_TICKS = 23;
    private static final int KOS_TO_WIN = 10;
    private static final int TICK_MS = 66;

    private static class PlayerState
    {
        double x;
        int health = MAX_HEALTH;
        boolean facingRight = true;
        boolean movingLeft;
        boolean movingRight;
        boolean wantsAttack;
        int attackCooldown = 0;
        int attackFlashTicks = 0;
        boolean alive = true;
        int respawnTimer = 0;
        int team;
        int kos = 0;
    }

    private final String matchId;
    private final String mode;
    private final List<ClientHandler> players;
    private final Map<ClientHandler, PlayerState> states = new HashMap<ClientHandler, PlayerState>();
    private final FightArenaMatchManager matchManager;
    private final LeaderboardManager leaderboardManager;
    private FightTournamentListener tournamentListener;

    public void setTournamentListener(FightTournamentListener listener)
    {
        this.tournamentListener = listener;
    }

    private Thread tickThread;
    private volatile boolean running = true;
    private boolean over = false;

    public FightMatch(String matchId, String mode, List<ClientHandler> players,
                       List<Integer> teamAssignments, FightArenaMatchManager matchManager, LeaderboardManager leaderboardManager)
    {
        this.matchId = matchId;
        this.mode = mode;
        this.players = players;
        this.matchManager = matchManager;
        this.leaderboardManager = leaderboardManager;

        for (int i = 0; i < players.size(); i++)
        {
            PlayerState st = new PlayerState();
            st.team = teamAssignments.get(i);
            st.x = spawnX(i, players.size());
            st.facingRight = st.x < ARENA_WIDTH / 2.0;
            states.put(players.get(i), st);
        }
    }

    private double spawnX(int index, int total)
    {
        double spacing = ARENA_WIDTH / (double) (total + 1);
        return spacing * (index + 1);
    }

    public void start()
    {
        List<String> roster = new ArrayList<String>();
        List<String> teamAssignmentsStr = new ArrayList<String>();
        for (int i = 0; i < players.size(); i++)
        {
            ClientHandler p = players.get(i);
            roster.add(p.getLoggedInUsername());
            teamAssignmentsStr.add(p.getLoggedInUsername() + "|" + states.get(p).team);
        }

        for (int i = 0; i < players.size(); i++)
        {
            ClientHandler p = players.get(i);
            Message msg = new Message();
            msg.setType(MessageType.FIGHT_MATCH_FOUND);
            msg.setMatchId(matchId);
            msg.setFightMode(mode);
            msg.setFightRosterUsernames(roster);
            msg.setFightTeamAssignments(teamAssignmentsStr);
            p.sendMessage(msg);
        }

        tickThread = new Thread(new Runnable()
        {
            public void run() { tickLoop(); }
        });
        tickThread.setDaemon(true);
        tickThread.start();
    }

    private void tickLoop()
    {
        while (running)
        {
            tick();
            try
            {
                Thread.sleep(TICK_MS);
            }
            catch (InterruptedException e)
            {
                break;
            }
        }
    }

    public synchronized void updateInput(ClientHandler who, boolean left, boolean right, boolean attack)
    {
        PlayerState st = states.get(who);
        if (st == null)
        {
            return;
        }
        st.movingLeft = left;
        st.movingRight = right;
        st.wantsAttack = attack;
    }

    private synchronized void tick()
    {
        if (over)
        {
            return;
        }

        for (int i = 0; i < players.size(); i++)
        {
            ClientHandler p = players.get(i);
            PlayerState st = states.get(p);

            if (!st.alive)
            {
                st.respawnTimer--;
                if (st.respawnTimer <= 0)
                {
                    respawn(st, i);
                }
                continue;
            }

            if (st.movingLeft && !st.movingRight)
            {
                st.x -= MOVE_SPEED;
                st.facingRight = false;
            }
            else if (st.movingRight && !st.movingLeft)
            {
                st.x += MOVE_SPEED;
                st.facingRight = true;
            }
            st.x = Math.max(0, Math.min(ARENA_WIDTH, st.x));

            if (st.attackCooldown > 0) st.attackCooldown--;
            if (st.attackFlashTicks > 0) st.attackFlashTicks--;

            if (st.wantsAttack && st.attackCooldown <= 0)
            {
                st.attackCooldown = ATTACK_COOLDOWN_TICKS;
                st.attackFlashTicks = 3;
                performAttack(p, st);
            }
        }

        if (checkWinCondition())
        {
            endMatch();
            return;
        }

        broadcastTick();
    }

    private void performAttack(ClientHandler attacker, PlayerState attackerState)
    {
        for (int i = 0; i < players.size(); i++)
        {
            ClientHandler p = players.get(i);
            if (p == attacker)
            {
                continue;
            }
            PlayerState target = states.get(p);
            if (!target.alive || target.team == attackerState.team)
            {
                continue;
            }

            double dx = target.x - attackerState.x;
            boolean inFront = attackerState.facingRight ? dx > 0 : dx < 0;
            if (inFront && Math.abs(dx) <= ATTACK_RANGE)
            {
                target.health -= ATTACK_DAMAGE;
                if (target.health <= 0)
                {
                    target.alive = false;
                    target.respawnTimer = RESPAWN_DELAY_TICKS;
                    attackerState.kos++;
                }
            }
        }
    }

    private void respawn(PlayerState st, int index)
    {
        st.health = MAX_HEALTH;
        st.alive = true;
        st.x = spawnX(index, players.size());
    }

    private boolean checkWinCondition()
    {
        if ("FFA".equals(mode))
        {
            for (PlayerState st : states.values())
            {
                if (st.kos >= KOS_TO_WIN) return true;
            }
        }
        else
        {
            int team0Kos = 0;
            int team1Kos = 0;
            for (PlayerState st : states.values())
            {
                if (st.team == 0) team0Kos += st.kos; else team1Kos += st.kos;
            }
            if (team0Kos >= KOS_TO_WIN || team1Kos >= KOS_TO_WIN) return true;
        }
        return false;
    }

    private void broadcastTick()
    {
        List<String> tickData = new ArrayList<String>();
        List<String> scores = new ArrayList<String>();
        for (int i = 0; i < players.size(); i++)
        {
            ClientHandler p = players.get(i);
            PlayerState st = states.get(p);
            tickData.add(p.getLoggedInUsername() + "|" + (int) st.x + "|" + st.health + "|"
                + (st.facingRight ? 1 : 0) + "|" + (st.attackFlashTicks > 0 ? 1 : 0) + "|" + (st.alive ? 1 : 0));
            scores.add(p.getLoggedInUsername() + "|" + st.kos);
        }

        for (int i = 0; i < players.size(); i++)
        {
            ClientHandler p = players.get(i);
            Message msg = new Message();
            msg.setType(MessageType.FIGHT_TICK_UPDATE);
            msg.setMatchId(matchId);
            msg.setFightTickData(tickData);
            msg.setFightScores(scores);
            p.sendMessage(msg);
        }
    }

    private void endMatch()
    {
        over = true;
        running = false;
        matchManager.endMatch(matchId);

        String resultText = buildResultText();
        recordRating();
        notifyTournamentListener();

        for (int i = 0; i < players.size(); i++)
        {
            ClientHandler p = players.get(i);
            Message msg = new Message();
            msg.setType(MessageType.FIGHT_MATCH_OVER);
            msg.setMatchId(matchId);
            msg.setFightResultText(resultText);
            p.sendMessage(msg);
        }
    }

    /** Computed independently of recordRating() (which is ELO-specific and skips entirely when leaderboardManager is null) so a tournament decider always reports its result regardless of whether rating tracking happens to be wired up. FFA has no teams, so there's nothing to report there - team tournaments only ever use 2V2/3V3. */
    private void notifyTournamentListener()
    {
        if (tournamentListener == null || "FFA".equals(mode))
        {
            return;
        }
        int team0Kos = 0;
        int team1Kos = 0;
        for (PlayerState st : states.values())
        {
            if (st.team == 0) team0Kos += st.kos; else team1Kos += st.kos;
        }
        if (team0Kos != team1Kos)
        {
            tournamentListener.onMatchComplete(team0Kos > team1Kos ? 0 : 1);
        }
    }

    /** Winners are every account on the team with more KOs (or the single top scorer in FFA); everyone else is a loser. Ties record nothing - there's no clear winner/loser pairing to rate. See LeaderboardManager's own javadoc for why this is a pairwise-ELO approximation rather than true multiplayer rating. */
    private void recordRating()
    {
        if (leaderboardManager == null)
        {
            return;
        }

        List<Integer> winners = new ArrayList<Integer>();
        List<Integer> losers = new ArrayList<Integer>();

        if ("FFA".equals(mode))
        {
            ClientHandler winner = null;
            int best = -1;
            boolean tie = false;
            for (int i = 0; i < players.size(); i++)
            {
                PlayerState st = states.get(players.get(i));
                if (st.kos > best)
                {
                    best = st.kos;
                    winner = players.get(i);
                    tie = false;
                }
                else if (st.kos == best)
                {
                    tie = true;
                }
            }
            if (winner == null || tie)
            {
                return;
            }
            for (int i = 0; i < players.size(); i++)
            {
                ClientHandler p = players.get(i);
                if (p == winner)
                {
                    if (p.getAccountId() != null) winners.add(p.getAccountId());
                }
                else if (p.getAccountId() != null)
                {
                    losers.add(p.getAccountId());
                }
            }
        }
        else
        {
            int team0Kos = 0;
            int team1Kos = 0;
            for (PlayerState st : states.values())
            {
                if (st.team == 0) team0Kos += st.kos; else team1Kos += st.kos;
            }
            if (team0Kos == team1Kos)
            {
                return;
            }
            int winningTeam = team0Kos > team1Kos ? 0 : 1;
            for (int i = 0; i < players.size(); i++)
            {
                ClientHandler p = players.get(i);
                if (p.getAccountId() == null)
                {
                    continue;
                }
                if (states.get(p).team == winningTeam) winners.add(p.getAccountId());
                else losers.add(p.getAccountId());
            }
        }

        if (!winners.isEmpty() && !losers.isEmpty())
        {
            leaderboardManager.recordMultiplayerResult("fight-arena", winners, losers);
        }
    }

    private String buildResultText()
    {
        if ("FFA".equals(mode))
        {
            ClientHandler winner = null;
            int best = -1;
            for (int i = 0; i < players.size(); i++)
            {
                ClientHandler p = players.get(i);
                PlayerState st = states.get(p);
                if (st.kos > best)
                {
                    best = st.kos;
                    winner = p;
                }
            }
            return winner != null ? winner.getLoggedInUsername() + " wins with " + best + " KOs!" : "No winner.";
        }

        int team0Kos = 0;
        int team1Kos = 0;
        for (PlayerState st : states.values())
        {
            if (st.team == 0) team0Kos += st.kos; else team1Kos += st.kos;
        }
        if (team0Kos > team1Kos) return "Team Red wins! " + team0Kos + " - " + team1Kos;
        if (team1Kos > team0Kos) return "Team Blue wins! " + team1Kos + " - " + team0Kos;
        return "It's a tie!";
    }

    /** Doesn't force-end the match - remaining players keep fighting. The disconnected player's slot just stops responding (never respawns, never attacks again). */
    public synchronized void handleDisconnect(ClientHandler who)
    {
        if (over || !players.contains(who))
        {
            return;
        }
        PlayerState st = states.get(who);
        if (st != null)
        {
            st.alive = false;
            st.respawnTimer = Integer.MAX_VALUE;
            st.movingLeft = false;
            st.movingRight = false;
            st.wantsAttack = false;
        }
    }
}
