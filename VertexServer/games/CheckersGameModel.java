package games;

import ai.search.GameModel;

import java.util.ArrayList;
import java.util.List;

/**
 * CheckersGameModel
 * -----------------
 * The one file Checkers needs to plug into the shared ai.search engine
 * - describes this game's rules only, including the two real rules
 * that make Checkers harder than Connect Four/Reversi: mandatory
 * captures (a non-capturing move is illegal whenever any capture is
 * available) and mandatory multi-jump continuation (the same piece
 * must keep jumping if another capture is immediately available to
 * it). Board encoding matches CheckersMatch's own (flat char[64],
 * row 0 at Red's back rank, 'r'/'R' Red man/king, 'b'/'B' Black
 * man/king) - a separate, decoupled implementation since
 * CheckersMatch itself is network-coupled.
 *
 * Player 0 = RED (moves toward increasing row), player 1 = BLACK
 * (moves toward decreasing row) - same convention CheckersMatch uses.
 * The mandatory-continuation rule is handled the same way
 * DotsAndBoxesGameModel handles "completing a box earns another
 * turn": nextPlayer() reads a fact carried in the state
 * (mustContinueFrom) rather than the shared engine needing any
 * concept of "the same player might move again."
 */
public class CheckersGameModel implements GameModel<CheckersState, CheckersMove>
{
    public static final int RED = 0;
    public static final int BLACK = 1;

    private static final int SIZE = CheckersMatch.SIZE;
    private static final int[][] DIRECTIONS = { {1, 1}, {1, -1}, {-1, 1}, {-1, -1} };

    public static CheckersState newBoard()
    {
        char[] board = new char[SIZE * SIZE];
        java.util.Arrays.fill(board, '.');
        for (int row = 0; row < SIZE; row++)
        {
            for (int col = 0; col < SIZE; col++)
            {
                if ((row + col) % 2 != 1) continue;
                if (row < 3) board[row * SIZE + col] = 'r';
                else if (row > 4) board[row * SIZE + col] = 'b';
            }
        }
        return new CheckersState(board, null);
    }

    @Override
    public List<CheckersMove> legalMoves(CheckersState state, int playerIndex)
    {
        char[] board = state.board;
        List<CheckersMove> moves = new ArrayList<CheckersMove>();

        if (state.mustContinueFrom != null)
        {
            for (int[] capture : capturesFor(board, state.mustContinueFrom))
            {
                moves.add(new CheckersMove(state.mustContinueFrom, capture[0]));
            }
            return moves;
        }

        boolean isRed = playerIndex == RED;
        boolean anyCapture = hasAnyCapture(board, isRed);

        for (int i = 0; i < board.length; i++)
        {
            char piece = board[i];
            if (piece == '.' || (Character.toLowerCase(piece) == 'r') != isRed) continue;

            if (anyCapture)
            {
                for (int[] capture : capturesFor(board, i))
                {
                    moves.add(new CheckersMove(i, capture[0]));
                }
            }
            else
            {
                for (int to : simpleMovesFor(board, i, piece))
                {
                    moves.add(new CheckersMove(i, to));
                }
            }
        }
        return moves;
    }

    @Override
    public CheckersState applyMove(CheckersState state, CheckersMove move, int playerIndex)
    {
        char[] board = state.board.clone();
        char piece = board[move.from];

        int fromRow = move.from / SIZE, toRow = move.to / SIZE;
        boolean isJump = Math.abs(toRow - fromRow) == 2;

        board[move.to] = piece;
        board[move.from] = '.';

        Integer mustContinueFrom = null;
        if (isJump)
        {
            int midIndex = (move.from + move.to) / 2;
            board[midIndex] = '.';
            maybeKing(board, move.to);
            if (!capturesFor(board, move.to).isEmpty())
            {
                mustContinueFrom = move.to;
            }
        }
        else
        {
            maybeKing(board, move.to);
        }

        return new CheckersState(board, mustContinueFrom);
    }

    @Override
    public boolean isTerminal(CheckersState state)
    {
        return checkWinner(state.board) != -1;
    }

    @Override
    public int nextPlayer(CheckersState state, int playerJustMoved)
    {
        return state.mustContinueFrom != null ? playerJustMoved : 1 - playerJustMoved;
    }

    @Override
    public Integer winner(CheckersState state)
    {
        int result = checkWinner(state.board);
        return result == -1 ? null : result;
    }

    @Override
    public int evaluate(CheckersState state, int forPlayerIndex)
    {
        int winner = checkWinner(state.board);
        if (winner != -1)
        {
            return winner == forPlayerIndex ? 1_000_000 : -1_000_000;
        }

        boolean myRed = forPlayerIndex == RED;
        int mine = 0, theirs = 0;
        for (char piece : state.board)
        {
            if (piece == '.') continue;
            boolean pieceIsRed = Character.toLowerCase(piece) == 'r';
            int value = Character.isUpperCase(piece) ? 3 : 1;
            if (pieceIsRed == myRed) mine += value; else theirs += value;
        }
        return (mine - theirs) * 100;
    }

    /** RED (0) or BLACK (1) if that side has no pieces or no legal move left, otherwise -1 (not terminal). */
    private static int checkWinner(char[] board)
    {
        boolean redHasMove = false, blackHasMove = false;
        boolean redHasPiece = false, blackHasPiece = false;

        for (int i = 0; i < board.length; i++)
        {
            char piece = board[i];
            if (piece == '.') continue;
            boolean isRed = Character.toLowerCase(piece) == 'r';
            if (isRed) redHasPiece = true; else blackHasPiece = true;

            if (!capturesFor(board, i).isEmpty() || !simpleMovesFor(board, i, piece).isEmpty())
            {
                if (isRed) redHasMove = true; else blackHasMove = true;
            }
        }

        if (!redHasPiece || !redHasMove) return BLACK;
        if (!blackHasPiece || !blackHasMove) return RED;
        return -1;
    }

    private static void maybeKing(char[] board, int index)
    {
        int row = index / SIZE;
        char piece = board[index];
        if (piece == 'r' && row == SIZE - 1) board[index] = 'R';
        else if (piece == 'b' && row == 0) board[index] = 'B';
    }

    /** Every capture available to the piece at fromIndex - each result is {landingIndex, capturedPieceIndex}. Mirrors CheckersMatch.capturesFor exactly. */
    private static List<int[]> capturesFor(char[] board, int fromIndex)
    {
        List<int[]> results = new ArrayList<int[]>();
        char piece = board[fromIndex];
        if (piece == '.') return results;

        boolean isKing = Character.isUpperCase(piece);
        boolean isRed = Character.toLowerCase(piece) == 'r';
        int fromRow = fromIndex / SIZE, fromCol = fromIndex % SIZE;

        for (int[] dir : DIRECTIONS)
        {
            if (!isKing && ((isRed && dir[0] != 1) || (!isRed && dir[0] != -1))) continue;

            int midRow = fromRow + dir[0], midCol = fromCol + dir[1];
            int landRow = fromRow + dir[0] * 2, landCol = fromCol + dir[1] * 2;
            if (landRow < 0 || landRow >= SIZE || landCol < 0 || landCol >= SIZE) continue;

            int midIndex = midRow * SIZE + midCol;
            int landIndex = landRow * SIZE + landCol;
            char midPiece = board[midIndex];
            boolean midIsOpponent = midPiece != '.' && (Character.toLowerCase(midPiece) == 'r') != isRed;

            if (midIsOpponent && board[landIndex] == '.')
            {
                results.add(new int[] { landIndex, midIndex });
            }
        }
        return results;
    }

    private static boolean hasAnyCapture(char[] board, boolean isRed)
    {
        for (int i = 0; i < board.length; i++)
        {
            char piece = board[i];
            if (piece == '.') continue;
            boolean pieceIsRed = Character.toLowerCase(piece) == 'r';
            if (pieceIsRed == isRed && !capturesFor(board, i).isEmpty())
            {
                return true;
            }
        }
        return false;
    }

    private static List<Integer> simpleMovesFor(char[] board, int fromIndex, char piece)
    {
        List<Integer> results = new ArrayList<Integer>();
        int fromRow = fromIndex / SIZE, fromCol = fromIndex % SIZE;
        boolean isKing = Character.isUpperCase(piece);
        boolean isRed = Character.toLowerCase(piece) == 'r';

        for (int[] dir : DIRECTIONS)
        {
            if (!isKing && ((isRed && dir[0] != 1) || (!isRed && dir[0] != -1))) continue;
            int toRow = fromRow + dir[0], toCol = fromCol + dir[1];
            if (toRow < 0 || toRow >= SIZE || toCol < 0 || toCol >= SIZE) continue;
            int to = toRow * SIZE + toCol;
            if (board[to] == '.') results.add(to);
        }
        return results;
    }
}
