package games;

import ai.search.GameModel;

import java.util.ArrayList;
import java.util.List;

/**
 * ConnectFourGameModel
 * --------------------
 * The one file Connect Four needs to plug into the shared ai.search
 * engine (Minimax / GenericBotStrategy / PracticeMatch, all fully
 * generic and shared with every other game) - describes this game's
 * rules only. Board encoding matches ConnectFourMatch's own (flat
 * char[COLS*ROWS], row 0 = bottom, '.'/'R'/'Y'), so a practice-mode
 * board looks and behaves identically to an online one - though this
 * is a separate, decoupled implementation of the same public-domain
 * rules, since ConnectFourMatch's own logic is tied to ClientHandler/
 * the network and can't be reused directly for search (same reasoning
 * TicTacToeAI already exists alongside the network-coupled
 * TicTacToeMatch).
 *
 * Player 0 = RED (moves first), player 1 = YELLOW - the same
 * convention ConnectFourMatch uses (redTurn starts true).
 */
public class ConnectFourGameModel implements GameModel<char[], Integer>
{
    public static final int RED = 0;
    public static final int YELLOW = 1;

    private static final int COLS = ConnectFourMatch.COLS;
    private static final int ROWS = ConnectFourMatch.ROWS;
    private static final int[][] DIRECTIONS = { {0, 1}, {1, 0}, {1, 1}, {1, -1} };

    public static char[] newBoard()
    {
        char[] board = new char[COLS * ROWS];
        java.util.Arrays.fill(board, '.');
        return board;
    }

    @Override
    public List<Integer> legalMoves(char[] state, int playerIndex)
    {
        List<Integer> moves = new ArrayList<Integer>();
        for (int col = 0; col < COLS; col++)
        {
            if (lowestEmptyRow(state, col) >= 0)
            {
                moves.add(col);
            }
        }
        return moves;
    }

    @Override
    public char[] applyMove(char[] state, Integer move, int playerIndex)
    {
        char[] next = state.clone();
        int row = lowestEmptyRow(next, move);
        next[row * COLS + move] = playerIndex == RED ? 'R' : 'Y';
        return next;
    }

    @Override
    public boolean isTerminal(char[] state)
    {
        return findWinnerColor(state) != '.' || !hasEmptyCell(state);
    }

    @Override
    public int nextPlayer(char[] state, int playerJustMoved)
    {
        return playerJustMoved == RED ? YELLOW : RED;
    }

    @Override
    public Integer winner(char[] state)
    {
        char color = findWinnerColor(state);
        if (color == 'R') return RED;
        if (color == 'Y') return YELLOW;
        return null;
    }

    @Override
    public int evaluate(char[] state, int forPlayerIndex)
    {
        char myColor = forPlayerIndex == RED ? 'R' : 'Y';
        char oppColor = forPlayerIndex == RED ? 'Y' : 'R';

        char winner = findWinnerColor(state);
        if (winner == myColor) return 1_000_000;
        if (winner == oppColor) return -1_000_000;
        if (!hasEmptyCell(state)) return 0;

        int score = 0;
        for (int row = 0; row < ROWS; row++)
        {
            for (int col = 0; col < COLS; col++)
            {
                for (int[] dir : DIRECTIONS)
                {
                    int endRow = row + dir[0] * 3;
                    int endCol = col + dir[1] * 3;
                    if (endRow < 0 || endRow >= ROWS || endCol < 0 || endCol >= COLS)
                    {
                        continue;
                    }

                    int mine = 0, theirs = 0;
                    for (int k = 0; k < 4; k++)
                    {
                        char c = state[(row + dir[0] * k) * COLS + (col + dir[1] * k)];
                        if (c == myColor) mine++;
                        else if (c == oppColor) theirs++;
                    }
                    if (mine > 0 && theirs > 0)
                    {
                        continue;
                    }
                    score += windowScore(mine) - windowScore(theirs);
                }
            }
        }
        return score;
    }

    private static int windowScore(int count)
    {
        if (count == 3) return 50;
        if (count == 2) return 10;
        if (count == 1) return 1;
        return 0;
    }

    private static int lowestEmptyRow(char[] board, int column)
    {
        for (int row = 0; row < ROWS; row++)
        {
            if (board[row * COLS + column] == '.')
            {
                return row;
            }
        }
        return -1;
    }

    private static boolean hasEmptyCell(char[] board)
    {
        for (char c : board)
        {
            if (c == '.') return true;
        }
        return false;
    }

    /** Full-board scan for any 4-in-a-row, since (unlike ConnectFourMatch's own incremental check) this has to work from state alone, with no "last move played" to check from. Cheap enough at 42 cells for a depth-limited search to call freely. */
    private static char findWinnerColor(char[] board)
    {
        for (int row = 0; row < ROWS; row++)
        {
            for (int col = 0; col < COLS; col++)
            {
                char piece = board[row * COLS + col];
                if (piece == '.') continue;

                for (int[] dir : DIRECTIONS)
                {
                    boolean four = true;
                    for (int k = 1; k < 4 && four; k++)
                    {
                        int r = row + dir[0] * k;
                        int c = col + dir[1] * k;
                        if (r < 0 || r >= ROWS || c < 0 || c >= COLS || board[r * COLS + c] != piece)
                        {
                            four = false;
                        }
                    }
                    if (four) return piece;
                }
            }
        }
        return '.';
    }
}
