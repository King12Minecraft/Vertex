import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * BattleshipMatch
 * ---------------
 * 1v1 Battleship - standard 10x10 grid, the classic 5-ship fleet
 * (Carrier-5, Battleship-4, Cruiser-3, Submarine-3, Destroyer-2).
 * Deliberately auto-places both fleets randomly rather than building a
 * manual placement UI - keeps this focused on the actual "hunt and
 * sink" gameplay, which is the part players actually interact with
 * turn to turn. Server is fully authoritative: owns both fleets,
 * validates every shot (must be your turn, must be an untried cell),
 * and resolves hit/miss/sunk. A hit (or sunk) grants another shot,
 * matching the classic rule that only a miss passes the turn.
 */
public class BattleshipMatch
{
    private static final int SIZE = 10;
    private static final int[] SHIP_LENGTHS = { 5, 4, 3, 3, 2 };
    private static final String[] SHIP_NAMES = { "Carrier", "Battleship", "Cruiser", "Submarine", "Destroyer" };

    private final String matchId;
    private final ClientHandler playerA;
    private final ClientHandler playerB;
    private final BattleshipMatchManager matchManager;
    private final LeaderboardManager leaderboardManager;
    private final Random random = new Random();
    private final List<ClientHandler> spectators = new ArrayList<ClientHandler>();
    private final List<String> shotLog = new ArrayList<String>();
    private final ReplayManager replayManager;

    /** fleet[cell] = ship index (0-4) if occupied, -1 if water. */
    private final int[] fleetA = new int[SIZE * SIZE];
    private final int[] fleetB = new int[SIZE * SIZE];
    private final int[] shipHitsA = new int[SHIP_LENGTHS.length];
    private final int[] shipHitsB = new int[SHIP_LENGTHS.length];
    private final boolean[] firedByA = new boolean[SIZE * SIZE];
    private final boolean[] firedByB = new boolean[SIZE * SIZE];

    private boolean aTurn = true;
    private boolean over = false;
    private TournamentMatchListener tournamentListener;

    public BattleshipMatch(String matchId, ClientHandler playerA, ClientHandler playerB, BattleshipMatchManager matchManager, LeaderboardManager leaderboardManager, ReplayManager replayManager)
    {
        this.matchId = matchId;
        this.playerA = playerA;
        this.playerB = playerB;
        this.matchManager = matchManager;
        this.leaderboardManager = leaderboardManager;
        this.replayManager = replayManager;
        placeFleet(fleetA);
        placeFleet(fleetB);
    }

    private void placeFleet(int[] fleet)
    {
        for (int i = 0; i < SIZE * SIZE; i++)
        {
            fleet[i] = -1;
        }
        for (int shipIndex = 0; shipIndex < SHIP_LENGTHS.length; shipIndex++)
        {
            placeShip(fleet, shipIndex);
        }
    }

    private void placeShip(int[] fleet, int shipIndex)
    {
        int length = SHIP_LENGTHS[shipIndex];
        while (true)
        {
            boolean horizontal = random.nextBoolean();
            int row = random.nextInt(SIZE);
            int col = random.nextInt(SIZE);

            if (horizontal && col + length > SIZE) continue;
            if (!horizontal && row + length > SIZE) continue;

            boolean fits = true;
            for (int i = 0; i < length; i++)
            {
                int r = horizontal ? row : row + i;
                int c = horizontal ? col + i : col;
                if (fleet[r * SIZE + c] != -1)
                {
                    fits = false;
                    break;
                }
            }
            if (!fits)
            {
                continue;
            }

            for (int i = 0; i < length; i++)
            {
                int r = horizontal ? row : row + i;
                int c = horizontal ? col + i : col;
                fleet[r * SIZE + c] = shipIndex;
            }
            return;
        }
    }

    public void start()
    {
        sendMatchFound(playerA, playerB.getLoggedInUsername(), fleetA, true);
        sendMatchFound(playerB, playerA.getLoggedInUsername(), fleetB, false);
    }

    public String getPlayerAUsername() { return playerA.getLoggedInUsername(); }
    public String getPlayerBUsername() { return playerB.getLoggedInUsername(); }

    /** Unlike Chess/RPS, a spectator here needs BOTH fleets revealed - hidden information is the whole point of Battleship for the players, but a spectator has no stake in it. Sends two messages, one per fleet, reusing BATTLESHIP_MATCH_FOUND's existing fields: symbol "SPECTATOR_A"/"SPECTATOR_B" tells the client which fleet this is (instead of "MINE"/"THEIRS", which only make sense from a player's own perspective), and opponentUsername carries that fleet's actual owner. No new Message fields needed. */
    public synchronized void addSpectator(ClientHandler spectator)
    {
        spectators.add(spectator);

        Message fleetAMsg = new Message();
        fleetAMsg.setType(MessageType.BATTLESHIP_MATCH_FOUND);
        fleetAMsg.setMatchId(matchId);
        fleetAMsg.setOpponentUsername(playerA.getLoggedInUsername());
        fleetAMsg.setBoardState(fleetToString(fleetA));
        fleetAMsg.setSymbol("SPECTATOR_A");
        spectator.sendMessage(fleetAMsg);

        Message fleetBMsg = new Message();
        fleetBMsg.setType(MessageType.BATTLESHIP_MATCH_FOUND);
        fleetBMsg.setMatchId(matchId);
        fleetBMsg.setOpponentUsername(playerB.getLoggedInUsername());
        fleetBMsg.setBoardState(fleetToString(fleetB));
        fleetBMsg.setSymbol("SPECTATOR_B");
        spectator.sendMessage(fleetBMsg);
    }

    /** toIsA tells this recipient whether it's their turn first - Player A always goes first. */
    private void sendMatchFound(ClientHandler to, String opponentUsername, int[] fleet, boolean toIsA)
    {
        Message msg = new Message();
        msg.setType(MessageType.BATTLESHIP_MATCH_FOUND);
        msg.setMatchId(matchId);
        msg.setOpponentUsername(opponentUsername);
        msg.setBoardState(fleetToString(fleet));
        msg.setSymbol(toIsA ? "MINE" : "THEIRS");
        to.sendMessage(msg);
    }

    private String fleetToString(int[] fleet)
    {
        StringBuilder sb = new StringBuilder(SIZE * SIZE);
        for (int i = 0; i < fleet.length; i++)
        {
            sb.append(fleet[i] == -1 ? '.' : SHIP_NAMES[fleet[i]].charAt(0));
        }
        return sb.toString();
    }

    public synchronized void fire(ClientHandler requester, int cellIndex)
    {
        if (over || cellIndex < 0 || cellIndex >= SIZE * SIZE)
        {
            return;
        }

        boolean isA = requester == playerA;
        if (isA != aTurn)
        {
            return;
        }

        boolean[] firedBy = isA ? firedByA : firedByB;
        if (firedBy[cellIndex])
        {
            return;
        }
        firedBy[cellIndex] = true;

        int[] targetFleet = isA ? fleetB : fleetA;
        int[] targetHits = isA ? shipHitsB : shipHitsA;

        int shipIndex = targetFleet[cellIndex];
        String result;
        String sunkShip = null;

        if (shipIndex == -1)
        {
            result = "MISS";
        }
        else
        {
            targetHits[shipIndex]++;
            if (targetHits[shipIndex] >= SHIP_LENGTHS[shipIndex])
            {
                result = "SUNK";
                sunkShip = SHIP_NAMES[shipIndex];
            }
            else
            {
                result = "HIT";
            }
        }

        boolean fleetDestroyed = allShipsSunk(targetHits);

        if (!fleetDestroyed && "MISS".equals(result))
        {
            aTurn = !aTurn;
        }

        shotLog.add(requester.getLoggedInUsername() + ":" + cellIndex + ":" + result);
        broadcastFireResult(requester, cellIndex, result, sunkShip);

        if (fleetDestroyed)
        {
            over = true;
            matchManager.endMatch(matchId);
            endGame(isA ? playerA : playerB, isA ? playerB : playerA);
        }
    }

    private boolean allShipsSunk(int[] hits)
    {
        for (int i = 0; i < SHIP_LENGTHS.length; i++)
        {
            if (hits[i] < SHIP_LENGTHS[i])
            {
                return false;
            }
        }
        return true;
    }

    private void broadcastFireResult(ClientHandler shooter, int cellIndex, String result, String sunkShip)
    {
        sendFireResult(playerA, shooter, cellIndex, result, sunkShip);
        sendFireResult(playerB, shooter, cellIndex, result, sunkShip);
        for (int i = 0; i < spectators.size(); i++)
        {
            sendFireResult(spectators.get(i), shooter, cellIndex, result, sunkShip);
        }
    }

    private void sendFireResult(ClientHandler to, ClientHandler shooter, int cellIndex, String result, String sunkShip)
    {
        Message msg = new Message();
        msg.setType(MessageType.BATTLESHIP_FIRE_RESULT);
        msg.setMatchId(matchId);
        msg.setUsername(shooter.getLoggedInUsername());
        msg.setCellIndex(cellIndex);
        msg.setBattleshipResult(result);
        msg.setBattleshipSunkShip(sunkShip);
        msg.setSymbol(aTurn ? "A" : "B");
        to.sendMessage(msg);
    }

    public void setTournamentListener(TournamentMatchListener listener)
    {
        this.tournamentListener = listener;
    }

    private void endGame(ClientHandler winner, ClientHandler loser)
    {
        recordRating(winner, loser);
        saveReplay(winner);

        Message winMsg = new Message();
        winMsg.setType(MessageType.BATTLESHIP_MATCH_OVER);
        winMsg.setMatchId(matchId);
        winMsg.setMatchResult("WIN");
        winner.sendMessage(winMsg);

        Message loseMsg = new Message();
        loseMsg.setType(MessageType.BATTLESHIP_MATCH_OVER);
        loseMsg.setMatchId(matchId);
        loseMsg.setMatchResult("LOSE");
        loser.sendMessage(loseMsg);

        notifySpectatorsEnded();

        if (tournamentListener != null)
        {
            tournamentListener.onMatchComplete(winner, loser);
        }
    }

    private void notifySpectatorsEnded()
    {
        for (int i = 0; i < spectators.size(); i++)
        {
            Message ended = new Message();
            ended.setType(MessageType.SPECTATE_ENDED);
            ended.setMatchId(matchId);
            spectators.get(i).sendMessage(ended);
        }
    }

    private void recordRating(ClientHandler winner, ClientHandler loser)
    {
        if (leaderboardManager == null || winner.getAccountId() == null || loser.getAccountId() == null)
        {
            return;
        }
        leaderboardManager.recordRatedMatch("battleship", winner.getAccountId(), loser.getAccountId(), 1.0);
    }

    /** Packs everything a replay viewer needs into ReplayManager's generic List<String> format, no protocol changes needed: index 0 is fleetA's layout, index 1 is fleetB's, everything after is one shot per entry ("shooterName:cellIndex:result") in the order they were fired - unlike Chess's per-move board snapshots, the viewer reconstructs each step by replaying the shot log against the two static fleet layouts rather than storing a full board state per step. */
    private void saveReplay(ClientHandler winner)
    {
        if (replayManager == null)
        {
            return;
        }
        List<String> data = new ArrayList<String>();
        data.add(fleetToString(fleetA));
        data.add(fleetToString(fleetB));
        data.addAll(shotLog);

        String result = winner == playerA ? "PLAYER_A" : "PLAYER_B";
        replayManager.save("battleship", playerA.getLoggedInUsername(), playerB.getLoggedInUsername(), result, data);
    }

    public synchronized void handleDisconnect(ClientHandler who)
    {
        if (over)
        {
            return;
        }
        over = true;
        matchManager.endMatch(matchId);

        ClientHandler remaining = (who == playerA) ? playerB : playerA;
        Message msg = new Message();
        msg.setType(MessageType.BATTLESHIP_MATCH_OVER);
        msg.setMatchId(matchId);
        msg.setMatchResult("OPPONENT_LEFT");
        remaining.sendMessage(msg);
        notifySpectatorsEnded();
    }
}
