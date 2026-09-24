package games;

/**
 * FusionGridState
 * ----------------
 * Everything FusionGridBotStrategy needs to decide where to place a
 * tile - the board (values/owners, mirroring FusionGridMatch's own
 * parallel arrays) plus which tile value was just drawn and which
 * player is deciding. Not an ai.search GameModel state: Fusion Grid's
 * NEXT tile after this placement is randomly drawn, so there's no
 * deterministic future for a search to explore - see
 * FusionGridBotStrategy's javadoc for why this is a one-ply greedy
 * decision instead.
 */
public final class FusionGridState
{
    public final int[] values;
    public final int[] owners;
    public final int currentTileValue;
    public final int forPlayerIndex;

    public FusionGridState(int[] values, int[] owners, int currentTileValue, int forPlayerIndex)
    {
        this.values = values;
        this.owners = owners;
        this.currentTileValue = currentTileValue;
        this.forPlayerIndex = forPlayerIndex;
    }
}
