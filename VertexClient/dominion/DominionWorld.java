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

    private final Map<Integer, Province> provinces = new HashMap<Integer, Province>();
    private final Map<Integer, Nation> nations = new HashMap<Integer, Nation>();
    private final Map<Integer, Army> armies = new HashMap<Integer, Army>();
    private final List<DiplomaticRelation> relations = new ArrayList<DiplomaticRelation>();
    private int currentTick = 0;
    private int nextNationId = 1;

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

    /** A client-facing snapshot of the whole world - see DominionSnapshot's javadoc for why this includes everything rather than just accountId's own nation (no fog of war in V1). */
    public DominionSnapshot toSnapshot()
    {
        return new DominionSnapshot(currentTick, new ArrayList<Province>(provinces.values()),
            new ArrayList<Nation>(nations.values()), new ArrayList<Army>(armies.values()),
            new ArrayList<DiplomaticRelation>(relations));
    }

    public void addArmy(Army army) { armies.put(army.getId(), army); }
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

    /** Queues a march order for the next tick - the target province doesn't need to be a legal destination yet (adjacency/ownership/war-state are all re-checked by DominionTickEngine at resolution time, same "never trust it, re-verify server-side" rule as every other game's move validation). */
    public void queueMarch(int armyId, int targetProvinceId)
    {
        Army army = armies.get(armyId);
        if (army != null)
        {
            army.setMarchOrderTargetProvinceId(targetProvinceId);
        }
    }

    /** Declares war from attacker onto defender - replaces whatever relation (if any) already existed between them. Effective starting next tick, never immediately (see DiplomaticRelation's javadoc). */
    public void declareWar(int attackerNationId, int defenderNationId)
    {
        removeRelationBetween(attackerNationId, defenderNationId);
        relations.add(new DiplomaticRelation(attackerNationId, defenderNationId, RelationType.WAR, currentTick + 1));
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
