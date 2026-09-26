package dominion;

/**
 * DiplomaticRelation
 * ------------------
 * One relationship between two nations. A declared WAR is deliberately
 * NOT immediately live - effectiveFromTick is always the tick after the
 * one it was declared on (see DOMINION_DESIGN.md's warfare section: a
 * one-tick warning is a strategic-pacing choice, not a technical
 * limitation), so a war can exist as a relation before it's actually
 * combat-active. Every other relation type is effective immediately and
 * leaves effectiveFromTick null.
 */
public class DiplomaticRelation
{
    private final int nationAId;
    private final int nationBId;
    private RelationType type;
    private final Integer effectiveFromTick;

    public DiplomaticRelation(int nationAId, int nationBId, RelationType type, Integer effectiveFromTick)
    {
        this.nationAId = nationAId;
        this.nationBId = nationBId;
        this.type = type;
        this.effectiveFromTick = effectiveFromTick;
    }

    public int getNationAId() { return nationAId; }
    public int getNationBId() { return nationBId; }
    public RelationType getType() { return type; }
    public void setType(RelationType type) { this.type = type; }
    public Integer getEffectiveFromTick() { return effectiveFromTick; }

    public boolean involves(int nationId1, int nationId2)
    {
        return (nationAId == nationId1 && nationBId == nationId2)
            || (nationAId == nationId2 && nationBId == nationId1);
    }

    public boolean isWarActiveAt(int currentTick)
    {
        return type == RelationType.WAR && effectiveFromTick != null && currentTick >= effectiveFromTick;
    }
}
