/**
 * FightTournamentListener
 * ------------------------
 * Optional callback a FightMatch can carry - null for a normal
 * party/public-queue match, set when TeamTournamentManager constructs
 * the match directly for a team tournament decider. Reports which
 * team (0 or 1) won; the tournament manager already knows each team's
 * roster from its own registration records, so this is all it needs.
 */
public interface FightTournamentListener
{
    void onMatchComplete(int winningTeam);
}
