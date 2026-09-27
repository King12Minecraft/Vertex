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

    /** One in-game day per 20 real minutes, per the design brief's tick cadence. */
    private static final long TICK_INTERVAL_MILLIS = 20L * 60L * 1000L;

    private final DominionStore store = new DominionStore();
    private final DominionWorld world;
    private final DominionTickEngine tickEngine = new DominionTickEngine();

    public DominionManager()
    {
        world = store.load();
        if (world.getProvinces().isEmpty())
        {
            seedMap(world);
            store.save(world);
        }
        startTickScheduler();
    }

    /**
     * Runs the daily tick every TICK_INTERVAL_MILLIS, forever, on its own daemon
     * thread - same "infinite loop on a daemon thread, sleep between iterations"
     * shape GameServer's own acceptLoop uses, rather than introducing this
     * codebase's first java.util.Timer/ScheduledExecutorService for one loop.
     * A tick that throws must not silently cancel every future tick (the same
     * "a transient failure can't become a permanent outage" reasoning
     * GameServer.acceptLoop's own comment gives) - caught and logged, not
     * propagated, so the schedule keeps running even if one tick's resolution
     * hits a bug.
     */
    private void startTickScheduler()
    {
        Thread tickThread = new Thread(new Runnable()
        {
            public void run() { tickLoop(); }
        });
        tickThread.setDaemon(true);
        tickThread.setName("dominion-tick");
        tickThread.start();
    }

    private void tickLoop()
    {
        while (true)
        {
            try
            {
                Thread.sleep(TICK_INTERVAL_MILLIS);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                return;
            }
            try
            {
                runTick();
            }
            catch (RuntimeException e)
            {
                System.err.println("Dominion tick failed, will retry next scheduled tick: " + e.getMessage());
            }
        }
    }

    /**
     * Synchronized, like every other method here that touches world - this is the
     * one whole-server DominionWorld, and it's now mutated from two kinds of
     * threads (a ClientHandler thread per connected player, and this tick thread),
     * not just one, so an unsynchronized method here would be a real, not
     * hypothetical, data race the moment a tick lands mid-request.
     */
    private synchronized void runTick()
    {
        tickEngine.resolveTick(world);
        store.save(world);
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

    public synchronized DominionWorld.FoundNationOutcome foundNation(int accountId, String name, int startingProvinceId)
    {
        DominionWorld.FoundNationOutcome outcome = world.foundNation(accountId, name, startingProvinceId);
        if (outcome.result == DominionWorld.FoundNationResult.SUCCESS)
        {
            store.save(world);
        }
        return outcome;
    }

    public synchronized DominionWorld.RecruitArmyOutcome recruitArmy(int accountId, int provinceId, int troopCount)
    {
        DominionWorld.RecruitArmyOutcome outcome = world.recruitArmy(accountId, provinceId, troopCount);
        if (outcome.result == DominionWorld.RecruitArmyResult.SUCCESS)
        {
            store.save(world);
        }
        return outcome;
    }

    public synchronized DominionWorld.QueueMarchResult queueMarch(int accountId, int armyId, int targetProvinceId)
    {
        DominionWorld.QueueMarchResult result = world.queueMarchForAccount(accountId, armyId, targetProvinceId);
        if (result == DominionWorld.QueueMarchResult.SUCCESS)
        {
            store.save(world);
        }
        return result;
    }

    public synchronized DominionWorld.DeclareWarResult declareWar(int accountId, int targetNationId, boolean targetIsOnline)
    {
        DominionWorld.DeclareWarResult result = world.declareWarForAccount(accountId, targetNationId, targetIsOnline);
        if (result == DominionWorld.DeclareWarResult.SUCCESS)
        {
            store.save(world);
        }
        return result;
    }

    /** Looks up a Nation by id, e.g. so ClientHandler can find who owns it before checking whether that player is online for the offline-war-protection rule in declareWar() above. Null if no such Nation exists. */
    public synchronized Nation getNation(int id)
    {
        return world.getNation(id);
    }

    public synchronized DominionWorld.ProposeRelationOutcome proposeRelation(int accountId, int targetNationId, RelationType type)
    {
        DominionWorld.ProposeRelationOutcome outcome = world.proposeRelation(accountId, targetNationId, type);
        if (outcome.result == DominionWorld.ProposeRelationResult.SUCCESS)
        {
            store.save(world);
        }
        return outcome;
    }

    public synchronized DominionWorld.RespondToProposalResult respondToProposal(int accountId, int proposalId, boolean accept)
    {
        DominionWorld.RespondToProposalResult result = world.respondToProposal(accountId, proposalId, accept);
        if (result == DominionWorld.RespondToProposalResult.SUCCESS)
        {
            store.save(world);
        }
        return result;
    }

    public synchronized DominionSnapshot getSnapshot(int accountId)
    {
        return world.toSnapshot(accountId);
    }
}
