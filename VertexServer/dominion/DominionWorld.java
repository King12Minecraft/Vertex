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
    private final Map<Integer, Province> provinces = new HashMap<Integer, Province>();
    private final Map<Integer, Nation> nations = new HashMap<Integer, Nation>();
    private final Map<Integer, Army> armies = new HashMap<Integer, Army>();
    private final List<DiplomaticRelation> relations = new ArrayList<DiplomaticRelation>();
    private int currentTick = 0;

    public int getCurrentTick() { return currentTick; }

    /** Package-visible - only DominionTickEngine advances the clock. */
    void setCurrentTick(int currentTick) { this.currentTick = currentTick; }

    public void addProvince(Province province) { provinces.put(province.getId(), province); }
    public Province getProvince(int id) { return provinces.get(id); }
    public Collection<Province> getProvinces() { return provinces.values(); }

    public void addNation(Nation nation) { nations.put(nation.getId(), nation); }
    public Nation getNation(int id) { return nations.get(id); }
    public Collection<Nation> getNations() { return nations.values(); }

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
