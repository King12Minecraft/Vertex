/**
 * PermissionManager
 * ------------------
 * Centralizes role checks so they're not scattered as raw
 * `role == Role.X` comparisons across the UI.
 *
 * IMPORTANT: this is a CLIENT-SIDE convenience only, used to decide
 * what to show/hide in the UI. It is NOT security (Section 13) -
 * hiding a button is not the same as enforcing a permission. Once the
 * real server exists (Phase 5), it must run these same checks itself
 * and reject any request from a client that lies about its role. Never
 * trust this class for anything that actually matters.
 */
public class PermissionManager
{
    private PermissionManager()
    {
        // Static utility class - never instantiated.
    }

    public static boolean isAtLeastModerator(Account account)
    {
        if (account == null)
        {
            return false;
        }
        return account.getRole() == Role.MODERATOR || account.getRole() == Role.ADMIN;
    }

    public static boolean isAdmin(Account account)
    {
        return account != null && account.getRole() == Role.ADMIN;
    }
}
