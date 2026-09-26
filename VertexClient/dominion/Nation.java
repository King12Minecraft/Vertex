package dominion;

import java.io.Serializable;

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
 * Serializable for the same reason as Province - see its javadoc.
 */
public class Nation implements Serializable
{
    private static final int STARTING_HONOR = 100;
    private static final int MIN_NAME_LENGTH = 2;
    private static final int MAX_NAME_LENGTH = 30;

    /**
     * Letters, digits, spaces, apostrophes, and hyphens only, 2-30 characters - lets
     * through ordinary multi-word names ("United Provinces", "Cote d'Or") while
     * rejecting "|" (this project's recurring pipe-delimited-save-format corruption
     * bug - see ServerAccountStore.isValidUsernameFormat's javadoc for the exact same
     * bug class with usernames) and anything else DominionStore's flat-file format
     * doesn't expect on one line.
     */
    private static final java.util.regex.Pattern VALID_NAME =
        java.util.regex.Pattern.compile("^[A-Za-z0-9 '-]{" + MIN_NAME_LENGTH + "," + MAX_NAME_LENGTH + "}$");

    public static boolean isValidName(String name)
    {
        return name != null && VALID_NAME.matcher(name).matches();
    }

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
