package games;

/**
 * CheckersState
 * -------------
 * The state type CheckersGameModel searches over - the 64-cell board
 * plus the one extra piece of bookkeeping Checkers' real rules need
 * beyond the board itself: which piece (if any) is mid multi-jump and
 * must keep capturing with that exact piece before any other move is
 * legal. A plain char[] (as ConnectFourGameModel/ReversiGameModel use)
 * isn't enough here since that extra fact needs a real index (0-63),
 * not a single flag bit - a small immutable holder is clearer than
 * encoding it into a spare character.
 */
public final class CheckersState
{
    public final char[] board;
    /** Non-null only immediately after a jump that must chain into another - see CheckersMatch's own mustContinueFrom field, which this mirrors. */
    public final Integer mustContinueFrom;

    public CheckersState(char[] board, Integer mustContinueFrom)
    {
        this.board = board;
        this.mustContinueFrom = mustContinueFrom;
    }
}
