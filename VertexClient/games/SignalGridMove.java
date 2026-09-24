package games;

/**
 * SignalGridMove
 * --------------
 * An (index, direction) pair, same shape SignalGridMatch.placeAndFire
 * already takes - placing a node and firing it are always one
 * decision, never two separate moves. Equality is by value so
 * PracticeMatch's legality check (legalMoves(...).contains(move))
 * works correctly.
 */
public final class SignalGridMove
{
    public final int index;
    public final int direction;

    public SignalGridMove(int index, int direction)
    {
        this.index = index;
        this.direction = direction;
    }

    @Override
    public boolean equals(Object other)
    {
        if (!(other instanceof SignalGridMove)) return false;
        SignalGridMove move = (SignalGridMove) other;
        return index == move.index && direction == move.direction;
    }

    @Override
    public int hashCode()
    {
        return index * 4 + direction;
    }

    @Override
    public String toString()
    {
        return index + "@" + direction;
    }
}
