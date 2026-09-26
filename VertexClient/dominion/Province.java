package dominion;

import java.io.Serializable;

/**
 * Province
 * --------
 * One tile of Dominion's fixed square grid. Adjacency is orthogonal only
 * (no diagonals) - armies march and combat resolves between orthogonal
 * neighbors, same as a simple grid map, not a hex map (see
 * DOMINION_DESIGN.md for why square over hex). owningNationId is null
 * for an unclaimed province - claiming one outright (no combat needed)
 * is exactly how a new Nation is founded and how an army expands into
 * empty territory.
 *
 * Serializable so DominionSnapshot can carry it straight over the wire as
 * V1's client-facing DTO (no fog of war yet - see DOMINION_DESIGN.md's
 * Future Depth section - so the real domain object doubles as the wire
 * format instead of a parallel read-only view class). Any concrete class
 * reachable from a serialized Message must be explicitly allow-listed in
 * VertexSerializationFilter or it silently fails to deserialize - this
 * class is listed there.
 */
public class Province implements Serializable
{
    private final int id;
    private final int row;
    private final int col;
    private final Terrain terrain;
    private Integer owningNationId;

    public Province(int id, int row, int col, Terrain terrain)
    {
        this.id = id;
        this.row = row;
        this.col = col;
        this.terrain = terrain;
    }

    public int getId() { return id; }
    public int getRow() { return row; }
    public int getCol() { return col; }
    public Terrain getTerrain() { return terrain; }
    public Integer getOwningNationId() { return owningNationId; }
    public void setOwningNationId(Integer owningNationId) { this.owningNationId = owningNationId; }

    public boolean isAdjacentTo(Province other)
    {
        int dRow = Math.abs(row - other.row);
        int dCol = Math.abs(col - other.col);
        return (dRow + dCol) == 1;
    }
}
