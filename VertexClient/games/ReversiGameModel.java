package games;

import ai.search.GameModel;

import java.util.ArrayList;
import java.util.List;

/**
 * ReversiGameModel
 * ----------------
 * The one file Reversi needs to plug into the shared ai.search engine
 * - describes this game's rules only, same role ConnectFourGameModel
 * plays for Connect Four. Board encoding matches ReversiMatch's own
 * (flat char[SIZE*SIZE], '.'/'B'/'W') - a separate, decoupled
 * implementation of the same rules, since ReversiMatch itself is
 * network-coupled.
 *
 * Player 0 = BLACK (moves first), player 1 = WHITE - same convention
 * ReversiMatch uses. Real Reversi forces a pass when a side has no
 * legal placement anywhere - represented here as the single synthetic
 * PASS move (a no-op applyMove), so the shared search engine and
 * PracticeMatch never need their own concept of "passing"; only this
 * game's own Window needs to know PASS exists at all, to auto-play it
 * for a human whose only "move" is one.
 */
public class ReversiGameModel implements GameModel<char[], Integer>
{
    public static final int BLACK = 0;
    public static final int WHITE = 1;
    public static final int PASS = -1;

    private static final int SIZE = ReversiMatch.SIZE;
    private static final int[][] DIRECTIONS = {
        {-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}
    };
    private static final int[] CORNERS = { 0, SIZE - 1, SIZE * (SIZE - 1), SIZE * SIZE - 1 };

    public static char[] newBoard()
    {
        char[] board = new char[SIZE * SIZE];
        java.util.Arrays.fill(board, '.');
        int mid = SIZE / 2;
        board[(mid - 1) * SIZE + (mid - 1)] = 'W';
        board[(mid - 1) * SIZE + mid] = 'B';
        board[mid * SIZE + (mid - 1)] = 'B';
        board[mid * SIZE + mid] = 'W';
        return board;
    }

    @Override
    public List<Integer> legalMoves(char[] state, int playerIndex)
    {
        boolean isBlack = playerIndex == BLACK;
        List<Integer> moves = new ArrayList<Integer>();
        for (int i = 0; i < state.length; i++)
        {
            if (state[i] == '.' && !flanksFor(state, i, isBlack).isEmpty())
            {
                moves.add(i);
            }
        }
        if (moves.isEmpty())
        {
            moves.add(PASS);
        }
        return moves;
    }

    @Override
    public char[] applyMove(char[] state, Integer move, int playerIndex)
    {
        if (move == PASS)
        {
            return state;
        }
        boolean isBlack = playerIndex == BLACK;
        char color = isBlack ? 'B' : 'W';
        List<Integer> toFlip = flanksFor(state, move, isBlack);

        char[] next = state.clone();
        next[move] = color;
        for (int flip : toFlip)
        {
            next[flip] = color;
        }
        return next;
    }

    @Override
    public boolean isTerminal(char[] state)
    {
        return !hasAnyLegalMove(state, true) && !hasAnyLegalMove(state, false);
    }

    @Override
    public int nextPlayer(char[] state, int playerJustMoved)
    {
        return playerJustMoved == BLACK ? WHITE : BLACK;
    }

    @Override
    public Integer winner(char[] state)
    {
        int black = 0, white = 0;
        for (char c : state)
        {
            if (c == 'B') black++;
            else if (c == 'W') white++;
        }
        if (black == white) return null;
        return black > white ? BLACK : WHITE;
    }

    @Override
    public int evaluate(char[] state, int forPlayerIndex)
    {
        if (isTerminal(state))
        {
            Integer winner = winner(state);
            if (winner == null) return 0;
            return winner == forPlayerIndex ? 1_000_000 : -1_000_000;
        }

        char myColor = forPlayerIndex == BLACK ? 'B' : 'W';
        char oppColor = forPlayerIndex == BLACK ? 'W' : 'B';

        int score = 0;
        for (int i = 0; i < state.length; i++)
        {
            if (state[i] != myColor && state[i] != oppColor)
            {
                continue;
            }
            int weight = weightOf(i);
            score += (state[i] == myColor) ? weight : -weight;
        }
        return score;
    }

    private static int weightOf(int index)
    {
        for (int corner : CORNERS)
        {
            if (index == corner) return 25;
        }
        int row = index / SIZE, col = index % SIZE;
        boolean edge = row == 0 || row == SIZE - 1 || col == 0 || col == SIZE - 1;
        return edge ? 5 : 1;
    }

    private static boolean hasAnyLegalMove(char[] board, boolean forBlack)
    {
        for (int i = 0; i < board.length; i++)
        {
            if (board[i] == '.' && !flanksFor(board, i, forBlack).isEmpty())
            {
                return true;
            }
        }
        return false;
    }

    /** Every opponent cell that placing a piece of the given color at fromIndex would flip - empty if this isn't actually a legal move. */
    private static List<Integer> flanksFor(char[] board, int fromIndex, boolean isBlack)
    {
        List<Integer> result = new ArrayList<Integer>();
        int fromRow = fromIndex / SIZE, fromCol = fromIndex % SIZE;
        char own = isBlack ? 'B' : 'W';
        char enemy = isBlack ? 'W' : 'B';

        for (int[] dir : DIRECTIONS)
        {
            List<Integer> line = new ArrayList<Integer>();
            int r = fromRow + dir[0], c = fromCol + dir[1];
            while (r >= 0 && r < SIZE && c >= 0 && c < SIZE && board[r * SIZE + c] == enemy)
            {
                line.add(r * SIZE + c);
                r += dir[0];
                c += dir[1];
            }
            if (!line.isEmpty() && r >= 0 && r < SIZE && c >= 0 && c < SIZE && board[r * SIZE + c] == own)
            {
                result.addAll(line);
            }
        }
        return result;
    }
}
