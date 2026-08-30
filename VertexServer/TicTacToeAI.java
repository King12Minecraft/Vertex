import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * TicTacToeAI
 * -----------
 * Win-detection and a simple opponent AI for Tic-Tac-Toe's Practice
 * Mode - entirely local, no network involvement at all, so Practice
 * Mode genuinely works offline (and online too, just without needing
 * the connection for anything). Medium difficulty: takes a winning
 * move if one exists, blocks the human's winning move if they have
 * one, otherwise prefers the center, then a corner, then anywhere
 * open. Deliberately not a perfect/unbeatable player - the point is
 * practice, not an unwinnable wall.
 */
public class TicTacToeAI
{
    private static final int[][] WIN_LINES = {
        {0, 1, 2}, {3, 4, 5}, {6, 7, 8},
        {0, 3, 6}, {1, 4, 7}, {2, 5, 8},
        {0, 4, 8}, {2, 4, 6}
    };
    private static final Random RANDOM = new Random();

    private TicTacToeAI()
    {
        // Static utility class - never instantiated.
    }

    /** Returns the winning 3-cell line, or null if there's no winner yet. */
    public static int[] checkWinner(char[] board)
    {
        for (int i = 0; i < WIN_LINES.length; i++)
        {
            int a = WIN_LINES[i][0];
            int b = WIN_LINES[i][1];
            int c = WIN_LINES[i][2];
            if (board[a] != '.' && board[a] == board[b] && board[b] == board[c])
            {
                return WIN_LINES[i];
            }
        }
        return null;
    }

    public static boolean hasEmptyCell(char[] board)
    {
        for (int i = 0; i < board.length; i++)
        {
            if (board[i] == '.') return true;
        }
        return false;
    }

    /** Picks a move for aiSymbol, given the human plays humanSymbol. */
    public static int pickMove(char[] board, char aiSymbol, char humanSymbol)
    {
        Integer winMove = findWinningMove(board, aiSymbol);
        if (winMove != null)
        {
            return winMove;
        }

        Integer blockMove = findWinningMove(board, humanSymbol);
        if (blockMove != null)
        {
            return blockMove;
        }

        if (board[4] == '.')
        {
            return 4;
        }

        int[] corners = { 0, 2, 6, 8 };
        List<Integer> availableCorners = new ArrayList<Integer>();
        for (int i = 0; i < corners.length; i++)
        {
            if (board[corners[i]] == '.')
            {
                availableCorners.add(corners[i]);
            }
        }
        if (!availableCorners.isEmpty())
        {
            return availableCorners.get(RANDOM.nextInt(availableCorners.size()));
        }

        List<Integer> anyOpen = new ArrayList<Integer>();
        for (int i = 0; i < board.length; i++)
        {
            if (board[i] == '.')
            {
                anyOpen.add(i);
            }
        }
        return anyOpen.get(RANDOM.nextInt(anyOpen.size()));
    }

    /** Checks if placing symbol at any empty cell would win immediately - returns that index, or null. */
    private static Integer findWinningMove(char[] board, char symbol)
    {
        for (int i = 0; i < board.length; i++)
        {
            if (board[i] == '.')
            {
                board[i] = symbol;
                boolean wins = checkWinner(board) != null;
                board[i] = '.';
                if (wins)
                {
                    return i;
                }
            }
        }
        return null;
    }
}
