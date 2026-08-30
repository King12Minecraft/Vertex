/**
 * TournamentMatchListener
 * -----------------------
 * Optional callback a match can carry - null for a normal public-queue
 * match, set when TournamentManager constructs the match directly for
 * a bracket round. Kept deliberately minimal (the match itself has no
 * idea what a tournament is) so this same interface could back other
 * future match customizations too, not just tournaments.
 */
public interface TournamentMatchListener
{
    void onMatchComplete(ClientHandler winner, ClientHandler loser);
}
