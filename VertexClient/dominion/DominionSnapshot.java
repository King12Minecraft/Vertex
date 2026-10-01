package dominion;

import java.io.Serializable;
import java.util.List;

/**
 * DominionSnapshot
 * ----------------
 * The client-facing view of the Dominion world - sent in response to
 * DOMINION_STATE_REQUEST (see Message.getDominionSnapshot()). V1 has no fog
 * of war (see DOMINION_DESIGN.md's Future Depth section), so provinces/
 * nations/armies/relations deliberately include everything - not just the
 * requester's own nation's view. myProposals is the one exception: a
 * pending Alliance/Non-Aggression proposal IS private between the two
 * nations involved even in V1 (see DiplomaticProposal's javadoc), so
 * DominionWorld.toSnapshot(accountId) scopes it to proposals where
 * accountId's own Nation is the sender or the target, never everyone's.
 * Every class reachable from here (Province/Nation/Army/
 * DiplomaticRelation/DiplomaticProposal/Terrain/RelationType) is
 * explicitly allow-listed in VertexSerializationFilter.
 */
public class DominionSnapshot implements Serializable
{
    private static final long serialVersionUID = 1L;

    private final int currentTick;
    private final List<Province> provinces;
    private final List<Nation> nations;
    private final List<Army> armies;
    private final List<DiplomaticRelation> relations;
    private final List<DiplomaticProposal> myProposals;

    public DominionSnapshot(int currentTick, List<Province> provinces, List<Nation> nations,
                             List<Army> armies, List<DiplomaticRelation> relations,
                             List<DiplomaticProposal> myProposals)
    {
        this.currentTick = currentTick;
        this.provinces = provinces;
        this.nations = nations;
        this.armies = armies;
        this.relations = relations;
        this.myProposals = myProposals;
    }

    public int getCurrentTick() { return currentTick; }
    public List<Province> getProvinces() { return provinces; }
    public List<Nation> getNations() { return nations; }
    public List<Army> getArmies() { return armies; }
    public List<DiplomaticRelation> getRelations() { return relations; }
    public List<DiplomaticProposal> getMyProposals() { return myProposals; }
}
