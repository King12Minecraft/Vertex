import java.util.ArrayList;
import java.util.List;

/**
 * Account
 * -------
 * An account. The accountId is PERMANENT (Section 11) - it never
 * changes, even if the username does. Everything that matters (role,
 * friends, messages, purchases, later on) should key off accountId, not
 * username.
 *
 * passwordHash/passwordSalt are never plain text (Section 33). This is
 * a SHARED (Common) class - identical copies live in both VertexClient
 * and VertexServer. It must implement Serializable so the server can
 * send Account objects to the client over the socket (Phase 5).
 */
public class Account implements java.io.Serializable
{
    private static final long serialVersionUID = 1L;

    private final int accountId;
    private String username;
    private String passwordHash;
    private String passwordSalt;
    private Role role;
    private String playerColorName;
    private String equippedBadgeId;
    private int coins;
    private final List<String> ownedItemIds = new ArrayList<String>();
    private String lastLoginDate;
    private int loginStreak;

    public Account(int accountId, String username, String passwordHash, String passwordSalt, Role role)
    {
        this.accountId = accountId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.passwordSalt = passwordSalt;
        this.role = role;
        this.playerColorName = "Default";
        this.equippedBadgeId = "";
        this.lastLoginDate = "";
        this.loginStreak = 0;
    }

    public int getAccountId() { return accountId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public String getPasswordSalt() { return passwordSalt; }
    public void setPassword(String hash, String salt)
    {
        this.passwordHash = hash;
        this.passwordSalt = salt;
    }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public String getPlayerColorName() { return playerColorName; }
    public void setPlayerColorName(String playerColorName) { this.playerColorName = playerColorName; }

    public String getEquippedBadgeId() { return equippedBadgeId; }
    public void setEquippedBadgeId(String equippedBadgeId) { this.equippedBadgeId = equippedBadgeId; }

    public int getCoins() { return coins; }
    public void setCoins(int coins) { this.coins = coins; }

    public List<String> getOwnedItemIds() { return ownedItemIds; }

    /** "YYYY-MM-DD" of the last calendar day a daily login reward was claimed - Phase 11. */
    public String getLastLoginDate() { return lastLoginDate; }
    public void setLastLoginDate(String lastLoginDate) { this.lastLoginDate = lastLoginDate; }

    /** Consecutive daily-login streak, reset if a day is missed. */
    public int getLoginStreak() { return loginStreak; }
    public void setLoginStreak(int loginStreak) { this.loginStreak = loginStreak; }
}
