package dominion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DominionWorld
 * -------------
 * All of Dominion's live state - the provinces grid, nations, armies, and
 * diplomatic relations - held in memory. Persistence (its own isolated
 * store, per ROADMAP.md's Architecture Decisions) is a later build-order
 * step (see DOMINION_DESIGN.md); this class is deliberately just the data
 * plus the small set of mutations that need validation (declaring war,
 * queuing a march), not yet wired to disk or a network protocol.
 */
public class DominionWorld
{
    /** What can go wrong founding a Nation - see foundNation(). */
    public enum FoundNationResult { SUCCESS, INVALID_NAME, ACCOUNT_ALREADY_HAS_NATION, PROVINCE_NOT_FOUND, PROVINCE_ALREADY_CLAIMED }

    /** result plus the created Nation (null unless result == SUCCESS) - see foundNation(). */
    public static class FoundNationOutcome
    {
        public final FoundNationResult result;
        public final Nation nation;
        FoundNationOutcome(FoundNationResult result, Nation nation) { this.result = result; this.nation = nation; }
    }

    /** What can go wrong recruiting an army - see recruitArmy(). */
    public enum RecruitArmyResult { SUCCESS, NO_NATION, INVALID_TROOP_COUNT, PROVINCE_NOT_FOUND, PROVINCE_NOT_OWNED, INSUFFICIENT_TREASURY }

    /** result plus the created Army (null unless result == SUCCESS) - see recruitArmy(). */
    public static class RecruitArmyOutcome
    {
        public final RecruitArmyResult result;
        public final Army army;
        RecruitArmyOutcome(RecruitArmyResult result, Army army) { this.result = result; this.army = army; }
    }

    /** What can go wrong queuing a march order - see queueMarchForAccount(). Adjacency/war-state legality is deliberately NOT checked here - see queueMarch()'s own javadoc for why that's DominionTickEngine's job at resolution time, not this method's. */
    public enum QueueMarchResult { SUCCESS, NO_NATION, ARMY_NOT_FOUND, ARMY_NOT_OWNED, TARGET_PROVINCE_NOT_FOUND }

    /** What can go wrong declaring war - see declareWarForAccount(). */
    public enum DeclareWarResult { SUCCESS, NO_NATION, TARGET_NOT_FOUND, CANNOT_DECLARE_ON_SELF }

    /** What can go wrong proposing Alliance/Non-Aggression - see proposeRelation(). */
    public enum ProposeRelationResult { SUCCESS, NO_NATION, TARGET_NOT_FOUND, CANNOT_PROPOSE_TO_SELF, INVALID_TYPE, ALREADY_IN_THAT_RELATION }

    /** result plus the created DiplomaticProposal (null unless result == SUCCESS) - see proposeRelation(). */
    public static class ProposeRelationOutcome
    {
        public final ProposeRelationResult result;
        public final DiplomaticProposal proposal;
        ProposeRelationOutcome(ProposeRelationResult result, DiplomaticProposal proposal) { this.result = result; this.proposal = proposal; }
    }

    /** What can go wrong accepting/rejecting a pending proposal - see respondToProposal(). */
    public enum RespondToProposalResult { SUCCESS, NO_NATION, PROPOSAL_NOT_FOUND, NOT_THE_TARGET }

    /** Placeholder tuning constant, same "deliberately not decided" status as the rest of V1's numbers pending real playtesting - see DOMINION_DESIGN.md. */
    private static final int TREASURY_COST_PER_TROOP = 2;

    private final Map<Integer, Province> provinces = new HashMap<Integer, Province>();
    private final Map<Integer, Nation> nations = new HashMap<Integer, Nation>();
    private final Map<Integer, Army> armies = new HashMap<Integer, Army>();
    private final List<DiplomaticRelation> relations = new ArrayList<DiplomaticRelation>();
    private final Map<Integer, DiplomaticProposal> pendingProposals = new HashMap<Integer, DiplomaticProposal>();
    private int currentTick = 0;
    private int nextNationId = 1;
    private int nextArmyId = 1;
    private int nextProposalId = 1;

    public int getCurrentTick() { return currentTick; }

    /** Package-visible - only DominionTickEngine advances the clock. */
    void setCurrentTick(int currentTick) { this.currentTick = currentTick; }

    public void addProvince(Province province) { provinces.put(province.getId(), province); }
    public Province getProvince(int id) { return provinces.get(id); }
    public Collection<Province> getProvinces() { return provinces.values(); }

    /** Adds a Nation exactly as given (DominionStore's load() is the intended caller for restoring one from disk - founding a brand NEW one is foundNation() below, which allocates the id and validates). Also bumps the next-id counter so a nation founded after a reload never collides with one restored from disk, same "id >= next -> next = id + 1" pattern ServerAccountStore.load() uses for accounts. */
    public void addNation(Nation nation)
    {
        nations.put(nation.getId(), nation);
        if (nation.getId() >= nextNationId)
        {
            nextNationId = nation.getId() + 1;
        }
    }
    public Nation getNation(int id) { return nations.get(id); }
    public Collection<Nation> getNations() { return nations.values(); }

    public Nation getNationForAccount(int accountId)
    {
        for (Nation nation : nations.values())
        {
            if (nation.getAccountId() == accountId)
            {
                return nation;
            }
        }
        return null;
    }

    /**
     * Founds a brand new Nation for accountId, claiming startingProvinceId outright
     * (no combat needed for an unclaimed province - see Province's javadoc). V1: one
     * account can only ever found one Nation (see DOMINION_DESIGN.md) - re-verified
     * here server-side, never trusting that a client only sends this once.
     */
    public FoundNationOutcome foundNation(int accountId, String name, int startingProvinceId)
    {
        if (!Nation.isValidName(name))
        {
            return new FoundNationOutcome(FoundNationResult.INVALID_NAME, null);
        }
        if (getNationForAccount(accountId) != null)
        {
            return new FoundNationOutcome(FoundNationResult.ACCOUNT_ALREADY_HAS_NATION, null);
        }
        Province province = provinces.get(startingProvinceId);
        if (province == null)
        {
            return new FoundNationOutcome(FoundNationResult.PROVINCE_NOT_FOUND, null);
        }
        if (province.getOwningNationId() != null)
        {
            return new FoundNationOutcome(FoundNationResult.PROVINCE_ALREADY_CLAIMED, null);
        }

        Nation nation = new Nation(nextNationId++, accountId, name);
        nations.put(nation.getId(), nation);
        province.setOwningNationId(nation.getId());
        return new FoundNationOutcome(FoundNationResult.SUCCESS, nation);
    }

    /** A client-facing snapshot of the whole world for accountId - see DominionSnapshot's javadoc for why province/nation/army/relation data includes everything rather than just accountId's own nation (no fog of war in V1), but pending proposals are scoped to accountId's own Nation only (a proposal IS private between two nations, even in V1). */
    public DominionSnapshot toSnapshot(int accountId)
    {
        Nation myNation = getNationForAccount(accountId);
        List<DiplomaticProposal> myProposals = myNation == null
            ? new ArrayList<DiplomaticProposal>() : getProposalsForNation(myNation.getId());
        return new DominionSnapshot(currentTick, new ArrayList<Province>(provinces.values()),
            new ArrayList<Nation>(nations.values()), new ArrayList<Army>(armies.values()),
            new ArrayList<DiplomaticRelation>(relations), myProposals);
    }

    /** Adds an Army exactly as given (DominionStore's load() is the intended caller for restoring one from disk - recruiting a brand NEW one is recruitArmy() below). Also bumps the next-id counter, same pattern as addNation(). */
    public void addArmy(Army army)
    {
        armies.put(army.getId(), army);
        if (army.getId() >= nextArmyId)
        {
            nextArmyId = army.getId() + 1;
        }
    }
    public void removeArmy(int id) { armies.remove(id); }
    public Collection<Army> getArmies() { return armies.values(); }

    public List<Army> getArmiesAt(int provinceId)
    {
        List<Army> result = new ArrayList<Army>();
        for (Army army : armies.values())
        {
            if (army.getLocationProvinceId() == provinceId)
            {
                result.add(army);
            }
        }
        return result;
    }

    /**
     * Recruits a new Army for accountId's Nation at provinceId, spending
     * troopCount * TREASURY_COST_PER_TROOP from the Nation's treasury. provinceId
     * must be owned by the requesting Nation - re-verified here server-side, never
     * trusting a client-supplied province.
     */
    public RecruitArmyOutcome recruitArmy(int accountId, int provinceId, int troopCount)
    {
        Nation nation = getNationForAccount(accountId);
        if (nation == null)
        {
            return new RecruitArmyOutcome(RecruitArmyResult.NO_NATION, null);
        }
        if (troopCount <= 0)
        {
            return new RecruitArmyOutcome(RecruitArmyResult.INVALID_TROOP_COUNT, null);
        }
        Province province = provinces.get(provinceId);
        if (province == null)
        {
            return new RecruitArmyOutcome(RecruitArmyResult.PROVINCE_NOT_FOUND, null);
        }
        if (province.getOwningNationId() == null || province.getOwningNationId().intValue() != nation.getId())
        {
            return new RecruitArmyOutcome(RecruitArmyResult.PROVINCE_NOT_OWNED, null);
        }
        int cost = troopCount * TREASURY_COST_PER_TROOP;
        if (nation.getTreasury() < cost)
        {
            return new RecruitArmyOutcome(RecruitArmyResult.INSUFFICIENT_TREASURY, null);
        }

        nation.setTreasury(nation.getTreasury() - cost);
        Army army = new Army(nextArmyId++, nation.getId(), troopCount, provinceId);
        armies.put(army.getId(), army);
        return new RecruitArmyOutcome(RecruitArmyResult.SUCCESS, army);
    }

    /** Queues a march order for the next tick - the target province doesn't need to be a legal destination yet (adjacency/ownership/war-state are all re-checked by DominionTickEngine at resolution time, same "never trust it, re-verify server-side" rule as every other game's move validation). Package-visible internal setter - queueMarchForAccount() below is the account-validated entry point everything else should call. */
    void queueMarch(int armyId, int targetProvinceId)
    {
        Army army = armies.get(armyId);
        if (army != null)
        {
            army.setMarchOrderTargetProvinceId(targetProvinceId);
        }
    }

    /** Validates armyId actually belongs to accountId's Nation before queuing the march - never trusts a client-supplied army id. */
    public QueueMarchResult queueMarchForAccount(int accountId, int armyId, int targetProvinceId)
    {
        Nation nation = getNationForAccount(accountId);
        if (nation == null)
        {
            return QueueMarchResult.NO_NATION;
        }
        Army army = armies.get(armyId);
        if (army == null)
        {
            return QueueMarchResult.ARMY_NOT_FOUND;
        }
        if (army.getNationId() != nation.getId())
        {
            return QueueMarchResult.ARMY_NOT_OWNED;
        }
        if (provinces.get(targetProvinceId) == null)
        {
            return QueueMarchResult.TARGET_PROVINCE_NOT_FOUND;
        }
        queueMarch(armyId, targetProvinceId);
        return QueueMarchResult.SUCCESS;
    }

    /** Declares war from attacker onto defender - replaces whatever relation (if any) already existed between them. Effective starting next tick, never immediately (see DiplomaticRelation's javadoc). Package-visible internal mutator - declareWarForAccount() below is the account-validated entry point. */
    void declareWar(int attackerNationId, int defenderNationId)
    {
        removeRelationBetween(attackerNationId, defenderNationId);
        relations.add(new DiplomaticRelation(attackerNationId, defenderNationId, RelationType.WAR, currentTick + 1));
    }

    /** Validates accountId has a Nation and targetNationId is a real, different Nation before declaring war. */
    public DeclareWarResult declareWarForAccount(int accountId, int targetNationId)
    {
        Nation nation = getNationForAccount(accountId);
        if (nation == null)
        {
            return DeclareWarResult.NO_NATION;
        }
        if (nation.getId() == targetNationId)
        {
            return DeclareWarResult.CANNOT_DECLARE_ON_SELF;
        }
        if (nations.get(targetNationId) == null)
        {
            return DeclareWarResult.TARGET_NOT_FOUND;
        }
        declareWar(nation.getId(), targetNationId);
        return DeclareWarResult.SUCCESS;
    }

    /** Alliance and non-aggression are both effective immediately (no next-tick delay - that delay is specific to declaring war, a deliberate asymmetry: peace shouldn't have to wait a day to take effect, only aggression does). */
    public void setPeacefulRelation(int nationAId, int nationBId, RelationType type)
    {
        if (type == RelationType.WAR)
        {
            throw new IllegalArgumentException("Use declareWar() for a WAR relation - it needs the next-tick delay.");
        }
        removeRelationBetween(nationAId, nationBId);
        relations.add(new DiplomaticRelation(nationAId, nationBId, type, null));
    }

    /**
     * Proposes an Alliance or Non-Aggression pact from accountId's Nation to
     * targetNationId - awaits the target's respondToProposal() call. Never
     * immediately effective, unlike declareWar()'s next-tick delay - a proposal
     * isn't a relation at all until accepted.
     */
    public ProposeRelationOutcome proposeRelation(int accountId, int targetNationId, RelationType type)
    {
        if (type != RelationType.ALLIANCE && type != RelationType.NON_AGGRESSION)
        {
            return new ProposeRelationOutcome(ProposeRelationResult.INVALID_TYPE, null);
        }
        Nation nation = getNationForAccount(accountId);
        if (nation == null)
        {
            return new ProposeRelationOutcome(ProposeRelationResult.NO_NATION, null);
        }
        if (nation.getId() == targetNationId)
        {
            return new ProposeRelationOutcome(ProposeRelationResult.CANNOT_PROPOSE_TO_SELF, null);
        }
        if (nations.get(targetNationId) == null)
        {
            return new ProposeRelationOutcome(ProposeRelationResult.TARGET_NOT_FOUND, null);
        }
        for (DiplomaticRelation relation : relations)
        {
            if (relation.involves(nation.getId(), targetNationId) && relation.getType() == type)
            {
                return new ProposeRelationOutcome(ProposeRelationResult.ALREADY_IN_THAT_RELATION, null);
            }
        }

        DiplomaticProposal proposal = new DiplomaticProposal(nextProposalId++, nation.getId(), targetNationId, type);
        pendingProposals.put(proposal.getId(), proposal);
        return new ProposeRelationOutcome(ProposeRelationResult.SUCCESS, proposal);
    }

    /** Accepts or rejects a pending proposal - only the proposal's target Nation may respond, re-verified here server-side. Accepting establishes the relation immediately via setPeacefulRelation(); rejecting just discards the proposal. Either way the proposal is consumed - it can't be responded to twice. */
    public RespondToProposalResult respondToProposal(int accountId, int proposalId, boolean accept)
    {
        Nation nation = getNationForAccount(accountId);
        if (nation == null)
        {
            return RespondToProposalResult.NO_NATION;
        }
        DiplomaticProposal proposal = pendingProposals.get(proposalId);
        if (proposal == null)
        {
            return RespondToProposalResult.PROPOSAL_NOT_FOUND;
        }
        if (proposal.getToNationId() != nation.getId())
        {
            return RespondToProposalResult.NOT_THE_TARGET;
        }

        pendingProposals.remove(proposalId);
        if (accept)
        {
            setPeacefulRelation(proposal.getFromNationId(), proposal.getToNationId(), proposal.getProposedType());
        }
        return RespondToProposalResult.SUCCESS;
    }

    /** Every pending proposal, regardless of which nations are involved - DominionStore's save() is the intended caller (unlike getProposalsForNation(), this one is NOT account-scoped, so nothing else should use it to answer a network request). */
    public Collection<DiplomaticProposal> getAllProposals() { return pendingProposals.values(); }

    public List<DiplomaticProposal> getProposalsForNation(int nationId)
    {
        List<DiplomaticProposal> result = new ArrayList<DiplomaticProposal>();
        for (DiplomaticProposal proposal : pendingProposals.values())
        {
            if (proposal.getFromNationId() == nationId || proposal.getToNationId() == nationId)
            {
                result.add(proposal);
            }
        }
        return result;
    }

    /** Adds a proposal exactly as given, restoring one from disk (DominionStore's load() is the intended caller) - also bumps the next-id counter, same pattern as addNation()/addArmy(). */
    void addProposal(DiplomaticProposal proposal)
    {
        pendingProposals.put(proposal.getId(), proposal);
        if (proposal.getId() >= nextProposalId)
        {
            nextProposalId = proposal.getId() + 1;
        }
    }

    private void removeRelationBetween(int nationAId, int nationBId)
    {
        relations.removeIf(new java.util.function.Predicate<DiplomaticRelation>()
        {
            public boolean test(DiplomaticRelation r) { return r.involves(nationAId, nationBId); }
        });
    }

    public boolean isAtWar(int nationAId, int nationBId, int atTick)
    {
        for (DiplomaticRelation relation : relations)
        {
            if (relation.involves(nationAId, nationBId) && relation.isWarActiveAt(atTick))
            {
                return true;
            }
        }
        return false;
    }

    public List<DiplomaticRelation> getRelations() { return relations; }

    /** Adds a relation exactly as given, with no business-rule recomputation (declareWar/setPeacefulRelation both derive effectiveFromTick from the CURRENT tick, which is wrong when restoring an already-fixed value from disk) - DominionStore's load() is the only intended caller. */
    void addRelation(DiplomaticRelation relation) { relations.add(relation); }
}
