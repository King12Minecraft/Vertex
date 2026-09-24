package games;

import ai.search.GameModel;

import java.util.ArrayList;
import java.util.List;

/**
 * DotsAndBoxesGameModel
 * ---------------------
 * The one file Dots and Boxes needs to plug into the shared ai.search
 * engine - describes this game's rules only, same role
 * ConnectFourGameModel/ReversiGameModel play for their games. Line/box
 * numbering matches DotsAndBoxesMatch's own scheme exactly (see that
 * class's javadoc) - a separate, decoupled implementation since
 * DotsAndBoxesMatch itself is network-coupled.
 *
 * State is a char[57]: the same 40-line + 16-box-owner flat encoding
 * DotsAndBoxesMatch uses for its own boardState string (indices 0-39
 * lines, 40-55 box owners), plus one extra bookkeeping character at
 * index 56 - '1' if the move that produced this state completed at
 * least one box, '0' otherwise. That single extra flag is what lets
 * nextPlayer() correctly implement the real "completing a box earns
 * another turn" rule using nothing but the state it's handed - the
 * shared Minimax/PracticeMatch code never needs its own concept of
 * "the same player moves again," it just calls nextPlayer() same as
 * for any other game.
 *
 * Player 0 moves first, matching DotsAndBoxesMatch's turnPlayerIndex
 * starting at 0.
 */
public class DotsAndBoxesGameModel implements GameModel<char[], Integer>
{
    public static final int PLAYER_A = 0;
    public static final int PLAYER_B = 1;

    private static final int LINE_COUNT = DotsAndBoxesMatch.LINE_COUNT;
    private static final int BOX_COUNT = DotsAndBoxesMatch.BOX_COUNT;
    private static final int BOX_COLS = DotsAndBoxesMatch.BOX_COLS;
    private static final int BOX_ROWS = DotsAndBoxesMatch.BOX_ROWS;
    private static final int EXTRA_TURN_INDEX = LINE_COUNT + BOX_COUNT;

    public static char[] newBoard()
    {
        char[] state = new char[EXTRA_TURN_INDEX + 1];
        java.util.Arrays.fill(state, '.');
        state[EXTRA_TURN_INDEX] = '0';
        return state;
    }

    @Override
    public List<Integer> legalMoves(char[] state, int playerIndex)
    {
        List<Integer> moves = new ArrayList<Integer>();
        for (int i = 0; i < LINE_COUNT; i++)
        {
            if (state[i] == '.')
            {
                moves.add(i);
            }
        }
        return moves;
    }

    @Override
    public char[] applyMove(char[] state, Integer move, int playerIndex)
    {
        char[] next = state.clone();
        next[move] = 'X';

        int completed = 0;
        for (int box : boxesBorderedBy(move))
        {
            int ownerSlot = LINE_COUNT + box;
            if (next[ownerSlot] == '.' && isBoxComplete(next, box))
            {
                next[ownerSlot] = (char) ('0' + playerIndex);
                completed++;
            }
        }
        next[EXTRA_TURN_INDEX] = completed > 0 ? '1' : '0';
        return next;
    }

    @Override
    public boolean isTerminal(char[] state)
    {
        for (int i = 0; i < LINE_COUNT; i++)
        {
            if (state[i] == '.') return false;
        }
        return true;
    }

    @Override
    public int nextPlayer(char[] state, int playerJustMoved)
    {
        return state[EXTRA_TURN_INDEX] == '1' ? playerJustMoved : 1 - playerJustMoved;
    }

    @Override
    public Integer winner(char[] state)
    {
        int[] counts = boxCounts(state);
        if (counts[0] == counts[1]) return null;
        return counts[0] > counts[1] ? PLAYER_A : PLAYER_B;
    }

    @Override
    public int evaluate(char[] state, int forPlayerIndex)
    {
        int[] counts = boxCounts(state);
        int myBoxes = counts[forPlayerIndex];
        int theirBoxes = counts[1 - forPlayerIndex];

        if (isTerminal(state))
        {
            if (myBoxes == theirBoxes) return 0;
            return myBoxes > theirBoxes ? 1_000_000 : -1_000_000;
        }

        // Box-count difference dominates; a small penalty for every box already sitting at
        // 3 drawn sides (a free box for WHOEVER moves next, so simply "up for grabs" rather
        // than clearly good or bad for forPlayer) discourages leaving them lying around
        // rather than a full chain-counting strategy, which is well beyond a simple heuristic.
        int threeSided = 0;
        for (int box = 0; box < BOX_COUNT; box++)
        {
            if (state[LINE_COUNT + box] == '.' && sidesDrawnFor(state, box) == 3)
            {
                threeSided++;
            }
        }

        return (myBoxes - theirBoxes) * 100 - threeSided * 3;
    }

    private static int[] boxCounts(char[] state)
    {
        int[] counts = new int[2];
        for (int box = 0; box < BOX_COUNT; box++)
        {
            char owner = state[LINE_COUNT + box];
            if (owner == '0') counts[0]++;
            else if (owner == '1') counts[1]++;
        }
        return counts;
    }

    private static int sidesDrawnFor(char[] state, int boxIndex)
    {
        int drawn = 0;
        for (int line : linesOf(boxIndex))
        {
            if (state[line] == 'X') drawn++;
        }
        return drawn;
    }

    private static boolean isBoxComplete(char[] state, int boxIndex)
    {
        return sidesDrawnFor(state, boxIndex) == 4;
    }

    private static int[] linesOf(int boxIndex)
    {
        int r = boxIndex / BOX_COLS, c = boxIndex % BOX_COLS;
        int top = r * BOX_COLS + c;
        int bottom = (r + 1) * BOX_COLS + c;
        int left = 20 + r * (BOX_COLS + 1) + c;
        int right = 20 + r * (BOX_COLS + 1) + (c + 1);
        return new int[] { top, bottom, left, right };
    }

    /** Which box(es) a given line index borders - mirrors DotsAndBoxesMatch.boxesBorderedBy exactly. */
    private static List<Integer> boxesBorderedBy(int lineIndex)
    {
        List<Integer> result = new ArrayList<Integer>();
        if (lineIndex < 20)
        {
            int row = lineIndex / BOX_COLS, col = lineIndex % BOX_COLS;
            if (row - 1 >= 0) result.add((row - 1) * BOX_COLS + col);
            if (row < BOX_ROWS) result.add(row * BOX_COLS + col);
        }
        else
        {
            int vIndex = lineIndex - 20;
            int row = vIndex / (BOX_COLS + 1), col = vIndex % (BOX_COLS + 1);
            if (col - 1 >= 0) result.add(row * BOX_COLS + (col - 1));
            if (col < BOX_COLS) result.add(row * BOX_COLS + col);
        }
        return result;
    }
}
