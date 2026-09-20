package games;

import ai.BotStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * TicTacToeRandomMoveStrategy
 * ----------------------------
 * The fallback AiKernel falls back to if TicTacToePracticeBotStrategy
 * (the "smart" one) ever throws. Deliberately as simple as possible -
 * just picks uniformly at random among the empty cells, with no board
 * analysis of any kind, so it has essentially nothing left in it that
 * could itself have a bug. Every game that registers with AiKernel
 * should have a fallback this trivial; the whole point is that it's
 * fundamentally simpler than the strategy it's backing up.
 */
public class TicTacToeRandomMoveStrategy implements BotStrategy<char[], Integer>
{
    private final Random random = new Random();

    public Integer chooseMove(char[] board)
    {
        List<Integer> openCells = new ArrayList<Integer>();
        for (int i = 0; i < board.length; i++)
        {
            if (board[i] == '.')
            {
                openCells.add(i);
            }
        }
        if (openCells.isEmpty())
        {
            return -1; // board is full - shouldn't be asked to move in this state, but never throw over it
        }
        return openCells.get(random.nextInt(openCells.size()));
    }
}
