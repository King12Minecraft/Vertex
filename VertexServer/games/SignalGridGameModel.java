package games;

import ai.search.GameModel;

import java.util.ArrayList;
import java.util.List;

/**
 * SignalGridGameModel
 * --------------------
 * The one file Signal Grid needs to plug into the shared ai.search
 * engine - describes this game's rules only. Unlike Memory Match,
 * Fusion Grid, Word Duel, and Dice Duel (which all involve hidden
 * information or randomness mid-decision and get their own bespoke
 * heuristic bots instead), Signal Grid is genuinely deterministic and
 * perfect-information - a clean fit for the same generic search engine
 * Connect Four/Reversi/Dots and Boxes/Checkers/Chess already use.
 * State/move encoding mirrors SignalGridMatch's own exactly.
 *
 * Player 0 moves first, matching SignalGridMatch's turnPlayerIndex
 * starting at 0.
 */
public class SignalGridGameModel implements GameModel<int[], SignalGridMove>
{
    public static final int PLAYER_A = 0;
    public static final int PLAYER_B = 1;

    private static final int SIZE = SignalGridMatch.GRID_SIZE;
    private static final int UP = SignalGridMatch.UP, DOWN = SignalGridMatch.DOWN;
    private static final int LEFT = SignalGridMatch.LEFT, RIGHT = SignalGridMatch.RIGHT;

    public static int[] newBoard()
    {
        int[] owners = new int[SIZE * SIZE];
        java.util.Arrays.fill(owners, -1);
        return owners;
    }

    @Override
    public List<SignalGridMove> legalMoves(int[] state, int playerIndex)
    {
        List<SignalGridMove> moves = new ArrayList<SignalGridMove>();
        for (int i = 0; i < state.length; i++)
        {
            if (state[i] == -1)
            {
                moves.add(new SignalGridMove(i, UP));
                moves.add(new SignalGridMove(i, DOWN));
                moves.add(new SignalGridMove(i, LEFT));
                moves.add(new SignalGridMove(i, RIGHT));
            }
        }
        return moves;
    }

    @Override
    public int[] applyMove(int[] state, SignalGridMove move, int playerIndex)
    {
        int[] next = state.clone();
        next[move.index] = playerIndex;
        fireSignal(next, move.index, move.direction, playerIndex);
        return next;
    }

    @Override
    public boolean isTerminal(int[] state)
    {
        for (int owner : state)
        {
            if (owner == -1) return false;
        }
        return true;
    }

    @Override
    public int nextPlayer(int[] state, int playerJustMoved)
    {
        return 1 - playerJustMoved;
    }

    @Override
    public Integer winner(int[] state)
    {
        int[] counts = countOwners(state);
        if (counts[0] == counts[1]) return null;
        return counts[0] > counts[1] ? PLAYER_A : PLAYER_B;
    }

    @Override
    public int evaluate(int[] state, int forPlayerIndex)
    {
        if (isTerminal(state))
        {
            Integer winner = winner(state);
            if (winner == null) return 0;
            return winner == forPlayerIndex ? 1_000_000 : -1_000_000;
        }
        int[] counts = countOwners(state);
        int mine = counts[forPlayerIndex];
        int theirs = counts[1 - forPlayerIndex];
        return (mine - theirs) * 100;
    }

    private static int[] countOwners(int[] state)
    {
        int[] counts = new int[2];
        for (int owner : state)
        {
            if (owner == 0) counts[0]++;
            else if (owner == 1) counts[1]++;
        }
        return counts;
    }

    /** Mirrors SignalGridMatch.fireSignal exactly - walks from fromIndex in direction until it hits the grid edge (does nothing) or another node (flips it to playerIndex if it was the opponent's, does nothing if it was already playerIndex's own). */
    private static void fireSignal(int[] state, int fromIndex, int direction, int playerIndex)
    {
        int row = fromIndex / SIZE, col = fromIndex % SIZE;
        int dr = direction == UP ? -1 : direction == DOWN ? 1 : 0;
        int dc = direction == LEFT ? -1 : direction == RIGHT ? 1 : 0;

        int r = row + dr, c = col + dc;
        while (r >= 0 && r < SIZE && c >= 0 && c < SIZE)
        {
            int hitIndex = r * SIZE + c;
            if (state[hitIndex] != -1)
            {
                if (state[hitIndex] != playerIndex)
                {
                    state[hitIndex] = playerIndex;
                }
                return;
            }
            r += dr;
            c += dc;
        }
    }
}
