package games;

/**
 * CheckersMove
 * ------------
 * A from/to pair, same shape CheckersMatch.makeMove already takes -
 * both a simple step and a single jump are just "from one square to
 * another" here; CheckersGameModel tells them apart by distance
 * (1 square vs. 2) rather than needing a separate move type. Equality
 * is by value so PracticeMatch's legality check
 * (legalMoves(...).contains(move)) works correctly.
 */
public final class CheckersMove
{
    public final int from;
    public final int to;

    public CheckersMove(int from, int to)
    {
        this.from = from;
        this.to = to;
    }

    @Override
    public boolean equals(Object other)
    {
        if (!(other instanceof CheckersMove)) return false;
        CheckersMove move = (CheckersMove) other;
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
