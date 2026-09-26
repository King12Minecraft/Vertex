package dominion;

/**
 * DominionManager
 * ---------------
 * The single, whole-server DominionWorld's front door for ClientHandler -
 * loads the world once at server startup (via DominionStore), seeds a
 * starting map on first launch (an empty world has nothing to found a
 * Nation on), exposes the request-handling operations V1's networking
 * needs, and saves after anything that actually changes state. Unlike
 * every other game's per-match manager, there is exactly one
 * DominionWorld for the whole server - matching Dominion's own
 * "persistent, whole-server" identity (see DOMINION_DESIGN.md), not one
 * instance per match.
 */
public class DominionManager
{
    /**
     * Placeholder defaults - DOMINION_DESIGN.md explicitly flags the real map size
     * and terrain distribution as "deliberately not decided here," pending real
     * playtesting once the loop is actually playable. This just needs to be
     * non-empty so founding a nation is possible at all; a deterministic (not
     * random) layout so the seeded map is reproducible and testable.
     */
    private static final int MAP_SIZE = 10;

    private final DominionStore store = new DominionStore();
    private final DominionWorld world;

    public DominionManager()
    {
        world = store.load();
        if (world.getProvinces().isEmpty())
        {
            seedMap(world);
            store.save(world);
        }
    }

    private void seedMap(DominionWorld world)
    {
        Terrain[] terrainCycle = Terrain.values();
        int id = 0;
        for (int row = 0; row < MAP_SIZE; row++)
        {
            for (int col = 0; col < MAP_SIZE; col++)
            {
                Terrain terrain = terrainCycle[(row * MAP_SIZE + col) % terrainCycle.length];
                world.addProvince(new Province(id, row, col, terrain));
                id++;
            }
        }
    }

    public DominionWorld.FoundNationOutcome foundNation(int accountId, String name, int startingProvinceId)
    {
        DominionWorld.FoundNationOutcome outcome = world.foundNation(accountId, name, startingProvinceId);
        if (outcome.result == DominionWorld.FoundNationResult.SUCCESS)
        {
            store.save(world);
        }
        return outcome;
    }

    public DominionSnapshot getSnapshot()
    {
        return world.toSnapshot();
    }
}
