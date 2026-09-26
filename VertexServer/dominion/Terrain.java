package dominion;

/**
 * Terrain
 * -------
 * A Province's terrain type - affects both its production yield and how
 * hard it is to take by force. See DOMINION_DESIGN.md for why V1 keeps
 * this to a short fixed list rather than a full tech/terrain tree, and
 * why the exact multiplier values here are a placeholder pending real
 * playtesting, not a final balance decision.
 */
public enum Terrain
{
    PLAINS(1.0, 1.0),
    HILLS(1.3, 1.25),
    FOREST(0.8, 1.1);

    private final double yieldMultiplier;
    private final double defenseMultiplier;

    Terrain(double yieldMultiplier, double defenseMultiplier)
    {
        this.yieldMultiplier = yieldMultiplier;
        this.defenseMultiplier = defenseMultiplier;
    }

    public double getYieldMultiplier() { return yieldMultiplier; }
    public double getDefenseMultiplier() { return defenseMultiplier; }
}
