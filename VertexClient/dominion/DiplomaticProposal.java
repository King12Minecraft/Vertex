package dominion;

import java.io.Serializable;

/**
 * DiplomaticProposal
 * -------------------
 * A pending Alliance or Non-Aggression proposal from one Nation to
 * another, awaiting the target's accept/reject (see DominionWorld.
 * proposeRelation()/respondToProposal()). Deliberately never included in
 * the broadcast DominionSnapshot the way Province/Nation/Army/
 * DiplomaticRelation are - unlike those, a pending proposal is private
 * between the two nations involved (this is the one place V1 already
 * needs a notion of "not everyone sees everything," even before real fog
 * of war exists - see DOMINION_DESIGN.md's Future Depth section).
 * DominionManager.getSnapshot(accountId) only includes proposals where
 * accountId's own Nation is the sender or the target.
 */
public class DiplomaticProposal implements Serializable
{
    private final int id;
    private final int fromNationId;
    private final int toNationId;
    private final RelationType proposedType;

    public DiplomaticProposal(int id, int fromNationId, int toNationId, RelationType proposedType)
    {
        this.id = id;
        this.fromNationId = fromNationId;
        this.toNationId = toNationId;
        this.proposedType = proposedType;
    }

    public int getId() { return id; }
    public int getFromNationId() { return fromNationId; }
    public int getToNationId() { return toNationId; }
    public RelationType getProposedType() { return proposedType; }
}
