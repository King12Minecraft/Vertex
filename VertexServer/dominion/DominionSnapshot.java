package dominion;

import java.io.Serializable;
import java.util.List;

/**
 * DominionSnapshot
 * ----------------
 * The client-facing view of the whole Dominion world - sent in response to
 * DOMINION_STATE_REQUEST (see Message.getDominionSnapshot()). V1 has no fog
 * of war (see DOMINION_DESIGN.md's Future Depth section), so this
 * deliberately includes everything - every province, every nation, every
 * army, every diplomatic relation - not just the requester's own nation's
 * view. Built by DominionWorld.toSnapshot(); every class reachable from
 * here (Province/Nation/Army/DiplomaticRelation/Terrain/RelationType) is
 * explicitly allow-listed in VertexSerializationFilter.
 */
public class DominionSnapshot implements Serializable
{
    private final int currentTick;
    private final List<Province> provinces;
    private final List<Nation> nations;
    private final List<Army> armies;
    private final List<DiplomaticRelation> relations;

    public DominionSnapshot(int currentTick, List<Province> provinces, List<Nation> nations,
                             List<Army> armies, List<DiplomaticRelation> relations)
    {
        this.currentTick = currentTick;
        this.provinces = provinces;
        this.nations = nations;
        this.armies = armies;
        this.relations = relations;
    }

    public int getCurrentTick() { return currentTick; }
    public List<Province> getProvinces() { return provinces; }
    public List<Nation> getNations() { return nations; }
    public List<Army> getArmies() { return armies; }
    public List<DiplomaticRelation> getRelations() { return relations; }
}
