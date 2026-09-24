package games;

/**
 * ChessState
 * ----------
 * The state type ChessGameModel searches over - the 64-cell board plus
 * every extra fact real chess rules need beyond the board itself:
 * which king/rooks have ever moved (castling rights don't come back
 * once lost, even if the piece returns to its home square) and the en
 * passant target square from the immediately preceding move. A plain
 * char[] (as ConnectFourGameModel/ReversiGameModel use) can't carry
 * any of this - same reasoning CheckersState already established for
 * Checkers' one extra field, just with more of them here.
 *
 * turnPlayer is included too, which the other four ai.search games
 * don't need: GameModel.isTerminal(state) only ever receives the
 * state, but chess's real terminal condition (checkmate/stalemate) is
 * inherently "does the side TO MOVE have any legal move" - unlike
 * Reversi/Checkers, where "is either side completely stuck" is
 * naturally turn-independent, chess has no such symmetric framing, so
 * the state has to carry whose turn it is for isTerminal to answer
 * that question on its own. It's kept in lockstep with what
 * ChessGameModel.nextPlayer() would independently compute (both are
 * the same plain alternation, since chess has no "same player moves
 * again" rule at all).
 */
public final class ChessState
{
    public final char[] board;
    public final int turnPlayer;
    public final boolean whiteKingMoved;
    public final boolean blackKingMoved;
    public final boolean whiteRookAMoved;
    public final boolean whiteRookHMoved;
    public final boolean blackRookAMoved;
    public final boolean blackRookHMoved;
    /** The square a pawn just skipped over with a double-move, capturable en passant only in this exact state - -1 when none is available. */
    public final int enPassantTarget;

    public ChessState(char[] board, int turnPlayer, boolean whiteKingMoved, boolean blackKingMoved,
                       boolean whiteRookAMoved, boolean whiteRookHMoved,
                       boolean blackRookAMoved, boolean blackRookHMoved, int enPassantTarget)
    {
        this.board = board;
        this.turnPlayer = turnPlayer;
        this.whiteKingMoved = whiteKingMoved;
        this.blackKingMoved = blackKingMoved;
        this.whiteRookAMoved = whiteRookAMoved;
        this.whiteRookHMoved = whiteRookHMoved;
        this.blackRookAMoved = blackRookAMoved;
        this.blackRookHMoved = blackRookHMoved;
        this.enPassantTarget = enPassantTarget;
    }
}
