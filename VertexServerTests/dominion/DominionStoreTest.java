package dominion;

import support.Check;

import java.util.Collection;

/**
 * DominionStoreTest
 * ------------------
 * Graduated from a scratch test - a full save-then-reload round trip for
 * every entity type DominionStore persists (Province, Nation, Army,
 * DiplomaticRelation, DiplomaticProposal, and the tick counter itself),
 * including a declared-but-not-yet-active war (RelationType.WAR with a
 * future effectiveFromTick) which must survive exactly as declared rather
 * than getting silently recomputed relative to whatever tick the world
 * happens to reload at. Runs in its own fresh temp working directory via
 * test.sh, since DominionStore hardcodes its relative file name.
 */
public class DominionStoreTest
{
    public static void main(String[] args)
    {
        Check check = new Check();

        testFullRoundTrip(check);
        testMissingFileYieldsFreshEmptyWorld(check);

        check.finish();
    }

    private static void testFullRoundTrip(Check check)
    {
        DominionWorld world = new DominionWorld();
        world.setCurrentTick(12);

        Province provinceA = new Province(1, 0, 0, Terrain.PLAINS);
        provinceA.setOwningNationId(100);
        Province provinceB = new Province(2, 0, 1, Terrain.HILLS);
        world.addProvince(provinceA);
        world.addProvince(provinceB);

        Nation nation = new Nation(100, 7, "Testland");
        nation.setTreasury(842);
        nation.setHonor(55);
        world.addNation(nation);

        Army army = new Army(1, 100, 30, 1);
        army.setMarchOrderTargetProvinceId(2);
        world.addArmy(army);

        // A declared-but-not-yet-active war - effectiveFromTick is in the future
        // relative to currentTick, and must stay that way after a reload rather
        // than being reinterpreted against whatever tick the reloaded world starts
        // at (DOMINION_DESIGN.md's deliberate "declared war isn't immediately
        // active" pacing choice).
        DiplomaticRelation war = new DiplomaticRelation(100, 200, RelationType.WAR, 13);
        world.addRelation(war);

        DiplomaticProposal proposal = new DiplomaticProposal(1, 100, 200, RelationType.ALLIANCE);
        world.addProposal(proposal);

        DominionStore store = new DominionStore();
        store.save(world);

        DominionWorld reloaded = store.load();

        check.check("Tick survives the round trip", reloaded.getCurrentTick() == 12);

        Province reloadedA = reloaded.getProvince(1);
        check.check("Province A survives with its terrain and owner", reloadedA != null
            && reloadedA.getTerrain() == Terrain.PLAINS && reloadedA.getOwningNationId().equals(100));
        Province reloadedB = reloaded.getProvince(2);
        check.check("Province B survives as unowned", reloadedB != null
            && reloadedB.getTerrain() == Terrain.HILLS && reloadedB.getOwningNationId() == null);

        Nation reloadedNation = reloaded.getNation(100);
        check.check("Nation survives with its treasury and honor", reloadedNation != null
            && reloadedNation.getTreasury() == 842 && reloadedNation.getHonor() == 55
            && "Testland".equals(reloadedNation.getName()));

        Collection<Army> reloadedArmies = reloaded.getArmiesAt(1);
        check.check("Army survives at its location with its march order", !reloadedArmies.isEmpty()
            && reloadedArmies.iterator().next().getTroopCount() == 30);

        boolean warSurvivedExactly = false;
        for (DiplomaticRelation relation : reloaded.getRelations())
        {
            if (relation.involves(100, 200) && relation.getType() == RelationType.WAR
                && Integer.valueOf(13).equals(relation.getEffectiveFromTick()))
            {
                warSurvivedExactly = true;
            }
        }
        check.check("A declared-but-not-yet-active war survives with its exact effectiveFromTick, not recomputed",
            warSurvivedExactly);
        check.check("The not-yet-active war correctly is NOT at war yet at tick 12",
            !reloaded.isAtWar(100, 200, 12));
        check.check("The war correctly becomes active from tick 13 onward",
            reloaded.isAtWar(100, 200, 13));

        boolean proposalSurvived = false;
        for (DiplomaticProposal p : reloaded.getAllProposals())
        {
            if (p.getFromNationId() == 100 && p.getToNationId() == 200 && p.getProposedType() == RelationType.ALLIANCE)
            {
                proposalSurvived = true;
            }
        }
        check.check("A pending proposal survives the round trip", proposalSurvived);
    }

    private static void testMissingFileYieldsFreshEmptyWorld(Check check)
    {
        java.io.File file = new java.io.File("gamehub_dominion.dat");
        if (file.exists())
        {
            file.delete();
        }

        DominionStore store = new DominionStore();
        DominionWorld world = store.load();
        check.check("Loading with no file present yields a fresh world, not an error",
            world != null && world.getCurrentTick() == 0 && world.getProvinces().isEmpty());
    }
}
