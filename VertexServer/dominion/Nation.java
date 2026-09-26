package dominion;

/**
 * Nation
 * ------
 * V1: one account founds and solely controls one Nation - multi-member
 * nations (councils, invites, ranks) are a real feature but a social one
 * layered on top of a working single-owner game, deferred per
 * DOMINION_DESIGN.md rather than built as a prerequisite. treasury is
 * Dominion's own currency, never the platform's match-reward coins (see
 * ROADMAP.md's Architecture Decisions - Dominion keeps an isolated
 * currency deliberately). honor starts at the max and is a soft
 * reputation stat - nothing in V1 hard-blocks on a low value yet, it's
 * tracked so it has real meaning to gate later once there's a concrete
 * reason to (see DOMINION_DESIGN.md's diplomacy section).
 *
 * name validation is NOT done here or anywhere yet - there's no caller that accepts a
 * player-chosen name yet (that arrives with build order step 3's networking, "found a
 * nation" message handling). Flagging now rather than after the fact: DominionStore
 * saves this in a pipe-delimited line, so whatever validates a nation name at that
 * future call site MUST reject "|" (same exact bug class ServerAccountStore.
 * isValidUsernameFormat's javadoc already documents for usernames - corrupts the saved
 * line, shifting every field after it on the next load).
 */
public class Nation
{
    private static final int STARTING_HONOR = 100;

    private final int id;
    private final int accountId;
    private String name;
    private int treasury;
    private int honor = STARTING_HONOR;

    public Nation(int id, int accountId, String name)
    {
        this.id = id;
        this.accountId = accountId;
        this.name = name;
    }

    public int getId() { return id; }
    public int getAccountId() { return accountId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getTreasury() { return treasury; }
    public void setTreasury(int treasury) { this.treasury = treasury; }
    public int getHonor() { return honor; }
    public void setHonor(int honor) { this.honor = honor; }
}
