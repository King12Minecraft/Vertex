import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * TournamentManager
 * -----------------
 * 4-player single-elimination brackets. Scoped to Battleship and Rock
 * Paper Scissors only - both always produce a decisive winner (no
 * draws), which avoids bracket-advancement edge cases a drawable game
 * like Chess would introduce. Team-based tournaments (2v2 etc.) would
 * need the party system integrated into matchmaking first, which
 * hasn't been built yet - solo brackets only, for now.
 *
 * Matches are constructed directly (bypassing the normal public
 * matchmaking queue entirely) so specific bracket opponents are always
 * paired together, using a TournamentMatchListener to find out who won
 * without the match classes needing to know anything about
 * tournaments themselves.
 */
public class TournamentManager
{
    private static final int BRACKET_SIZE = 4;

    public static class Tournament
    {
        final String id;
        final String gameId;
        final List<ClientHandler> registered = new ArrayList<ClientHandler>();
        List<ClientHandler> round1Winners = new ArrayList<ClientHandler>();
        String status = "REGISTRATION"; // REGISTRATION, ROUND_1, FINAL, COMPLETE
        ClientHandler champion;

        Tournament(String id, String gameId)
        {
            this.id = id;
            this.gameId = gameId;
        }
    }

    private final Map<String, Tournament> tournaments = new HashMap<String, Tournament>();
    private int nextTournamentId = 1;
    private final BattleshipMatchManager battleshipMatchManager;
    private final RockPaperScissorsMatchManager rpsMatchManager;
    private final LeaderboardManager leaderboardManager;
    private final ChatManager chatManager;
    private final ReplayManager replayManager;
    private final Random random = new Random();

    public TournamentManager(BattleshipMatchManager battleshipMatchManager, RockPaperScissorsMatchManager rpsMatchManager,
                              LeaderboardManager leaderboardManager, ChatManager chatManager, ReplayManager replayManager)
    {
        this.battleshipMatchManager = battleshipMatchManager;
        this.rpsMatchManager = rpsMatchManager;
        this.leaderboardManager = leaderboardManager;
        this.chatManager = chatManager;
        this.replayManager = replayManager;
    }

    public synchronized void create(ClientHandler creator, String gameId)
    {
        if (!"battleship".equals(gameId) && !"rock-paper-scissors".equals(gameId))
        {
            return;
        }

        String id = "tournament-" + (nextTournamentId++);
        Tournament tournament = new Tournament(id, gameId);
        tournament.registered.add(creator);
        tournaments.put(id, tournament);

        broadcastList();
    }

    public synchronized void join(ClientHandler player, String tournamentId)
    {
        Tournament tournament = tournaments.get(tournamentId);
        if (tournament == null || !"REGISTRATION".equals(tournament.status)
            || tournament.registered.contains(player) || tournament.registered.size() >= BRACKET_SIZE)
        {
            return;
        }

        tournament.registered.add(player);

        if (tournament.registered.size() >= BRACKET_SIZE)
        {
            startRound1(tournament);
        }

        broadcastList();
    }

    private void startRound1(Tournament tournament)
    {
        tournament.status = "ROUND_1";
        List<ClientHandler> players = new ArrayList<ClientHandler>(tournament.registered);
        Collections.shuffle(players, random);

        createMatch(tournament, players.get(0), players.get(1));
        createMatch(tournament, players.get(2), players.get(3));

        broadcastList();
    }

    private void createMatch(final Tournament tournament, final ClientHandler playerA, final ClientHandler playerB)
    {
        TournamentMatchListener listener = new TournamentMatchListener()
        {
            public void onMatchComplete(ClientHandler winner, ClientHandler loser)
            {
                advanceWinner(tournament, winner);
            }
        };

        if ("battleship".equals(tournament.gameId))
        {
            String matchId = tournament.id + "-match-" + System.nanoTime();
            BattleshipMatch match = new BattleshipMatch(matchId, playerA, playerB, battleshipMatchManager, leaderboardManager, replayManager, null);
            match.setTournamentListener(listener);
            playerA.setCurrentBattleshipMatch(match);
            playerB.setCurrentBattleshipMatch(match);
            match.start();
        }
        else
        {
            String matchId = tournament.id + "-match-" + System.nanoTime();
            RockPaperScissorsMatch match = new RockPaperScissorsMatch(matchId, playerA, playerB, rpsMatchManager, leaderboardManager, replayManager, null);
            match.setTournamentListener(listener);
            playerA.setCurrentRpsMatch(match);
            playerB.setCurrentRpsMatch(match);
            match.start();
        }
    }

    private synchronized void advanceWinner(Tournament tournament, ClientHandler winner)
    {
        tournament.round1Winners.add(winner);

        if (tournament.round1Winners.size() == 1)
        {
            // First semifinal just finished - wait for the second before starting the final.
            return;
        }

        if ("ROUND_1".equals(tournament.status))
        {
            tournament.status = "FINAL";
            ClientHandler finalistA = tournament.round1Winners.get(0);
            ClientHandler finalistB = tournament.round1Winners.get(1);
            createMatch(tournament, finalistA, finalistB);
            broadcastList();
        }
        else if ("FINAL".equals(tournament.status))
        {
            tournament.status = "COMPLETE";
            tournament.champion = winner;
            announceChampion(tournament);
            broadcastList();
        }
    }

    private void announceChampion(Tournament tournament)
    {
        for (int i = 0; i < tournament.registered.size(); i++)
        {
            Message msg = new Message();
            msg.setType(MessageType.TOURNAMENT_COMPLETE);
            msg.setTournamentId(tournament.id);
            msg.setUsername(tournament.champion.getLoggedInUsername());
            tournament.registered.get(i).sendMessage(msg);
        }
    }

    public synchronized List<String> listOpen()
    {
        List<String> result = new ArrayList<String>();
        for (Tournament t : tournaments.values())
        {
            String championName = t.champion != null ? t.champion.getLoggedInUsername() : "";
            result.add(t.id + "|" + t.gameId + "|" + t.status + "|" + t.registered.size() + "|" + championName);
        }
        return result;
    }

    private void broadcastList()
    {
        Message msg = new Message();
        msg.setType(MessageType.TOURNAMENT_LIST_RESPONSE);
        msg.setTournamentEntries(listOpen());
        chatManager.broadcastToAll(msg);
    }

    public synchronized void handleDisconnect(ClientHandler player)
    {
        // Registrations from a disconnected player during REGISTRATION simply
        // stay in the list - if they never come back the bracket just never
        // fills. Once a match is underway, that match's own disconnect
        // handling (in BattleshipMatch/RockPaperScissorsMatch) already covers
        // awarding the win to their opponent, which naturally still advances
        // the bracket via the same TournamentMatchListener callback.
    }
}
