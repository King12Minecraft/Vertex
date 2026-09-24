package games;

/**
 * ChessMove
 * ---------
 * A from/to pair, same shape ChessMatch.makeMove already takes -
 * castling and en passant are both just "from one square to another"
 * here too (ChessGameModel's applyRawMove detects and handles their
 * side effects from the squares alone, mirroring ChessMatch's own
 * applyMove exactly). Pawns always auto-promote to a queen, the same
 * simplification ChessMatch itself already makes, so there's no
 * separate promotion-choice field to carry.
 */
public final class ChessMove
{
    public final int from;
    public final int to;

    public ChessMove(int from, int to)
    {
        this.from = from;
        this.to = to;
    }

    @Override
    public boolean equals(Object other)
    {
        if (!(other instanceof ChessMove)) return false;
        ChessMove move = (ChessMove) other;
        return from == move.from && to == move.to;
    }

    @Override
    public int hashCode()
    {
        return from * 31 + to;
    }

    @Override
    public String toString()
    {
        return from + "->" + to;
    }
}
